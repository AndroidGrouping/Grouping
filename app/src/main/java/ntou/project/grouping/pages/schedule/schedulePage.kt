package ntou.project.grouping.pages.schedule

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import ntou.project.grouping.components.DisbandConfirmDialog
import ntou.project.grouping.components.ExpandablePostCard
import ntou.project.grouping.components.PostCardStyle
import ntou.project.grouping.components.SectionHeader
import ntou.project.grouping.components.getPostStatus
import ntou.project.grouping.components.performLeaveTransaction
import ntou.project.grouping.models.Post
import ntou.project.grouping.pages.LocationPickerDialog
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
    var selectedTab by remember { mutableStateOf(0) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var expandedPostId by remember { mutableStateOf<String?>(null) }
    var showDisbandDialog by remember { mutableStateOf(false) }
    var postToDisband by remember { mutableStateOf<Post?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
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
            createdListener = db.collection("posts").whereEqualTo("authorId", currentUser.uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null) myCreatedPosts = snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }.sortedByDescending { it.timestamp }
                isLoading = false
            }
            joinedListener = db.collection("posts").whereArrayContains("participants", currentUser.uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null) myJoinedPosts = snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }.sortedByDescending { it.timestamp }
                isLoading = false
            }
        } else { isLoading = false }
        onDispose { createdListener?.remove(); joinedListener?.remove() }
    }

    val activePosts = remember(myJoinedPosts, myCreatedPosts) {
        val all = (myJoinedPosts + myCreatedPosts).distinctBy { it.id }
        val myCreated = all.filter { it.authorId == currentUser?.uid && getPostStatus(it.eventTime, it.eventEndTime) != "已結束" }
        val myCreatedIds = myCreated.map { it.id }.toSet()
        val ongoing = all.filter { it.id !in myCreatedIds && getPostStatus(it.eventTime, it.eventEndTime) == "進行中" }
        val recruiting = all.filter { it.id !in myCreatedIds && getPostStatus(it.eventTime, it.eventEndTime) == "揪人中" }
        Triple(ongoing, myCreated, recruiting)
    }

    val historyPosts = remember(myJoinedPosts, myCreatedPosts) {
        (myJoinedPosts + myCreatedPosts).distinctBy { it.id }
            .filter { getPostStatus(it.eventTime, it.eventEndTime) == "已結束" }
            .sortedByDescending { it.timestamp }
    }

    if (showDisbandDialog && postToDisband != null) {
        DisbandConfirmDialog(
            post = postToDisband!!,
            db = db,
            currentUid = currentUser?.uid,
            onDismiss = { showDisbandDialog = false },
            onComplete = { showDisbandDialog = false; postToDisband = null; expandedPostId = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; expandedPostId = null }) { Text("我的活動", modifier = Modifier.padding(16.dp)) }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; expandedPostId = null }) { Text("歷史紀錄", modifier = Modifier.padding(16.dp)) }
        }

        if (isLoading && myCreatedPosts.isEmpty() && myJoinedPosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else when (selectedTab) {
            0 -> {
                val (ongoing, myCreated, recruiting) = activePosts
                val isEmpty = ongoing.isEmpty() && myCreated.isEmpty() && recruiting.isEmpty()
                if (isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("目前沒有進行中的行程", color = Color.Gray) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (ongoing.isNotEmpty()) {
                            item { SectionHeader("進行中", Color(0xFF4CAF50)) }
                            items(ongoing, key = { "ong_${it.id}" }) { post ->
                                ExpandableScheduleCard(
                                    post = post, isExpanded = expandedPostId == post.id,
                                    userLocation = userLocation, showQuitButton = true, currentUid = currentUser?.uid,
                                    onExpandClick = { expandedPostId = if (expandedPostId == post.id) null else post.id },
                                    onLocationClick = { onNavigateToMap(post) },
                                    onQuit = { handleQuit(post, currentUser?.uid, db, context, onDisband = { postToDisband = post; showDisbandDialog = true }, onExpand = { expandedPostId = null }) }
                                )
                            }
                        }
                        if (myCreated.isNotEmpty()) {
                            item { SectionHeader("我發起的", MaterialTheme.colorScheme.primary) }
                            items(myCreated, key = { "cre_${it.id}" }) { post ->
                                ExpandableScheduleCard(
                                    post = post, isExpanded = expandedPostId == post.id,
                                    userLocation = userLocation, showQuitButton = true, currentUid = currentUser?.uid,
                                    onExpandClick = { expandedPostId = if (expandedPostId == post.id) null else post.id },
                                    onLocationClick = { onNavigateToMap(post) },
                                    onQuit = { handleQuit(post, currentUser?.uid, db, context, onDisband = { postToDisband = post; showDisbandDialog = true }, onExpand = { expandedPostId = null }) },
                                    onKickParticipant = { kickUid ->
                                        db.collection("posts").document(post.id)
                                            .update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(kickUid))
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "已將該成員移除", Toast.LENGTH_SHORT).show()
                                                // 寫入踢人通知，觸發 Cloud Function 發送推播
                                                db.collection("kickNotifications").add(
                                                    mapOf(
                                                        "kickedUid" to kickUid,
                                                        "postTitle" to post.title,
                                                        "authorName" to post.authorName,
                                                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                                    )
                                                )
                                            }
                                    }
                                )
                            }
                        }
                        if (recruiting.isNotEmpty()) {
                            item { SectionHeader("參加中", MaterialTheme.colorScheme.primary) }
                            items(recruiting, key = { "rec_${it.id}" }) { post ->
                                ExpandableScheduleCard(
                                    post = post, isExpanded = expandedPostId == post.id,
                                    userLocation = userLocation, showQuitButton = true, currentUid = currentUser?.uid,
                                    onExpandClick = { expandedPostId = if (expandedPostId == post.id) null else post.id },
                                    onLocationClick = { onNavigateToMap(post) },
                                    onQuit = { handleQuit(post, currentUser?.uid, db, context, onDisband = { postToDisband = post; showDisbandDialog = true }, onExpand = { expandedPostId = null }) }
                                )
                            }
                        }
                    }
                }
            }
            1 -> {
                if (historyPosts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("尚無歷史紀錄", color = Color.Gray) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(historyPosts, key = { it.id }) { post ->
                            ExpandableScheduleCard(
                                post = post, isExpanded = expandedPostId == post.id,
                                userLocation = userLocation, showQuitButton = false, currentUid = currentUser?.uid,
                                onExpandClick = { expandedPostId = if (expandedPostId == post.id) null else post.id },
                                onLocationClick = { onNavigateToMap(post) },
                                onQuit = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun handleQuit(
    post: Post,
    currentUid: String?,
    db: FirebaseFirestore,
    context: android.content.Context,
    onDisband: () -> Unit,
    onExpand: () -> Unit
) {
    if (currentUid == null) return
    // 如果是主揪或是最後一個人，才跳解散提醒
    if (post.authorId == currentUid || post.participants.size <= 1) {
        onDisband()
    } else {
        val postRef = db.collection("posts").document(post.id)
        performLeaveTransaction(db, postRef, currentUid) {
            Toast.makeText(context, "已退出活動", Toast.LENGTH_SHORT).show()
            onExpand()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableScheduleCard(
    post: Post,
    isExpanded: Boolean,
    userLocation: LatLng?,
    showQuitButton: Boolean,
    currentUid: String?,
    onExpandClick: () -> Unit,
    onLocationClick: () -> Unit,
    onQuit: () -> Unit,
    onKickParticipant: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var isEditing by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf(post.title) }
    var editContent by remember { mutableStateOf(post.content) }
    var editMax by remember { mutableStateOf(post.maxParticipants.toString()) }
    var editTime by remember { mutableStateOf(post.eventTime) }
    var editEndTime by remember { mutableStateOf(post.eventEndTime) }
    var editLat by remember { mutableDoubleStateOf(post.latitude) }
    var editLng by remember { mutableDoubleStateOf(post.longitude) }
    var editLocName by remember { mutableStateOf(post.locationName) }

    val startCalendar = remember { Calendar.getInstance() }
    val endCalendar = remember { Calendar.getInstance() }
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()
    val startTimePickerState = rememberTimePickerState()
    val endTimePickerState = rememberTimePickerState()
    var pickerStep by remember { mutableStateOf(0) }
    var showMapPicker by remember { mutableStateOf(false) }

    if (showMapPicker) {
        LocationPickerDialog(
            initialLatLng = LatLng(editLat, editLng),
            onDismiss = { showMapPicker = false },
            onLocationConfirm = { latLng, name, addr ->
                editLat = latLng.latitude; editLng = latLng.longitude
                editLocName = if (addr.contains(name)) addr else "$name ($addr)"
                showMapPicker = false
            }
        )
    }

    if (pickerStep == 1) {
        DatePickerDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { startCalendar.timeInMillis = startDatePickerState.selectedDateMillis ?: System.currentTimeMillis(); pickerStep = 2 }) { Text("下一步") } }) { DatePicker(state = startDatePickerState, title = { Text("選擇新的開始日期", modifier = Modifier.padding(16.dp)) }) }
    } else if (pickerStep == 2) {
        AlertDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { startCalendar.set(Calendar.HOUR_OF_DAY, startTimePickerState.hour); startCalendar.set(Calendar.MINUTE, startTimePickerState.minute); editTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(startCalendar.time); pickerStep = 0 }) { Text("確定") } }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("選擇開始時間", fontWeight = FontWeight.Bold); TimePicker(state = startTimePickerState) } })
    } else if (pickerStep == 3) {
        DatePickerDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { endCalendar.timeInMillis = endDatePickerState.selectedDateMillis ?: System.currentTimeMillis(); pickerStep = 4 }) { Text("下一步") } }) { DatePicker(state = endDatePickerState, title = { Text("選擇新的結束日期", modifier = Modifier.padding(16.dp)) }) }
    } else if (pickerStep == 4) {
        AlertDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { endCalendar.set(Calendar.HOUR_OF_DAY, endTimePickerState.hour); endCalendar.set(Calendar.MINUTE, endTimePickerState.minute); editEndTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(endCalendar.time); pickerStep = 0 }) { Text("確定") } }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("選擇結束時間", fontWeight = FontWeight.Bold); TimePicker(state = endTimePickerState) } })
    }

    if (isEditing) {
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("編輯活動", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("活動標題") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Surface(modifier = Modifier.fillMaxWidth().clickable { showMapPicker = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = editLocName, fontSize = 13.sp, maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = editTime, onValueChange = {}, label = { Text("開始時間") }, modifier = Modifier.weight(1f).clickable { pickerStep = 1 }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = editEndTime, onValueChange = {}, label = { Text("結束時間") }, modifier = Modifier.weight(1f).clickable { pickerStep = 3 }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editMax, onValueChange = { if (it.all { c -> c.isDigit() }) editMax = it }, label = { Text("人數上限") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editContent, onValueChange = { editContent = it }, label = { Text("詳細內容") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { isEditing = false }) { Text("取消") }
                    Button(onClick = {
                        db.collection("posts").document(post.id).update(mapOf(
                            "title" to editTitle, "content" to editContent, "eventTime" to editTime,
                            "eventEndTime" to editEndTime, "maxParticipants" to (editMax.toIntOrNull() ?: 0),
                            "latitude" to editLat, "longitude" to editLng, "locationName" to editLocName
                        )).addOnSuccessListener { isEditing = false; Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show() }
                    }) { Text("儲存變更") }
                }
            }
        }
    } else {
        ExpandablePostCard(
            post = post,
            isExpanded = isExpanded,
            style = PostCardStyle.SCHEDULE,
            currentUid = currentUid,
            showQuitButton = showQuitButton,
            userLocation = userLocation,
            onExpandClick = onExpandClick,
            onLocationClick = onLocationClick,
            onPrimaryAction = onQuit,
            onEditClick = if (showQuitButton) { { isEditing = true } } else null,
            onKickParticipant = onKickParticipant
        )
    }
}
