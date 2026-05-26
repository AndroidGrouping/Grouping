package ntou.project.grouping.pages.user

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    var friendList by remember { mutableStateOf<List<User>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var foundUser by remember { mutableStateOf<User?>(null) }
    var isLoadingFriends by remember { mutableStateOf(true) }

    // Fetch current user's friends details
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    @Suppress("UNCHECKED_CAST")
                    val friendUids = snapshot?.get("friends") as? List<String> ?: emptyList()
                    if (friendUids.isEmpty()) {
                        friendList = emptyList()
                        isLoadingFriends = false
                    } else {
                        // Fetch user details for each friend UID
                        db.collection("users").whereIn("uid", friendUids)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                friendList = querySnapshot.toObjects(User::class.java)
                                isLoadingFriends = false
                            }
                            .addOnFailureListener {
                                isLoadingFriends = false
                            }
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Text(
            text = "好友列表",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Add Friend Section
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("輸入電子郵件新增好友") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    if (searchQuery.isNotBlank()) {
                        isSearching = true
                        db.collection("users")
                            .whereEqualTo("email", searchQuery.trim())
                            .get()
                            .addOnSuccessListener { results ->
                                isSearching = false
                                if (!results.isEmpty) {
                                    val user = results.documents[0].toObject(User::class.java)
                                    if (user?.uid == currentUser?.uid) {
                                        Toast.makeText(context, "不能新增自己為好友", Toast.LENGTH_SHORT).show()
                                        foundUser = null
                                    } else {
                                        foundUser = user
                                    }
                                } else {
                                    foundUser = null
                                    Toast.makeText(context, "找不到該使用者", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                isSearching = false
                                Toast.makeText(context, "搜尋失敗", Toast.LENGTH_SHORT).show()
                            }
                    }
                }) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp)
        )

        // Show search result
        foundUser?.let { user ->
            val isAlreadyFriend = friendList.any { it.uid == user.uid }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                ListItem(
                    headlineContent = { Text(user.displayName, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(user.email) },
                    leadingContent = {
                        UserAvatar(user.photoUrl, user.displayName)
                    },
                    trailingContent = {
                        if (isAlreadyFriend) {
                            Text("已是好友", color = Color.Gray, fontSize = 14.sp)
                        } else {
                            Button(onClick = {
                                if (currentUser != null) {
                                    // Add to current user's friend list
                                    db.collection("users").document(currentUser.uid)
                                        .update("friends", FieldValue.arrayUnion(user.uid))
                                    
                                    // Make it mutual
                                    db.collection("users").document(user.uid)
                                        .update("friends", FieldValue.arrayUnion(currentUser.uid))
                                    
                                    Toast.makeText(context, "已新增 ${user.displayName} 為好友！", Toast.LENGTH_SHORT).show()
                                    foundUser = null
                                    searchQuery = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("新增")
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Friends List Display
        if (isLoadingFriends) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (friendList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("尚無好友，快去搜尋 Email 新增吧！", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(friendList) { friend ->
                    FriendItem(friend) {
                        // 點擊好友的功能 (例如開啟聊天室)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(url: String, name: String, size: Int = 40) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun FriendItem(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text(user.displayName, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(user.email, fontSize = 12.sp, color = Color.Gray) },
            leadingContent = {
                UserAvatar(user.photoUrl, user.displayName, size = 48)
            }
        )
    }
}
