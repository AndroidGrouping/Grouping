package ntou.project.grouping.pages.user

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun UserPage(paddingValues: PaddingValues) {
    // 獲取目前登入的 Firebase 使用者資訊
    val user = FirebaseAuth.getInstance().currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (user != null) {
            // 顯示歡迎文字與 Google 名字
            Text(
                text = "登入成功！",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "你好，${user.displayName ?: "Grouping 會員"}",
                style = MaterialTheme.typography.headlineMedium
            )

            // 顯示登入的 Email
            Text(
                text = user.email ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 顯示 UID (這是 Firebase 給你的唯一識別碼，證明真的有連上後台)
            Text(
                text = "用戶 ID: ${user.uid.take(8)}...",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(text = "目前處於訪客模式，請先登入")
        }
    }
}
