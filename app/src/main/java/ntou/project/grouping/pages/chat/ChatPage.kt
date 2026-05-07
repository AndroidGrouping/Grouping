package ntou.project.grouping.pages.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import ntou.project.grouping.pages.methods.NavigationMethods

@Composable
fun ChatPage(paddingValues: PaddingValues) {
    val context = LocalContext.current
    var travelTime by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 測試資料：基隆車站 -> 海洋大學
    val origin = LatLng(25.1283, 121.7391)
    val destination = LatLng(25.1505, 121.7770)

    LaunchedEffect(Unit) {
        travelTime = NavigationMethods.getTravelTime(context, origin, destination)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "--- 預計到達時間測試 ---", style = MaterialTheme.typography.titleMedium)
            
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                Text(
                    text = travelTime ?: "無法取得資料，請確認 API Key 是否正確且已啟動 Distance Matrix API",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Text(text = "測試結束後可自行恢復原狀", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
