package ntou.project.grouping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ntou.project.grouping.ui.theme.GroupingTheme
import ntou.project.grouping.ui.theme.MainBlue
import ntou.project.grouping.ui.theme.MainYellow
import ntou.project.grouping.ui.theme.TabUnselected

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GroupingTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    // 上方 Tab 的狀態
    var selectedTopTab by remember { mutableStateOf("新到者") }
    // 下方 Tab 的狀態 (新增)
    var selectedBottomTab by remember { mutableStateOf("Home") }

    Scaffold(
        topBar = {
            TopNavigationBar(
                selectedTab = selectedTopTab,
                onTabSelected = { selectedTopTab = it }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedItem = selectedBottomTab,
                onItemSelected = { selectedBottomTab = it }
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF5F5F5))
        ) {
            // 中間顯示兩者的狀態資訊
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("底部選單：$selectedBottomTab", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("上方分頁：$selectedTopTab", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun TopNavigationBar(
    selectedTab: String,// 新增這行：接收目前選中的標籤
    onTabSelected: (String) -> Unit
) {
    val tabs = listOf("即將開始", "新到者", "在我附近", "追蹤動態")
    Surface(
        color = MainBlue,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(top = 48.dp, bottom = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onTabSelected(tab) }
                    ) {
                        Text(
                            text = tab,
                            color = if (selectedTab == tab) MainYellow else TabUnselected,
                            fontSize = 16.sp,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                        if (selectedTab == tab) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(MainYellow)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = MainBlue,
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 這裡將 label 傳入，並判斷是否被選中
                BottomNavItem(Icons.Default.Home, "Home", selectedItem == "Home") {
                    onItemSelected("Home")
                }
                BottomNavItem(Icons.Default.Notifications, "Notifications", selectedItem == "Notifications") {
                    onItemSelected("Notifications")
                }

                Spacer(modifier = Modifier.size(56.dp))

                BottomNavItem(Icons.Default.Email, "Messages", selectedItem == "Messages") {
                    onItemSelected("Messages")
                }
                BottomNavItem(Icons.Default.Person, "Profile", selectedItem == "Profile") {
                    onItemSelected("Profile")
                }
            }
        }

        // 中間的 FAB (鉛筆按鈕) 也可以加上點擊事件
        Box(
            modifier = Modifier
                .offset(y = (-30).dp)
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(6.dp)
                .clip(CircleShape)
                .background(MainYellow)
                .clickable { onItemSelected("Edit") }, // 假設點擊鉛筆也有動作
            contentAlignment = Alignment.Center
        ) {
            Text("✎", color = Color.White, fontSize = 30.sp)
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit // 新增點擊回調
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        // 選中時變色（例如 MainYellow），未選中時用 TabUnselected
        tint = if (isSelected) MainYellow else TabUnselected,
        modifier = Modifier
            .size(28.dp)
            .clickable { onClick() } // 使圖示可點擊
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    GroupingTheme {
        MainScreen()
    }
}
