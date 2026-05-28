package ntou.project.grouping.pages.search

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import ntou.project.grouping.models.Post

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    paddingValues: PaddingValues,
    onNavigateToMap: (Post) -> Unit 
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    
    var allPosts by remember { mutableStateOf(listOf<Post>()) }
    var filteredPosts by remember { mutableStateOf(listOf<Post>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showDisbandDialog by remember { mutableStateOf(false) }

    val categories = listOf("全部", "羽球", "唱歌", "運動", "美食", "桌遊", "旅遊", "學習")

    LaunchedEffect(Unit) {
        db.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (snapshot != null) {
                    allPosts = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Post::class.java)?.copy(id = doc.id)
                    }
                }
                isLoading = false
            }
    }

    LaunchedEffect(searchQuery, selectedCategory, allPosts) {
        filteredPosts = allPosts.filter { post ->
            val matchesQuery = post.title.contains(searchQuery, ignoreCase = true) || 
                               post.content.contains(searchQuery, ignoreCase = true)
            val matchesCategory = if (selectedCategory == "全部") true else post.tags.contains(selectedCategory)
            matchesQuery && matchesCategory
        }
    }

    // --- 解散活動確認 ---
    if (showDisbandDialog && selectedPost != null) {
        AlertDialog(
            onDismissRequest = { showDisbandDialog = false },
            title = { Text("解散活動") },
            text = { Text("你是此活動最後一位參加者，退出將會解散活動，是否確定？") },
            confirmButton = {
                TextButton(onClick = {
                    val postRef = db.collection("posts").document(selectedPost!!.id)
                    performLeaveTransaction(db, postRef, currentUser?.uid) {
                        Toast.makeText(context, "活動已解散", Toast.LENGTH_SHORT).show()
                    }
                    showDisbandDialog = false
                    showDetailDialog = false
                    selectedPost = null
                }) {
                    Text("確定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisbandDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDetailDialog && selectedPost != null) {
        val post = selectedPost!!
        val isAlreadyJoined = post.participants.contains(currentUser?.uid)
        val isFull = post.maxParticipants > 0 && post.participants.size >= post.maxParticipants

        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text(text = post.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = "發起人: ${post.authorName}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    
                    // 詳情視窗中的時間顯示
                    EventTimeDisplay(start = post.eventTime, end = post.eventEndTime)
                    
                    if (post.locationName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { 
                                    showDetailDialog = false
                                    onNavigateToMap(post) 
                                }
                        ) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                            Text(text = post.locationName, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = post.content)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val participantText = if (post.maxParticipants > 0) "${post.participants.size} / ${post.maxParticipants}" else "${post.participants.size} 人"
                    Text(text = "目前人數: $participantText", fontWeight = FontWeight.Bold, color = if (isFull) Color.Red else MaterialTheme.colorScheme.secondary)
                }
            },
            confirmButton = {
                Button(
                    enabled = !isFull || isAlreadyJoined,
                    onClick = {
                        if (currentUser == null) return@Button
                        val postRef = db.collection("posts").document(post.id)
                        
                        if (isAlreadyJoined) {
                            if (post.participants.size == 1) {
                                showDisbandDialog = true
                            } else {
                                performLeaveTransaction(db, postRef, currentUser.uid) {
                                    Toast.makeText(context, "已取消參加", Toast.LENGTH_SHORT).show()
                                }
                                showDetailDialog = false
                            }
                        } else {
                            postRef.update("participants", FieldValue.arrayUnion(currentUser.uid))
                                .addOnSuccessListener {
                                    Toast.makeText(context, "報名成功！", Toast.LENGTH_SHORT).show()
                                }
                            showDetailDialog = false
                        }
                    }
                ) {
                    Text(if (isAlreadyJoined) "取消參加" else if (isFull) "人數已滿" else "我要參加")
                }
            },
            dismissButton = { TextButton(onClick = { showDetailDialog = false }) { Text("關閉") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("搜尋貼文...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) })
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(filteredPosts) { post ->
                    PostCard(
                        post = post,
                        onClick = {
                            selectedPost = post
                            showDetailDialog = true
                        },
                        onLocationClick = { onNavigateToMap(post) }
                    )
                }
            }
        }
    }
}

private fun performLeaveTransaction(db: FirebaseFirestore, postRef: com.google.firebase.firestore.DocumentReference, userUid: String?, onComplete: () -> Unit) {
    if (userUid == null) return
    db.runTransaction { transaction ->
        val snapshot = transaction.get(postRef)
        @Suppress("UNCHECKED_CAST")
        val currentParticipants = snapshot.get("participants") as? List<String> ?: emptyList()
        val newList = currentParticipants.filter { it != userUid }
        
        if (newList.isEmpty()) {
            transaction.delete(postRef)
        } else {
            transaction.update(postRef, "participants", newList)
        }
    }.addOnSuccessListener { onComplete() }
}

@Composable
fun EventTimeDisplay(start: String, end: String) {
    if (start.isBlank()) return
    
    // 假設格式為 "yyyy/MM/dd HH:mm"
    val startDate = if (start.length >= 10) start.substring(0, 10) else start
    val endDate = if (end.length >= 10) end.substring(0, 10) else end
    val startTime = if (start.length >= 16) start.substring(11) else ""
    val endTime = if (end.length >= 16) end.substring(11) else ""

    if (startDate == endDate || end.isBlank()) {
        // 同一天
        Text(
            text = "時間: $startDate $startTime ${if(endTime.isNotEmpty()) "~ $endTime" else ""}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    } else {
        // 不同天：分兩排顯示
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
fun PostCard(
    post: Post, 
    onClick: () -> Unit,
    onLocationClick: () -> Unit = {}
) {
    val isFull = post.maxParticipants > 0 && post.participants.size >= post.maxParticipants

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isFull) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)) 
                 else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = post.authorName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                val statusText = if (post.maxParticipants > 0) "${post.participants.size}/${post.maxParticipants} 人" else "${post.participants.size} 人"
                Text(text = if (isFull) "額滿 ($statusText)" else statusText, fontSize = 12.sp, color = if (isFull) Color.Red else MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            
            // 使用自定義的時間顯示組件
            EventTimeDisplay(start = post.eventTime, end = post.eventEndTime)

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
                    Text(
                        text = post.locationName, 
                        fontSize = 12.sp, 
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                post.tags.forEach { tag ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(text = "#$tag", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
