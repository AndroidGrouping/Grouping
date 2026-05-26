package ntou.project.grouping.pages.schedule

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.model.TravelMode
import kotlinx.coroutines.launch
import ntou.project.grouping.models.Post
import ntou.project.grouping.models.User
import ntou.project.grouping.pages.methods.NavigationMethods

@SuppressLint("MissingPermission")
@Composable
fun SchedulePage(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var myCreatedPosts by remember { mutableStateOf(listOf<Post>()) }
    var myJoinedPosts by remember { mutableStateOf(listOf<Post>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0: 參加中, 1: 我發起的
    
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var expandedPostId by remember { mutableStateOf<String?>(null) }

    // 取得權限並獲取目前位置
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { userLocation = LatLng(it.latitude, it.longitude) }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { userLocation = LatLng(it.latitude, it.longitude) }
            }
        }
    }

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
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; expandedPostId = null }) {
                Text("參加中", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; expandedPostId = null }) {
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
                        ExpandableScheduleCard(
                            post = post,
                            isExpanded = expandedPostId == post.id,
                            userLocation = userLocation,
                            onExpandClick = {
                                expandedPostId = if (expandedPostId == post.id) null else post.id
                            },
                            onQuitClick = {
                                if (currentUser != null) {
                                    db.collection("posts").document(post.id)
                                        .update("participants", FieldValue.arrayRemove(currentUser.uid))
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "已退出活動", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableScheduleCard(
    post: Post,
    isExpanded: Boolean,
    userLocation: LatLng?,
    onExpandClick: () -> Unit,
    onQuitClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var estimatedTime by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf<TravelMode?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onExpandClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = post.authorName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text(text = "${post.participants.size} 人", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = "時間: ${post.eventTime}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(text = "詳細內容", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = post.content, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "地點: ${post.locationName}", fontSize = 13.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(text = "參加人員", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    ParticipantDetailList(participantIds = post.participants)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 交通工具導覽按鈕與退出按鈕
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 汽車
                            IconButton(onClick = {
                                userLocation?.let {
                                    scope.launch {
                                        isCalculating = true
                                        currentMode = TravelMode.DRIVING
                                        estimatedTime = NavigationMethods.getTravelTime(context, it, LatLng(post.latitude, post.longitude), TravelMode.DRIVING)
                                        isCalculating = false
                                    }
                                }
                            }) {
                                Icon(Icons.Default.DirectionsCar, "汽車", tint = if (currentMode == TravelMode.DRIVING) MaterialTheme.colorScheme.primary else Color.Gray)
                            }

                            // 走路
                            IconButton(onClick = {
                                userLocation?.let {
                                    scope.launch {
                                        isCalculating = true
                                        currentMode = TravelMode.WALKING
                                        estimatedTime = NavigationMethods.getTravelTime(context, it, LatLng(post.latitude, post.longitude), TravelMode.WALKING)
                                        isCalculating = false
                                    }
                                }
                            }) {
                                Icon(Icons.Default.DirectionsWalk, "走路", tint = if (currentMode == TravelMode.WALKING) MaterialTheme.colorScheme.primary else Color.Gray)
                            }

                            if (isCalculating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(start = 8.dp), strokeWidth = 2.dp)
                            } else {
                                estimatedTime?.let {
                                    Text(text = it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }

                        IconButton(onClick = onQuitClick) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, "退出", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParticipantDetailList(participantIds: List<String>) {
    val db = FirebaseFirestore.getInstance()
    var participants by remember { mutableStateOf(listOf<User>()) }

    LaunchedEffect(participantIds) {
        if (participantIds.isNotEmpty()) {
            db.collection("users")
                .whereIn("uid", participantIds.take(10))
                .get()
                .addOnSuccessListener { snapshot ->
                    participants = snapshot.toObjects(User::class.java)
                }
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        participants.forEach { user ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    AsyncImage(
                        model = user.photoUrl.ifEmpty { "https://ui-avatars.com/api/?name=${user.displayName}" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = user.displayName, fontSize = 14.sp)
            }
        }
    }
}
