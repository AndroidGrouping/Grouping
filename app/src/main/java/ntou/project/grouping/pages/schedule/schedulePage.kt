package ntou.project.grouping.pages.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import ntou.project.grouping.models.Post
import ntou.project.grouping.pages.search.PostCard

@Composable
fun SchedulePage(paddingValues: PaddingValues) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    var myCreatedPosts by remember { mutableStateOf(listOf<Post>()) }
    var myJoinedPosts by remember { mutableStateOf(listOf<Post>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0: 參加中, 1: 我發起的

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            // 監聽我發起的活動
            db.collection("posts")
                .whereEqualTo("authorId", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        myCreatedPosts = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Post::class.java)?.copy(id = doc.id)
                        }
                    }
                    if (selectedTab == 1) isLoading = false
                }

            // 監聽我參加的活動
            db.collection("posts")
                .whereArrayContains("participants", currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        myJoinedPosts = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Post::class.java)?.copy(id = doc.id)
                        }
                    }
                    if (selectedTab == 0) isLoading = false
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("參加中", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("我發起的", modifier = Modifier.padding(16.dp))
            }
        }

        if (isLoading && (myCreatedPosts.isEmpty() && myJoinedPosts.isEmpty())) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val displayList = if (selectedTab == 0) myJoinedPosts else myCreatedPosts
            
            if (displayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "目前沒有行程", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayList) { post ->
                        PostCard(post = post, onClick = { /* 可以實作查看詳情 */ })
                    }
                }
            }
        }
    }
}
