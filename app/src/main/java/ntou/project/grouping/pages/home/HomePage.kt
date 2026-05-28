package ntou.project.grouping.pages.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import ntou.project.grouping.models.Post

@SuppressLint("MissingPermission")
@Composable
fun HomePage(
    paddingValues: PaddingValues,
    targetPost: Post? = null,
    onTargetHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    
    var allPosts by remember { mutableStateOf(listOf<Post>()) }
    
    // --- 篩選相關狀態 ---
    val categories = listOf("羽球", "唱歌", "運動", "美食", "桌遊", "旅遊", "學習")
    var selectedCategories by remember { mutableStateOf(categories.toSet()) }
    var isFilterVisible by remember { mutableStateOf(false) } 

    // 控制詳情視窗的狀態
    var selectedPostForDetail by remember { mutableStateOf<Post?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(25.1502, 121.7761), 15f)
    }

    var locationPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    // 處理跳轉
    LaunchedEffect(targetPost) {
        if (targetPost != null && targetPost.latitude != 0.0) {
            selectedPostForDetail = targetPost
            showDetailDialog = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                scope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(targetPost.latitude, targetPost.longitude), 17f))
                    onTargetHandled()
                }
            }
        }
    }

    // Firestore 監聽
    DisposableEffect(Unit) {
        val firestoreListener = db.collection("posts").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                allPosts = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java)?.copy(id = doc.id)
                }
            }
        }
        onDispose { firestoreListener?.remove() }
    }

    // 篩選過濾
    val filteredPosts = remember(allPosts, selectedCategories) {
        allPosts.filter { post ->
            post.tags.any { tag -> selectedCategories.contains(tag) } || post.tags.isEmpty()
        }
    }

    // 活動詳情視窗
    if (showDetailDialog && selectedPostForDetail != null) {
        val post = selectedPostForDetail!!
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text(post.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("發起人: ${post.authorName}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    Text("時間: ${post.eventTime}", fontSize = 14.sp)
                    if (post.locationName.isNotBlank()) Text("地點: ${post.locationName}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(post.content, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("參加人數: ${post.participants.size} / ${if(post.maxParticipants > 0) post.maxParticipants else "不限"}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (currentUser == null) return@Button
                    val postRef = db.collection("posts").document(post.id)
                    val isJoined = post.participants.contains(currentUser.uid)
                    if (isJoined) postRef.update("participants", FieldValue.arrayRemove(currentUser.uid))
                    else postRef.update("participants", FieldValue.arrayUnion(currentUser.uid))
                    showDetailDialog = false
                }) {
                    Text(if (post.participants.contains(currentUser?.uid)) "取消參加" else "我要參加")
                }
            },
            dismissButton = { TextButton(onClick = { showDetailDialog = false }) { Text("關閉") } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // --- 1. 地圖主體 ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            filteredPosts.forEach { post ->
                key(post.id) {
                    MarkerComposable(
                        state = MarkerState(position = LatLng(post.latitude, post.longitude)),
                        title = post.title,
                        anchor = Offset(0.5f, 1f),
                        onClick = { selectedPostForDetail = post; showDetailDialog = true; true }
                    ) {
                        CategoryMarker(post)
                    }
                }
            }
        }

        // --- 2. 懸浮橫向勾選列 ---
        Row(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 切換按鈕
            FloatingActionButton(
                onClick = { isFilterVisible = !isFilterVisible },
                modifier = Modifier.size(48.dp),
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    if (isFilterVisible) Icons.Default.Close else Icons.Default.Menu, 
                    contentDescription = "切換篩選"
                )
            }

            // 橫向滑動勾選列表
            AnimatedVisibility(
                visible = isFilterVisible,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .height(48.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 4.dp
                ) {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(categories) { category ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    selectedCategories = if (selectedCategories.contains(category)) {
                                        selectedCategories - category
                                    } else {
                                        selectedCategories + category
                                    }
                                }
                            ) {
                                Checkbox(
                                    checked = selectedCategories.contains(category),
                                    onCheckedChange = null, // 由 Row 點擊處理
                                    modifier = Modifier.scale(0.8f)
                                )
                                Text(
                                    text = category,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedCategories.contains(category)) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedCategories.contains(category)) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryMarker(post: Post) {
    val icon = when {
        post.tags.contains("羽球") -> androidx.compose.material.icons.Icons.Filled.SportsTennis
        post.tags.contains("唱歌") -> androidx.compose.material.icons.Icons.Filled.Mic
        post.tags.contains("運動") -> androidx.compose.material.icons.Icons.Filled.FitnessCenter
        post.tags.contains("美食") -> androidx.compose.material.icons.Icons.Filled.Restaurant
        post.tags.contains("桌遊") -> androidx.compose.material.icons.Icons.Filled.Casino
        post.tags.contains("旅遊") -> androidx.compose.material.icons.Icons.Filled.Explore
        post.tags.contains("學習") -> androidx.compose.material.icons.Icons.Filled.School
        else -> androidx.compose.material.icons.Icons.Filled.Groups
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(45.dp),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 6.dp
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(10.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Canvas(modifier = Modifier.size(12.dp, 8.dp).offset(y = (-2).dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(path, color = Color.White)
        }
    }
}
