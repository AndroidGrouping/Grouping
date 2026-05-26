package ntou.project.grouping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.auth.FirebaseAuth
import ntou.project.grouping.components.BottomNavigationBar
import ntou.project.grouping.components.TopNavigationBar
import ntou.project.grouping.pages.chat.ChatPage
import ntou.project.grouping.pages.home.HomePage
import ntou.project.grouping.pages.NewPostPage
import ntou.project.grouping.pages.auth.LoginPage
import ntou.project.grouping.pages.search.SearchPage
import ntou.project.grouping.pages.user.UserPage
import ntou.project.grouping.ui.theme.GroupingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GroupingTheme {
                var isLoggedIn by remember { 
                    mutableStateOf(FirebaseAuth.getInstance().currentUser != null) 
                }

                if (isLoggedIn) {
                    MainScreen()
                } else {
                    LoginPage(onLoginSuccess = { isLoggedIn = true })
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedBottomTab by remember { mutableStateOf("Home") }

    Scaffold(
        topBar = {
            TopNavigationBar()
        },
        bottomBar = {
            BottomNavigationBar(
                selectedItem = selectedBottomTab,
                onItemSelected = { selectedBottomTab = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (selectedBottomTab) {
            "Home" -> HomePage(innerPadding)
            "Search" -> SearchPage(innerPadding)
            "Add" -> NewPostPage(innerPadding)
            "Messages" -> ChatPage(innerPadding)
            "Profile" -> UserPage(
                paddingValues = innerPadding,
                onNavigateToFriends = { selectedBottomTab = "Messages" }
            )
            else -> HomePage(innerPadding)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    GroupingTheme {
        MainScreen()
    }
}
