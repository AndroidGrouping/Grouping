package ntou.project.grouping.pages.search

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import ntou.project.grouping.components.DisbandConfirmDialog
import ntou.project.grouping.components.ExpandablePostCard
import ntou.project.grouping.components.PostCardStyle
import ntou.project.grouping.components.performLeaveTransaction
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
    var expandedPostId by remember { mutableStateOf<String?>(null) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var showDisbandDialog by remember { mutableStateOf(false) }
    var friendIds by remember { mutableStateOf(setOf<String>()) }

    val categories = listOf("全部", "好友", "羽球", "唱歌", "運動", "美食", "桌遊", "旅遊", "學習")

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            @Suppress("UNCHECKED_CAST")
            val friends = snapshot?.get("friends") as? List<String> ?: emptyList()
            friendIds = friends.toSet()
        }
    }

    LaunchedEffect(Unit) {
        db.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    allPosts = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Post::class.java)?.copy(id = doc.id)
                    }
                }
                isLoading = false
            }
    }

    LaunchedEffect(searchQuery, selectedCategory, allPosts, friendIds) {
        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
        val now = java.util.Date()
        filteredPosts = allPosts.filter { post ->
            val matchesQuery = post.title.contains(searchQuery, ignoreCase = true) ||
                    post.content.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "全部" -> true
                "好友" -> post.authorId in friendIds
                else -> post.tags.contains(selectedCategory)
            }
            val notStarted = if (post.eventTime.isNotBlank()) {
                try { sdf.parse(post.eventTime)?.after(now) ?: true } catch (e: Exception) { true }
            } else { true }
            matchesQuery && matchesCategory && notStarted
        }
    }

    if (showDisbandDialog && selectedPost != null) {
        DisbandConfirmDialog(
            post = selectedPost!!,
            db = db,
            currentUid = currentUser?.uid,
            onDismiss = { showDisbandDialog = false },
            onComplete = { showDisbandDialog = false; selectedPost = null }
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
                items(filteredPosts, key = { it.id }) { post ->
                    ExpandablePostCard(
                        post = post,
                        isExpanded = expandedPostId == post.id,
                        style = PostCardStyle.SEARCH,
                        currentUid = currentUser?.uid,
                        onExpandClick = { expandedPostId = if (expandedPostId == post.id) null else post.id },
                        onLocationClick = { onNavigateToMap(post) },
                        onPrimaryAction = {
                            if (currentUser == null) return@ExpandablePostCard
                            val isJoined = post.participants.contains(currentUser.uid)
                            val postRef = db.collection("posts").document(post.id)
                            if (isJoined) {
                                if (post.participants.size == 1) {
                                    selectedPost = post
                                    showDisbandDialog = true
                                } else {
                                    performLeaveTransaction(db, postRef, currentUser.uid) {
                                        Toast.makeText(context, "已取消參加", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                postRef.update("participants", FieldValue.arrayUnion(currentUser.uid))
                                    .addOnSuccessListener { Toast.makeText(context, "報名成功！", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    )
                }
            }
        }
    }
}
