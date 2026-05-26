package ntou.project.grouping.pages.user

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import ntou.project.grouping.models.User

@Composable
fun FriendPage(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    var friendList by remember { mutableStateOf<List<User>>(emptyList()) }
    var incomingRequests by remember { mutableStateOf<List<User>>(emptyList()) }
    var outgoingRequests by remember { mutableStateOf<List<User>>(emptyList()) }
    var outgoingRequestUids by remember { mutableStateOf<List<String>>(emptyList()) }

    var globalSearchQuery by remember { mutableStateOf("") }
    var localFriendSearchQuery by remember { mutableStateOf("") }
    var isSearchingGlobal by remember { mutableStateOf(false) }
    var foundUserGlobal by remember { mutableStateOf<User?>(null) }
    var isLoadingData by remember { mutableStateOf(true) }

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: 好友, 1: 好友請求
    var expandedFriendId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    val user = snapshot?.toObject(User::class.java)
                    val friendUids = user?.friends ?: emptyList()
                    val incomingUids = user?.incomingRequests ?: emptyList()
                    val outgoingUids = user?.outgoingRequests ?: emptyList()
                    outgoingRequestUids = outgoingUids

                    if (friendUids.isNotEmpty()) {
                        db.collection("users").whereIn("uid", friendUids).get()
                            .addOnSuccessListener { friendList = it.toObjects(User::class.java) }
                    } else { friendList = emptyList() }

                    if (incomingUids.isNotEmpty()) {
                        db.collection("users").whereIn("uid", incomingUids).get()
                            .addOnSuccessListener { incomingRequests = it.toObjects(User::class.java) }
                    } else { incomingRequests = emptyList() }

                    if (outgoingUids.isNotEmpty()) {
                        db.collection("users").whereIn("uid", outgoingUids).get()
                            .addOnSuccessListener { outgoingRequests = it.toObjects(User::class.java) }
                    } else { outgoingRequests = emptyList() }

                    isLoadingData = false
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "好友與請求", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        TabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.fillMaxWidth()) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                Text("好友", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                Text("好友請求", modifier = Modifier.padding(12.dp))
            }
        }

        if (selectedTabIndex == 0) {
            // --- 好友分頁 ---
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                OutlinedTextField(
                    value = localFriendSearchQuery,
                    onValueChange = { localFriendSearchQuery = it },
                    placeholder = { Text("搜尋我的好友...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                val filteredFriends = friendList.filter {
                    it.displayName.contains(localFriendSearchQuery, ignoreCase = true) ||
                            it.email.contains(localFriendSearchQuery, ignoreCase = true)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoadingData) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (filteredFriends.isEmpty()) {
                    Text("尚無好友", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredFriends) { friend ->
                            FriendListItem(
                                user = friend,
                                isExpanded = expandedFriendId == friend.uid,
                                onExpandClick = { expandedFriendId = if (expandedFriendId == friend.uid) null else friend.uid },
                                onDeleteClick = {
                                    removeFriend(db, currentUser!!.uid, friend.uid)
                                    Toast.makeText(context, "已刪除好友", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // --- 好友請求分頁 ---
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // 發送請求的搜尋欄 (現在移到了這裡)
                OutlinedTextField(
                    value = globalSearchQuery,
                    onValueChange = { globalSearchQuery = it },
                    label = { Text("輸入 Email 發送好友請求") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (globalSearchQuery.isNotBlank()) {
                                isSearchingGlobal = true
                                db.collection("users").whereEqualTo("email", globalSearchQuery.trim()).get()
                                    .addOnSuccessListener { results ->
                                        isSearchingGlobal = false
                                        if (!results.isEmpty) {
                                            val user = results.documents[0].toObject(User::class.java)
                                            if (user?.uid == currentUser?.uid) {
                                                Toast.makeText(context, "不能新增自己", Toast.LENGTH_SHORT).show()
                                                foundUserGlobal = null
                                            } else { foundUserGlobal = user }
                                        } else {
                                            foundUserGlobal = null
                                            Toast.makeText(context, "找不到該使用者", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                        }) {
                            if (isSearchingGlobal) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else Icon(Icons.Default.Search, null)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                foundUserGlobal?.let { user ->
                    val isFriend = friendList.any { it.uid == user.uid }
                    val hasSent = outgoingRequestUids.contains(user.uid)
                    val hasReceived = incomingRequests.any { it.uid == user.uid }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        ListItem(
                            headlineContent = { Text(user.displayName, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(user.email) },
                            leadingContent = { UserAvatar(user.photoUrl, user.displayName) },
                            trailingContent = {
                                when {
                                    isFriend -> Text("已是好友", color = Color.Gray)
                                    hasSent -> Text("已送出", color = Color.Gray)
                                    hasReceived -> Button(onClick = { acceptFriendRequest(db, currentUser!!.uid, user.uid) }) { Text("接受") }
                                    else -> IconButton(onClick = {
                                        sendFriendRequest(db, currentUser!!.uid, user.uid)
                                        foundUserGlobal = null
                                        globalSearchQuery = ""
                                        Toast.makeText(context, "請求已送出", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (incomingRequests.isNotEmpty()) {
                        item { Text("收到的請求", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        items(incomingRequests) { requester ->
                            RequestItem(
                                user = requester,
                                isIncoming = true,
                                onAccept = { acceptFriendRequest(db, currentUser!!.uid, requester.uid) },
                                onDecline = { declineOrCancelRequest(db, currentUser!!.uid, requester.uid, true) }
                            )
                        }
                    }

                    if (outgoingRequests.isNotEmpty()) {
                        item { Text("已送出的請求", fontWeight = FontWeight.Bold, color = Color.Gray) }
                        items(outgoingRequests) { target ->
                            RequestItem(
                                user = target,
                                isIncoming = false,
                                onCancel = { declineOrCancelRequest(db, currentUser!!.uid, target.uid, false) }
                            )
                        }
                    }

                    if (incomingRequests.isEmpty() && outgoingRequests.isEmpty() && foundUserGlobal == null) {
                        item { Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { Text("目前沒有任何請求", color = Color.Gray) } }
                    }
                }
            }
        }
    }
}

@Composable
fun RequestItem(
    user: User,
    isIncoming: Boolean,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(user.displayName, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(user.email, fontSize = 12.sp) },
            leadingContent = { UserAvatar(user.photoUrl, user.displayName) },
            trailingContent = {
                if (isIncoming) {
                    Row {
                        IconButton(onClick = onAccept) { Icon(Icons.Default.Check, "接受", tint = Color(0xFF4CAF50)) }
                        IconButton(onClick = onDecline) { Icon(Icons.Default.Close, "拒絕", tint = Color.Red) }
                    }
                } else {
                    TextButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("收回", color = Color.Red)
                    }
                }
            }
        )
    }
}

@Composable
private fun FriendListItem(user: User, isExpanded: Boolean, onExpandClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().animateContentSize().clickable { onExpandClick() }, shape = RoundedCornerShape(12.dp)) {
        Column {
            ListItem(
                headlineContent = { Text(user.displayName, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(user.email, fontSize = 12.sp) },
                leadingContent = { UserAvatar(user.photoUrl, user.displayName, size = 48) }
            )
            if (isExpanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                    Text("自我介紹", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(text = user.bio.ifBlank { "尚未填寫自我介紹" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDeleteClick, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("刪除好友")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(url: String, name: String, size: Int = 40) {
    Surface(modifier = Modifier.size(size.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(text = name.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- 邏輯操作函數 ---

private fun sendFriendRequest(db: FirebaseFirestore, myUid: String, targetUid: String) {
    db.collection("users").document(myUid).update("outgoingRequests", FieldValue.arrayUnion(targetUid))
    db.collection("users").document(targetUid).update("incomingRequests", FieldValue.arrayUnion(myUid))
}

private fun acceptFriendRequest(db: FirebaseFirestore, myUid: String, targetUid: String) {
    val myRef = db.collection("users").document(myUid)
    val targetRef = db.collection("users").document(targetUid)
    db.runBatch { batch ->
        batch.update(myRef, "friends", FieldValue.arrayUnion(targetUid))
        batch.update(targetRef, "friends", FieldValue.arrayUnion(myUid))
        batch.update(myRef, "incomingRequests", FieldValue.arrayRemove(targetUid))
        batch.update(targetRef, "outgoingRequests", FieldValue.arrayRemove(myUid))
    }
}

private fun declineOrCancelRequest(db: FirebaseFirestore, myUid: String, targetUid: String, isIncoming: Boolean) {
    val myRef = db.collection("users").document(myUid)
    val targetRef = db.collection("users").document(targetUid)
    if (isIncoming) {
        // 我拒絕別人的請求
        myRef.update("incomingRequests", FieldValue.arrayRemove(targetUid))
        targetRef.update("outgoingRequests", FieldValue.arrayRemove(myUid))
    } else {
        // 我收回發給別人的請求
        myRef.update("outgoingRequests", FieldValue.arrayRemove(targetUid))
        targetRef.update("incomingRequests", FieldValue.arrayRemove(myUid))
    }
}

private fun removeFriend(db: FirebaseFirestore, myUid: String, targetUid: String) {
    val myRef = db.collection("users").document(myUid)
    val targetRef = db.collection("users").document(targetUid)
    db.runBatch { batch ->
        batch.update(myRef, "friends", FieldValue.arrayRemove(targetUid))
        batch.update(targetRef, "friends", FieldValue.arrayRemove(myUid))
    }
}
