package ntou.project.grouping.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.model.TravelMode
import kotlinx.coroutines.launch
import com.google.firebase.firestore.DocumentReference
import ntou.project.grouping.models.Post
import ntou.project.grouping.models.User
import ntou.project.grouping.pages.methods.NavigationMethods
import java.text.SimpleDateFormat
import java.util.*

enum class PostCardStyle { SCHEDULE, SEARCH }

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
fun ExpandablePostCard(
    post: Post,
    isExpanded: Boolean,
    style: PostCardStyle,
    currentUid: String?,
    showQuitButton: Boolean = false,
    userLocation: LatLng? = null,
    onExpandClick: () -> Unit,
    onLocationClick: () -> Unit,
    onPrimaryAction: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    onKickParticipant: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAlreadyJoined = post.participants.contains(currentUid)
    val isFull = post.maxParticipants > 0 && post.participants.size >= post.maxParticipants

    var estimatedTime by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf<TravelMode?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onExpandClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (style == PostCardStyle.SEARCH && isFull)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
        else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (style) {
                PostCardStyle.SCHEDULE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(post.authorName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PostStatusTag(getPostStatus(post.eventTime, post.eventEndTime))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${post.participants.size} 人", fontSize = 12.sp, color = Color.Gray)
                            if (post.authorId == currentUid && onEditClick != null) {
                                IconButton(onClick = onEditClick, modifier = Modifier.size(28.dp).padding(start = 4.dp)) {
                                    Icon(Icons.Default.Edit, "編輯", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    ScheduleTimeDisplay(post.eventTime, post.eventEndTime)
                }

                PostCardStyle.SEARCH -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(post.authorName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        val statusText = if (post.maxParticipants > 0) "${post.participants.size}/${post.maxParticipants} 人" else "${post.participants.size} 人"
                        Text(
                            if (isFull) "額滿 ($statusText)" else statusText,
                            fontSize = 12.sp,
                            color = if (isFull) Color.Red else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    EventTimeDisplay(post.eventTime, post.eventEndTime)
                    if (post.locationName.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onLocationClick() }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = Color.Red)
                            Text(post.locationName, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (post.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            post.tags.forEach { tag ->
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                                    Text("#$tag", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    when (style) {
                        PostCardStyle.SCHEDULE -> {
                            Text("詳細內容", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(post.content, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

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
                                Text("地點: ${post.locationName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("參加人員", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            ParticipantDetailList(
                                participantIds = post.participants,
                                authorId = post.authorId,
                                onKickParticipant = onKickParticipant
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 交通時間估算
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        userLocation?.let { loc ->
                                            scope.launch {
                                                isCalculating = true
                                                currentMode = TravelMode.DRIVING
                                                estimatedTime = NavigationMethods.getTravelTime(context, loc, LatLng(post.latitude, post.longitude), TravelMode.DRIVING)
                                                isCalculating = false
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.DirectionsCar, "汽車", tint = if (currentMode == TravelMode.DRIVING) MaterialTheme.colorScheme.primary else Color.Gray)
                                    }
                                    IconButton(onClick = {
                                        userLocation?.let { loc ->
                                            scope.launch {
                                                isCalculating = true
                                                currentMode = TravelMode.BICYCLING
                                                estimatedTime = NavigationMethods.getTravelTime(context, loc, LatLng(post.latitude, post.longitude), TravelMode.BICYCLING)
                                                isCalculating = false
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.TwoWheeler, "機車", tint = if (currentMode == TravelMode.BICYCLING) MaterialTheme.colorScheme.primary else Color.Gray)
                                    }
                                    IconButton(onClick = {
                                        userLocation?.let { loc ->
                                            scope.launch {
                                                isCalculating = true
                                                currentMode = TravelMode.WALKING
                                                estimatedTime = NavigationMethods.getTravelTime(context, loc, LatLng(post.latitude, post.longitude), TravelMode.WALKING)
                                                isCalculating = false
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.DirectionsWalk, "步行", tint = if (currentMode == TravelMode.WALKING) MaterialTheme.colorScheme.primary else Color.Gray)
                                    }
                                    if (isCalculating) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(start = 8.dp), strokeWidth = 2.dp)
                                    } else {
                                        estimatedTime?.let {
                                            Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }

                                // 退出按鈕
                                if (showQuitButton) {
                                    IconButton(onClick = { onPrimaryAction?.invoke() }) {
                                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "退出", tint = Color.Red)
                                    }
                                }
                            }
                        }

                        PostCardStyle.SEARCH -> {
                            Text("詳細內容", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(post.content, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val participantText = if (post.maxParticipants > 0) "${post.participants.size} / ${post.maxParticipants}" else "${post.participants.size} 人"
                                Text("目前人數: $participantText", fontWeight = FontWeight.Bold, color = if (isFull) Color.Red else MaterialTheme.colorScheme.secondary)
                                if (!isFull || isAlreadyJoined) {
                                    IconButton(onClick = { onPrimaryAction?.invoke() }) {
                                        Icon(
                                            imageVector = if (isAlreadyJoined) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.PersonAdd,
                                            contentDescription = if (isAlreadyJoined) "取消參加" else "我要參加",
                                            tint = if (isAlreadyJoined) Color.Red else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PersonOff,
                                        contentDescription = "人數已滿",
                                        tint = Color.Gray,
                                        modifier = Modifier.padding(12.dp).size(24.dp)
                                    )
                                }
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
        Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color)) {
            Text(status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ScheduleTimeDisplay(start: String, end: String) {
    if (start.isBlank()) return
    val startDate = if (start.length >= 10) start.substring(0, 10) else start
    val endDate = if (end.length >= 10) end.substring(0, 10) else end
    val startTime = if (start.length >= 16) start.substring(11) else ""
    val endTime = if (end.length >= 16) end.substring(11) else ""

    if (startDate == endDate || end.isBlank()) {
        Text("時間: $startDate $startTime ${if (endTime.isNotEmpty()) "~ $endTime" else ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
    } else {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("起: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(start, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text("止: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(end, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun EventTimeDisplay(start: String, end: String) {
    if (start.isBlank()) return
    val startDate = if (start.length >= 10) start.substring(0, 10) else start
    val endDate = if (end.length >= 10) end.substring(0, 10) else end
    val startTime = if (start.length >= 16) start.substring(11) else ""
    val endTime = if (end.length >= 16) end.substring(11) else ""

    if (startDate == endDate || end.isBlank()) {
        Text("時間: $startDate $startTime ${if (endTime.isNotEmpty()) "~ $endTime" else ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
    } else {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("起: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(start, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text("止: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(end, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun ParticipantDetailList(
    participantIds: List<String>,
    authorId: String = "",
    onKickParticipant: ((String) -> Unit)? = null
) {
    val db = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf(listOf<User>()) }

    LaunchedEffect(participantIds) {
        if (participantIds.isNotEmpty()) {
            db.collection("users").whereIn("uid", participantIds.take(10)).get().addOnSuccessListener {
                users = it.toObjects(User::class.java)
            }
        } else { users = emptyList() }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (users.isEmpty()) {
            Text("目前尚無人參加", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
        } else {
            users.forEach { user ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            AsyncImage(
                                model = user.photoUrl.ifEmpty { "https://ui-avatars.com/api/?name=${user.displayName}" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(user.displayName, fontSize = 14.sp)
                    }
                    // 踢人按鈕：只對非發起人顯示
                    if (onKickParticipant != null && user.uid != authorId) {
                        IconButton(
                            onClick = { onKickParticipant(user.uid) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.PersonRemove,
                                contentDescription = "踢出",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
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
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
    }
}

fun performLeaveTransaction(
    db: FirebaseFirestore,
    postRef: DocumentReference,
    userUid: String?,
    onComplete: () -> Unit
) {
    if (userUid == null) return
    db.runTransaction { transaction ->
        val snapshot = transaction.get(postRef)
        @Suppress("UNCHECKED_CAST")
        val currentParticipants = snapshot.get("participants") as? List<String> ?: emptyList()
        val newList = currentParticipants.filter { it != userUid }
        if (newList.isEmpty()) transaction.delete(postRef) else transaction.update(postRef, "participants", newList)
    }.addOnSuccessListener { onComplete() }
}

@Composable
fun DisbandConfirmDialog(
    post: Post,
    db: FirebaseFirestore,
    currentUid: String?,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("是否解散活動？") },
        text = { Text("你是最後一位參加者，退出將會自動刪除此活動，確定要解散嗎？") },
        confirmButton = {
            TextButton(onClick = {
                performLeaveTransaction(db, db.collection("posts").document(post.id), currentUid) {
                    onComplete()
                }
                onDismiss()
            }) { Text("確定解散", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
