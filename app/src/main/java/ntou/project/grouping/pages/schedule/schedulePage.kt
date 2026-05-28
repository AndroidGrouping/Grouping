package ntou.project.grouping.pages.schedule

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.model.TravelMode
import kotlinx.coroutines.launch
import ntou.project.grouping.models.Post
import ntou.project.grouping.models.User
import ntou.project.grouping.pages.methods.NavigationMethods
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
@Composable
fun SchedulePage(
    paddingValues: PaddingValues,
    onNavigateToMap: (Post) -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var myCreatedPosts by remember { mutableStateOf(listOf<Post>()) }
    var myJoinedPosts by remember { mutableStateOf(listOf<Post>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0: 參加中, 1: 歷史紀錄

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var expandedPostId by remember { mutableStateOf<String?>(null) }

    var showDisbandDialog by remember { mutableStateOf(false) }
    var postToDisband by remember { mutableStateOf<Post?>(null) }

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

    DisposableEffect(currentUser) {
        var createdListener: ListenerRegistration? = null
        var joinedListener: ListenerRegistration? = null

        if (currentUser != null) {
            isLoading = true
            createdListener = db.collection("posts")
                .whereEqualTo("authorId", currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        myCreatedPosts = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Post::class.java)?.copy(id = doc.id)
                        }.sortedByDescending { it.timestamp }
                    }
                    isLoading = false
                }

            joinedListener = db.collection("posts")
                .whereArrayContains("participants", currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        myJoinedPosts = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Post::class.java)?.copy(id = doc.id)
                        }.sortedByDescending { it.timestamp }
                    }
                    isLoading = false
                }
        } else {
            isLoading = false
        }

        onDispose {
            createdListener?.remove()
            joinedListener?.remove()
        }
    }

    // 「參加中」tab 的三個分區（不含已結束）
    val activePosts = remember(myJoinedPosts, myCreatedPosts) {
        // 合併自己參加 + 自己發起（避免重複）
        val all = (myJoinedPosts + myCreatedPosts).distinctBy { it.id }
        val ongoing   = all.filter { getPostStatus(it.eventTime, it.eventEndTime) == "進行中" }
        val recruiting = all.filter { getPostStatus(it.eventTime, it.eventEndTime) == "揪人中" }
        val myCreated = all.filter {
            it.authorId == currentUser?.uid &&
            getPostStatus(it.eventTime, it.eventEndTime) != "已結束"
        }
        Triple(ongoing, myCreated, recruiting)
    }

    // 「歷史紀錄」tab：自己參加過（含發起）且已結束
    val historyPosts = remember(myJoinedPosts, myCreatedPosts) {
        (myJoinedPosts + myCreatedPosts)
            .distinctBy { it.id }
            .filter { getPostStatus(it.eventTime, it.eventEndTime) == "已結束" }
            .sortedByDescending { it.timestamp }
    }

    if (showDisbandDialog && postToDisband != null) {
        AlertDialog(
            onDismissRequest = { showDisbandDialog = false },
            title = { Text("是否解散活動？") },
            text = { Text("你是最後一位參加者，退出將會自動刪除此活動，確定要解散嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    val postRef = db.collection("posts").document(postToDisband!!.id)
                    db.runTransaction { transaction ->
                        transaction.delete(postRef)
                    }.addOnSuccessListener {
                        Toast.makeText(context, "活動已解散", Toast.LENGTH_SHORT).show()
                    }
                    showDisbandDialog = false
                    postToDisband = null
                    expandedPostId = null
                }) {
                    Text("確定解散", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisbandDialog = false }) { Text("取消") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; expandedPostId = null }) {
                Text("我的活動", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; expandedPostId = null }) {
                Text("歷史紀錄", modifier = Modifier.padding(16.dp))
            }
        }

        if (isLoading && myCreatedPosts.isEmpty() && myJoinedPosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else when (selectedTab) {

            // ── 參加中 ──────────────────────────────────────────────────────
            0 -> {
                val (ongoing, myCreated, recruiting) = activePosts
                val isEmpty = ongoing.isEmpty() && myCreated.isEmpty() && recruiting.isEmpty()

                if (isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("目前沒有進行中的行程", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 區塊一：進行中
                        if (ongoing.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "進行中",
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            items(ongoing, key = { "ongoing_${it.id}" }) { post ->
                                ExpandableScheduleCard(
                                    post = post,
                                    isExpanded = expandedPostId == post.id,
                                    userLocation = userLocation,
                                    showQuitButton = true,
                                    onExpandClick = {
                                        expandedPostId = if (expandedPostId == post.id) null else post.id
                                    },
                                    onQuitClick = {
                                        handleQuit(
                                            post = post,
                                            currentUid = currentUser?.uid,
                                            db = db,
                                            context = context,
                                            onDisband = {
                                                postToDisband = post
                                                showDisbandDialog = true
                                            },
                                            onExpand = { expandedPostId = null }
                                        )
                                    },
                                    onLocationClick = { onNavigateToMap(post) }
                                )
                            }
                        }

                        // 區塊二：我發起的（未結束）
                        if (myCreated.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "我發起的",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(myCreated, key = { "created_${it.id}" }) { post ->
                                ExpandableScheduleCard(
                                    post = post,
                                    isExpanded = expandedPostId == post.id,
                                    userLocation = userLocation,
                                    showQuitButton = true,
                                    onExpandClick = {
                                        expandedPostId = if (expandedPostId == post.id) null else post.id
                                    },
                                    onQuitClick = {
                                        handleQuit(
                                            post = post,
                                            currentUid = currentUser?.uid,
                                            db = db,
                                            context = context,
                                            onDisband = {
                                                postToDisband = post
                                                showDisbandDialog = true
                                            },
                                            onExpand = { expandedPostId = null }
                                        )
                                    },
                                    onLocationClick = { onNavigateToMap(post) }
                                )
                            }
                        }

                        // 區塊三：參加中
                        if (recruiting.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "參加中",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(recruiting, key = { "recruiting_${it.id}" }) { post ->
                                ExpandableScheduleCard(
                                    post = post,
                                    isExpanded = expandedPostId == post.id,
                                    userLocation = userLocation,
                                    showQuitButton = true,
                                    onExpandClick = {
                                        expandedPostId = if (expandedPostId == post.id) null else post.id
                                    },
                                    onQuitClick = {
                                        handleQuit(
                                            post = post,
                                            currentUid = currentUser?.uid,
                                            db = db,
                                            context = context,
                                            onDisband = {
                                                postToDisband = post
                                                showDisbandDialog = true
                                            },
                                            onExpand = { expandedPostId = null }
                                        )
                                    },
                                    onLocationClick = { onNavigateToMap(post) }
                                )
                            }
                        }
                    }
                }
            }

            // ── 歷史紀錄 ─────────────────────────────────────────────────────
            1 -> {
                if (historyPosts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("尚無歷史紀錄", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyPosts, key = { it.id }) { post ->
                            ExpandableScheduleCard(
                                post = post,
                                isExpanded = expandedPostId == post.id,
                                userLocation = userLocation,
                                showQuitButton = false,   // 歷史紀錄不顯示退出按鈕
                                onExpandClick = {
                                    expandedPostId = if (expandedPostId == post.id) null else post.id
                                },
                                onQuitClick = {},
                                onLocationClick = { onNavigateToMap(post) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 輔助函式：退出 / 解散邏輯 ────────────────────────────────────────────────
private fun handleQuit(
    post: Post,
    currentUid: String?,
    db: FirebaseFirestore,
    context: android.content.Context,
    onDisband: () -> Unit,
    onExpand: () -> Unit
) {
    if (currentUid == null) return
    if (post.participants.size <= 1) {
        onDisband()
    } else {
        db.collection("posts").document(post.id)
            .update("participants", FieldValue.arrayRemove(currentUid))
            .addOnSuccessListener {
                Toast.makeText(context, "已退出活動", Toast.LENGTH_SHORT).show()
                onExpand()
            }
    }
}

// ── 區塊標題 ─────────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = color
        )
    }
}

@Composable
fun ExpandableScheduleCard(
    post: Post,
    isExpanded: Boolean,
    userLocation: LatLng?,
    showQuitButton: Boolean,
    onExpandClick: () -> Unit,
    onQuitClick: () -> Unit,
    onLocationClick: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.authorName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val status = getPostStatus(post.eventTime, post.eventEndTime)
                    PostStatusTag(status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "${post.participants.size} 人", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            ScheduleTimeDisplay(start = post.eventTime, end = post.eventEndTime)

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    Text(text = "詳細內容", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = post.content,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLocationClick() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "地點: ${post.locationName}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "參加人員", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    ParticipantDetailList(participantIds = post.participants)

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 交通時間估算
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            IconButton(onClick = {
                                userLocation?.let {
                                    scope.launch {
                                        isCalculating = true
                                        currentMode = TravelMode.BICYCLING
                                        estimatedTime = NavigationMethods.getTravelTime(context, it, LatLng(post.latitude, post.longitude), TravelMode.BICYCLING)
                                        isCalculating = false
                                    }
                                }
                            }) {
                                Icon(Icons.Default.TwoWheeler, "機車", tint = if (currentMode == TravelMode.BICYCLING) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp).padding(start = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                estimatedTime?.let {
                                    Text(
                                        text = it,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }

                        // 退出按鈕：歷史紀錄不顯示
                        if (showQuitButton) {
                            IconButton(onClick = onQuitClick) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, "退出", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostStatusTag(status: String) {
    val color = when (status) {
        "進行中" -> Color(0xFF4CAF50)
        "已結束" -> Color.Gray
        "揪人中" -> Color(0xFFF57C00)
        else -> null
    }

    if (color != null) {
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, color)
        ) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

fun getPostStatus(startTimeStr: String, endTimeStr: String): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    val now = Calendar.getInstance().time
    return try {
        val start = sdf.parse(startTimeStr)
        val end = sdf.parse(endTimeStr)
        if (start == null || end == null) return "揪人中"
        when {
            now.before(start) -> "揪人中"
            now.after(end)    -> "已結束"
            else              -> "進行中"
        }
    } catch (e: Exception) {
        "揪人中"
    }
}

@Composable
fun ScheduleTimeDisplay(start: String, end: String) {
    if (start.isBlank()) return

    val startDate = if (start.length >= 10) start.substring(0, 10) else start
    val endDate   = if (end.length >= 10)   end.substring(0, 10)   else end
    val startTime = if (start.length >= 16) start.substring(11)    else ""
    val endTime   = if (end.length >= 16)   end.substring(11)      else ""

    if (startDate == endDate || end.isBlank()) {
        Text(
            text = "時間: $startDate $startTime ${if (endTime.isNotEmpty()) "~ $endTime" else ""}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    } else {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "起: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(text = start, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(text = "止: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(text = end, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
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
