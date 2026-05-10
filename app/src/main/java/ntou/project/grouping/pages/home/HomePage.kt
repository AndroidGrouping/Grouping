package ntou.project.grouping.pages.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import ntou.project.grouping.models.Post

@Composable
fun HomePage(paddingValues: PaddingValues) {
    val db = FirebaseFirestore.getInstance()
    var posts by remember { mutableStateOf(listOf<Post>()) }

    // 地圖狀態 (基隆海大)
    val ntou = LatLng(25.1502, 121.7761)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(ntou, 15f)
    }

    // 監聽所有貼文，用來在地圖上顯示圖釘
    LaunchedEffect(Unit) {
        db.collection("posts").addSnapshotListener { snapshot, e ->
            if (snapshot != null) {
                posts = snapshot.toObjects(Post::class.java)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                // 1. 顯示我的固定位置
                Marker(
                    state = MarkerState(position = ntou),
                    title = "你在這裡",
                    snippet = "目前定位點"
                )

                // 2. 顯示所有貼文的活動圖釘
                posts.forEach { post ->
                    if (post.latitude != 0.0 && post.longitude != 0.0) {
                        Marker(
                            state = MarkerState(position = LatLng(post.latitude, post.longitude)),
                            title = post.title,
                            snippet = "時間: ${post.eventTime} | 發起人: ${post.authorName}",
                            onClick = {
                                // 以後可以做點擊圖釘彈出詳情
                                false
                            }
                        )
                    }
                }
            }
        }
    }
}
