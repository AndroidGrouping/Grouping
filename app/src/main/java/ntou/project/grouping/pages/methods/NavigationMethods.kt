package ntou.project.grouping.pages.methods

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

object NavigationMethods {

    /**
     * 給予起點與終點，回傳預計到達時間字串 (例如: "15 分鐘")
     * mode: 交通方式，可選 "driving", "walking", "bicycling", "transit"
     */
    suspend fun getTravelTime(
        context: Context,
        origin: LatLng,
        destination: LatLng,
        mode: String = "driving"
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getMetadataApiKey(context) ?: return@withContext "API Key 缺失"
        
        // 構建 Google Distance Matrix API 請求 URL
        val urlString = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=${origin.latitude},${origin.longitude}" +
                "&destinations=${destination.latitude},${destination.longitude}" +
                "&mode=$mode" +
                "&language=zh-TW" +
                "&key=$apiKey"

        try {
            // 使用 URI(urlString).toURL() 避免 URL(String) 棄用警告
            val url = URI(urlString).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                
                if (json.getString("status") == "OK") {
                    val rows = json.getJSONArray("rows")
                    val elements = rows.getJSONObject(0).getJSONArray("elements")
                    val element = elements.getJSONObject(0)
                    
                    if (element.getString("status") == "OK") {
                        // 取得時間文字描述
                        return@withContext element.getJSONObject("duration").getString("text")
                    } else {
                        return@withContext "API 錯誤: ${element.getString("status")}"
                    }
                } else {
                    return@withContext "請求失敗: ${json.getString("status")}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "網路或解析錯誤"
        }
        null
    }

    private fun getMetadataApiKey(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            null
        }
    }
}
