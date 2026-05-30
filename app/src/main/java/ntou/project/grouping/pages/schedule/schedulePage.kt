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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
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
import ntou.project.grouping.pages.LocationPickerDialog
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
    var selectedTab by remember { mutableStateOf(0) } 

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var expandedPostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
        }
        onDispose { createdListener?.remove(); joinedListener?.remove() }
    }

    val activePosts = remember(myJoinedPosts, myCreatedPosts) {
        val all = (myJoinedPosts + myCreatedPosts).distinctBy { it.id }
        val ongoing   = all.filter { getPostStatus(it.eventTime, it.eventEndTime) == "進行中" }
        val recruiting = all.filter { getPostStatus(it.eventTime, it.eventEndTime) == "揪人中" }
        val myCreated = all.filter { it.authorId == currentUser?.uid && getPostStatus(it.eventTime, it.eventEndTime) != "已結束" }
        Triple(ongoing, myCreated, recruiting)
    }

    val historyPosts = remember(myJoinedPosts, myCreatedPosts) {
        (myJoinedPosts + myCreatedPosts).distinctBy { it.id }.filter { getPostStatus(it.eventTime, it.eventEndTime) == "已結束" }.sortedByDescending { it.timestamp }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; expandedPostId = null }) { Text("我的活動", modifier = Modifier.padding(16.dp)) }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; expandedPostId = null }) { Text("歷史紀錄", modifier = Modifier.padding(16.dp)) }
        }

        if (isLoading && myCreatedPosts.isEmpty() && myJoinedPosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedTab == 0) {
                    val (ongoing, myCreated, recruiting) = activePosts
                    if (ongoing.isNotEmpty()) {
                        item { SectionHeader("進行中", Color(0xFF4CAF50)) }
                        items(ongoing, key = { "ong_${it.id}" }) { post -> 
                            ExpandableScheduleCard(post, expandedPostId == post.id, userLocation, true, currentUser?.uid, { expandedPostId = if (expandedPostId == post.id) null else post.id }, { onNavigateToMap(post) }) 
                        }
                    }
                    if (myCreated.isNotEmpty()) {
                        item { SectionHeader("我發起的", MaterialTheme.colorScheme.primary) }
                        items(myCreated, key = { "cre_${it.id}" }) { post -> 
                            ExpandableScheduleCard(post, expandedPostId == post.id, userLocation, true, currentUser?.uid, { expandedPostId = if (expandedPostId == post.id) null else post.id }, { onNavigateToMap(post) }) 
                        }
                    }
                    if (recruiting.isNotEmpty()) {
                        item { SectionHeader("參加中", MaterialTheme.colorScheme.primary) }
                        items(recruiting, key = { "rec_${it.id}" }) { post -> 
                            ExpandableScheduleCard(post, expandedPostId == post.id, userLocation, true, currentUser?.uid, { expandedPostId = if (expandedPostId == post.id) null else post.id }, { onNavigateToMap(post) }) 
                        }
                    }
                } else {
                    items(historyPosts, key = { it.id }) { post -> 
                        ExpandableScheduleCard(post, expandedPostId == post.id, userLocation, false, currentUser?.uid, { expandedPostId = if (expandedPostId == post.id) null else post.id }, { onNavigateToMap(post) }) 
                    }
                }
            }
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
    onLocationClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    var isEditing by remember { mutableStateOf(false) }

    // --- 編輯用狀態 ---
    var editTitle by remember { mutableStateOf(post.title) }
    var editContent by remember { mutableStateOf(post.content) }
    var editMax by remember { mutableStateOf(post.maxParticipants.toString()) }
    var editTime by remember { mutableStateOf(post.eventTime) }
    var editEndTime by remember { mutableStateOf(post.eventEndTime) }
    var editLat by remember { mutableDoubleStateOf(post.latitude) }
    var editLng by remember { mutableDoubleStateOf(post.longitude) }
    var editLocName by remember { mutableStateOf(post.locationName) }

    // --- 時間選擇器邏輯 ---
    val startCalendar = remember { Calendar.getInstance() }
    val endCalendar = remember { Calendar.getInstance() }
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()
    val startTimePickerState = rememberTimePickerState()
    val endTimePickerState = rememberTimePickerState()
    var pickerStep by remember { mutableStateOf(0) } 

    // --- 地點選擇器邏輯 ---
    var showMapPicker by remember { mutableStateOf(false) }

    if (showMapPicker) {
        LocationPickerDialog(
            initialLatLng = LatLng(editLat, editLng),
            onDismiss = { showMapPicker = false },
            onLocationConfirm = { latLng, name, addr ->
                editLat = latLng.latitude
                editLng = latLng.longitude
                editLocName = if (addr.contains(name)) addr else "$name ($addr)"
                showMapPicker = false
            }
        )
    }

    // 時間選擇對話框 (與發文頁面一致)
    if (pickerStep == 1) {
        DatePickerDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { startCalendar.timeInMillis = startDatePickerState.selectedDateMillis ?: System.currentTimeMillis(); pickerStep = 2 }) { Text("下一步") } }) { DatePicker(state = startDatePickerState, title = { Text("選擇新的開始日期", modifier = Modifier.padding(16.dp)) }) }
    } else if (pickerStep == 2) {
        AlertDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { startCalendar.set(Calendar.HOUR_OF_DAY, startTimePickerState.hour); startCalendar.set(Calendar.MINUTE, startTimePickerState.minute); editTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(startCalendar.time); pickerStep = 0 }) { Text("確定") } }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("選擇開始時間", fontWeight = FontWeight.Bold); TimePicker(state = startTimePickerState) } })
    } else if (pickerStep == 3) {
        DatePickerDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { endCalendar.timeInMillis = endDatePickerState.selectedDateMillis ?: System.currentTimeMillis(); pickerStep = 4 }) { Text("下一步") } }) { DatePicker(state = endDatePickerState, title = { Text("選擇新的結束日期", modifier = Modifier.padding(16.dp)) }) }
    } else if (pickerStep == 4) {
        AlertDialog(onDismissRequest = { pickerStep = 0 }, confirmButton = { TextButton(onClick = { endCalendar.set(Calendar.HOUR_OF_DAY, endTimePickerState.hour); endCalendar.set(Calendar.MINUTE, endTimePickerState.minute); editEndTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(endCalendar.time); pickerStep = 0 }) { Text("確定") } }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("選擇結束時間", fontWeight = FontWeight.Bold); TimePicker(state = endTimePickerState) } })
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(enabled = !isEditing) { onExpandClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isEditing) {
                // --- 專業編輯介面 ---
                Text("編輯活動", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("活動標題") }, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(8.dp))
                // 地點編輯按鈕
                Surface(modifier = Modifier.fillMaxWidth().clickable { showMapPicker = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = editLocName, fontSize = 13.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                // 時間編輯按鈕
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = editTime, onValueChange = {}, label = { Text("開始時間") }, modifier = Modifier.weight(1f).clickable { pickerStep = 1 }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = editEndTime, onValueChange = {}, label = { Text("結束時間") }, modifier = Modifier.weight(1f).clickable { pickerStep = 3 }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editMax, onValueChange = { if(it.all { c -> c.isDigit() }) editMax = it }, label = { Text("人數上限") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
            } else {
                // --- 正常顯示模式 ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = post.authorName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PostStatusTag(getPostStatus(post.eventTime, post.eventEndTime))
                        if (post.authorId == currentUid) {
                            IconButton(onClick = { isEditing = true }, modifier = Modifier.size(28.dp).padding(start = 8.dp)) {
                                Icon(Icons.Default.Edit, "編輯", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Text(text = post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                ScheduleTimeDisplay(post.eventTime, post.eventEndTime)

                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        HorizontalDivider(thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("詳細內容", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(post.content, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onLocationClick() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(post.locationName, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("目前報名人數 (${post.participants.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ParticipantDetailList(post.participants)

                        if (showQuitButton && post.authorId != currentUid) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { 
                                    db.collection("posts").document(post.id).update("participants", FieldValue.arrayRemove(currentUid))
                                    Toast.makeText(context, "已退出活動", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red)
                            ) { Text("退出活動") }
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
    var users by remember { mutableStateOf(listOf<User>()) }
    
    LaunchedEffect(participantIds) {
        if (participantIds.isNotEmpty()) {
            db.collection("users").whereIn("uid", participantIds).get().addOnSuccessListener { 
                users = it.toObjects(User::class.java)
            }
        } else { users = emptyList() }
    }

    if (users.isEmpty()) {
        Text("目前尚無人參加", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
    } else {
        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(users) { user ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        AsyncImage(model = user.photoUrl.ifEmpty { "https://ui-avatars.com/api/?name=${user.displayName}" }, contentDescription = null, contentScale = ContentScale.Crop)
                    }
                    Text(user.displayName, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Box(modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
    }
}

@Composable
fun PostStatusTag(status: String) {
    val color = when (status) { "進行中" -> Color(0xFF4CAF50); "已結束" -> Color.Gray; "揪人中" -> Color(0xFFF57C00); else -> null }
    if (color != null) {
        Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color)) {
            Text(text = status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

fun getPostStatus(start: String, end: String): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    val now = Calendar.getInstance().time
    return try {
        val s = sdf.parse(start); val e = sdf.parse(end)
        if (s == null || e == null) return "揪人中"
        when { now.before(s) -> "揪人中"; now.after(e) -> "已結束"; else -> "進行中" }
    } catch (e: Exception) { "揪人中" }
}

@Composable
fun ScheduleTimeDisplay(start: String, end: String) {
    Text(text = "時間: $start ~ ${end.takeLast(5)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
}
