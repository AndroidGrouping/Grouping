package ntou.project.grouping.pages.methods

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.maps.model.LatLng
import com.google.maps.DistanceMatrixApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NavigationMethods {

    /**
     * 使用 Google Maps Services SDK 計算預計到達時間
     * mode: 交通方式，預設為 TravelMode.DRIVING
     */
    suspend fun getTravelTime(
        context: Context,
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode = TravelMode.DRIVING
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getMetadataApiKey(context) ?: return@withContext "API Key 缺失"

        // 初始化 GeoApiContext (這是 SDK 的進入點)
        val geoContext = GeoApiContext.Builder()
            .apiKey(apiKey)
            .build()

        try {
            // 發送 Distance Matrix 請求
            val result = DistanceMatrixApi.newRequest(geoContext)
                .origins("${origin.latitude},${origin.longitude}")
                .destinations("${destination.latitude},${destination.longitude}")
                .mode(mode)
                .language("zh-TW")
                .await() // 這是 SDK 提供的同步調用方法，已被 withContext(Dispatchers.IO) 包裹

            if (result.rows.isNotEmpty()) {
                val element = result.rows[0].elements[0]
                if (element.status.toString() == "OK") {
                    // 回傳易讀的時間字串，例如 "15 分鐘"
                    return@withContext element.duration.humanReadable
                } else {
                    return@withContext "API 狀態錯誤: ${element.status}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "SDK 錯誤: ${e.localizedMessage}"
        } finally {
            // 務必關閉 Context 以釋放執行緒池資源
            geoContext.shutdown()
        }
        null
    }

    /**
     * 從 AndroidManifest 讀取 API Key
     */
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
