package ntou.project.grouping.pages.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ntou.project.grouping.components.DisbandConfirmDialog
import ntou.project.grouping.models.Post
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
@Composable
fun HomePage(
    paddingValues: PaddingValues,
    cameraPositionState: CameraPositionState,
    isInitialLocationSet: Boolean,
    onInitialLocationSet: () -> Unit,
    targetPost: Post? = null,
    onTargetHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var allPosts by remember { mutableStateOf(listOf<Post>()) }
    var selectedPostForDetail by remember { mutableStateOf<Post?>(null) }
    var currentUserLocation by remember { mutableStateOf<LatLng?>(null) }
    var friendIds by remember { mutableStateOf(setOf<String>()) }

    var showDisbandDialog by remember { mutableStateOf(false) }
    var postToDisband by remember { mutableStateOf<Post?>(null) }

    // 分類篩選
    val categories = listOf("全部", "好友", "羽球", "唱歌", "運動", "美食", "桌遊", "旅遊", "學習")
    var selectedCategory by remember { mutableStateOf("全部") }

    // 載入好友清單
    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            @Suppress("UNCHECKED_CAST")
            val friends = snapshot?.get("friends") as? List<String> ?: emptyList()
            friendIds = friends.toSet()
        }
    }

    // 過濾邏輯：分類 + 自動隱藏過期活動
    val filteredPosts = remember(allPosts, selectedCategory, friendIds) {
        val currentTime = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        allPosts.filter { post ->
            val matchesCategory = when (selectedCategory) {
                "全部" -> true
                "好友" -> post.authorId in friendIds
                else -> post.tags.contains(selectedCategory)
            }
            val isNotExpired = if (post.eventEndTime.isNotBlank()) {
                try {
                    val endTime = sdf.parse(post.eventEndTime)?.time ?: Long.MAX_VALUE
                    endTime > currentTime
                } catch (e: Exception) { true }
            } else { true }
            matchesCategory && isNotExpired
        }
    }

    var isMapLoaded by remember { mutableStateOf(false) }

    val activePost = remember(allPosts, selectedPostForDetail) {
        allPosts.find { it.id == selectedPostForDetail?.id } ?: selectedPostForDetail
    }

    var locationPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { currentUserLocation = LatLng(it.latitude, it.longitude) }
            }
        }
    }

    LaunchedEffect(currentUserLocation, isMapLoaded) {
        if (currentUserLocation != null && !isInitialLocationSet && targetPost == null && isMapLoaded) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(currentUserLocation!!, 15f)
            onInitialLocationSet()
        }
    }

    LaunchedEffect(targetPost, isMapLoaded) {
        if (targetPost != null && targetPost.latitude != 0.0 && isMapLoaded) {
            selectedPostForDetail = targetPost
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(targetPost.latitude, targetPost.longitude), 17f))
            onTargetHandled()
            onInitialLocationSet()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

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

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        if (showDisbandDialog && postToDisband != null) {
            DisbandConfirmDialog(
                post = postToDisband!!,
                db = db,
                currentUid = currentUser?.uid,
                onDismiss = { showDisbandDialog = false },
                onComplete = { showDisbandDialog = false; postToDisband = null; selectedPostForDetail = null }
            )
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
            onMapLoaded = { isMapLoaded = true }
        ) {
            filteredPosts.forEach { post ->
                key(post.id) {
                    MarkerComposable(
                        state = MarkerState(position = LatLng(post.latitude, post.longitude)),
                        title = post.title,
                        anchor = Offset(0.5f, 1f),
                        onClick = {
                            selectedPostForDetail = if (selectedPostForDetail?.id == post.id) null else post
                            true
                        }
                    ) { CategoryMarker(post) }
                }
            }
        }

        // 分類篩選列
        LazyRow(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 8.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category; selectedPostForDetail = null }, label = { Text(category) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White))
            }
        }

        // 回到目前位置按鈕
        if (locationPermissionGranted) {
            FloatingActionButton(onClick = { currentUserLocation?.let { scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f)) } } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp), containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary, elevation = FloatingActionButtonDefaults.elevation(4.dp), shape = CircleShape) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "回到目前位置", modifier = Modifier.size(22.dp))
            }
        }

        // 懸浮詳情視窗
        activePost?.let { post ->
            val projection = cameraPositionState.projection
            val screenPosition = remember(projection, cameraPositionState.isMoving) {
                projection?.toScreenLocation(LatLng(post.latitude, post.longitude))
            }
            screenPosition?.let { point ->
                val xOffset = with(density) { point.x.toDp() - 110.dp }
                val yOffset = with(density) { point.y.toDp() - 210.dp }
                Box(modifier = Modifier.offset(x = xOffset, y = yOffset)) {
                    PostDetailBubble(
                        post = post,
                        currentUserUid = currentUser?.uid,
                        onJoinClick = {
                            if (currentUser == null) return@PostDetailBubble
                            val isJoined = post.participants.contains(currentUser.uid)
                            
                            if (isJoined) {
                                // 退出邏輯
                                if (post.authorId == currentUser.uid || post.participants.size <= 1) {
                                    postToDisband = post
                                    showDisbandDialog = true
                                } else {
                                    db.collection("posts").document(post.id)
                                        .update("participants", FieldValue.arrayRemove(currentUser.uid))
                                }
                            } else {
                                // 參加邏輯
                                db.collection("posts").document(post.id)
                                    .update("participants", FieldValue.arrayUnion(currentUser.uid))
                            }
                        },
                        onClose = { selectedPostForDetail = null }
                    )
                }
            }
        }
    }
}

@Composable
fun PostDetailBubble(
    post: Post,
    currentUserUid: String?,
    onJoinClick: () -> Unit,
    onClose: () -> Unit
) {
    val isJoined = post.participants.contains(currentUserUid)

    Surface(
        modifier = Modifier.width(220.dp).wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = post.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }
            Text(text = "發起人: ${post.authorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(text = "時間: ${post.eventTime}", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = post.content, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "人數: ${post.participants.size}/${if(post.maxParticipants > 0) post.maxParticipants else "∞"}", style = MaterialTheme.typography.labelSmall)
                Button(onClick = onJoinClick, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(8.dp), colors = if (isJoined) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
                    Text(if (isJoined) "退出" else "參加", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategoryMarker(post: Post) {
    val icon = when {
        post.tags.contains("羽球") -> Icons.Default.SportsTennis
        post.tags.contains("唱歌") -> Icons.Default.Mic
        post.tags.contains("運動") -> Icons.Default.FitnessCenter
        post.tags.contains("美食") -> Icons.Default.Restaurant
        post.tags.contains("桌遊") -> Icons.Default.Casino
        post.tags.contains("旅遊") -> Icons.Default.Explore
        post.tags.contains("學習") -> Icons.Default.School
        else -> Icons.Default.Groups
    }
    val eventColor = MaterialTheme.colorScheme.tertiary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(45.dp), shape = CircleShape, color = Color.White, border = BorderStroke(2.dp, eventColor), shadowElevation = 6.dp) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(10.dp), tint = eventColor)
        }
        Canvas(modifier = Modifier.size(12.dp, 8.dp).offset(y = (-2).dp)) {
            val path = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }
            drawPath(path, color = Color.White)
        }
    }
}
