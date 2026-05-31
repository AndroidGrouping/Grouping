package ntou.project.grouping

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.rememberCameraPositionState
import ntou.project.grouping.components.BottomNavigationBar
import ntou.project.grouping.components.TopNavigationBar
import ntou.project.grouping.models.Post
import ntou.project.grouping.pages.schedule.SchedulePage
import ntou.project.grouping.pages.home.HomePage
import ntou.project.grouping.pages.NewPostPage
import ntou.project.grouping.pages.auth.LoginPage
import ntou.project.grouping.pages.search.SearchPage
import ntou.project.grouping.pages.user.UserPage
import ntou.project.grouping.pages.user.FriendPage
import ntou.project.grouping.ui.theme.GroupingTheme
import ntou.project.grouping.utils.FcmTokenManager

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 使用者選擇後不需額外處理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 需要動態申請通知權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            GroupingTheme {
                var isLoggedIn by remember {
                    mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
                }

                if (isLoggedIn) {
                    // 登入後同步 FCM Token
                    FcmTokenManager.updateToken()
                    MainScreen(onLogout = { isLoggedIn = false })
                } else {
                    LoginPage(onLoginSuccess = {
                        isLoggedIn = true
                        FcmTokenManager.updateToken()
                    })
                }
            }
        }
    }
}

@Composable
fun MainScreen(onLogout: () -> Unit) {
    var selectedBottomTab by remember { mutableStateOf("Home") }
    // 追蹤從 搜尋 或 行程 頁面點擊跳轉的地點
    var mapTargetPost by remember { mutableStateOf<Post?>(null) }

    // 提升鏡頭位置狀態，用以紀錄 HomePage 最後的位置
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(25.1502, 121.7761), 15f)
    }

    // 提升初次定位旗標：確保切換頁面回來後不會重設鏡頭位置
    var isInitialLocationSet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopNavigationBar()
        },
        bottomBar = {
            BottomNavigationBar(
                selectedItem = if (selectedBottomTab == "Friends") "Profile" else selectedBottomTab,
                onItemSelected = {
                    selectedBottomTab = it
                    if (it != "Home") mapTargetPost = null
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (selectedBottomTab) {
            "Home" -> HomePage(
                paddingValues = innerPadding,
                cameraPositionState = cameraPositionState,
                isInitialLocationSet = isInitialLocationSet,
                onInitialLocationSet = { isInitialLocationSet = true },
                targetPost = mapTargetPost,
                onTargetHandled = { mapTargetPost = null }
            )
            "Search" -> SearchPage(
                paddingValues = innerPadding,
                onNavigateToMap = { post ->
                    // 跳轉前將鏡頭起點固定為離開 Home 時的最後位置，
                    // 讓 HomePage 的動畫從該位置出發飛向目標活動
                    val lastPosition = cameraPositionState.position
                    cameraPositionState.position = lastPosition
                    mapTargetPost = post
                    selectedBottomTab = "Home"
                }
            )
            "Add" -> NewPostPage(
                paddingValues = innerPadding,
                onPostCreated = { post ->
                    mapTargetPost = post
                    selectedBottomTab = "Home"
                }
            )
            "Schedule" -> SchedulePage(
                paddingValues = innerPadding,
                onNavigateToMap = { post ->
                    // 跳轉前將鏡頭起點固定為離開 Home 時的最後位置，
                    // 讓 HomePage 的動畫從該位置出發飛向目標活動
                    val lastPosition = cameraPositionState.position
                    cameraPositionState.position = lastPosition
                    mapTargetPost = post
                    selectedBottomTab = "Home"
                }
            )
            "Profile" -> UserPage(
                paddingValues = innerPadding,
                onNavigateToFriends = { selectedBottomTab = "Friends" },
                onNavigateToSchedule = { selectedBottomTab = "Schedule" },
                onLogout = onLogout
            )
            "Friends" -> FriendPage(innerPadding)
            else -> HomePage(
                paddingValues = innerPadding,
                cameraPositionState = cameraPositionState,
                isInitialLocationSet = isInitialLocationSet,
                onInitialLocationSet = { isInitialLocationSet = true },
                targetPost = mapTargetPost,
                onTargetHandled = { mapTargetPost = null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    GroupingTheme {
        MainScreen(onLogout = {})
    }
}
