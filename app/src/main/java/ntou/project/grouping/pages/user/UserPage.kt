package ntou.project.grouping.pages.user

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import ntou.project.grouping.models.User

@Composable
fun UserPage(
    paddingValues: PaddingValues,
    onNavigateToFriends: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    var userData by remember { mutableStateOf<User?>(null) }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var postCount by remember { mutableStateOf(0) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { document, _ ->
                    val user = document?.toObject(User::class.java)
                    userData = user
                    displayName = user?.displayName ?: ""
                    bio = user?.bio ?: ""
                    isLoading = false
                }
            db.collection("posts").whereEqualTo("authorId", currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    postCount = snapshot?.size() ?: 0
                }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (currentUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("請先登入")
        }
        return
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(top = 32.dp, bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 大頭貼
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (!userData?.photoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = userData?.photoUrl,
                            contentDescription = "頭像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(52.dp), tint = onSurface.copy(alpha = 0.6f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = userData?.displayName ?: "未設定名稱",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = onSurface
                )

                if (!userData?.bio.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = userData?.bio ?: "",
                        fontSize = 14.sp,
                        color = onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 統計列
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(count = userData?.friends?.size ?: 0, label = "好友", color = onSurface)
                    VerticalDivider(modifier = Modifier.height(24.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    StatItem(count = postCount, label = "發起活動", color = onSurface)
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(modifier = Modifier.height(24.dp))

        // ── 內容區 ──────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            if (isEditing) {
                // 編輯模式
                Text("編輯個人資料", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("暱稱") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("自我介紹") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("介紹一下你自己吧...") }
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            isEditing = false
                            displayName = userData?.displayName ?: ""
                            bio = userData?.bio ?: ""
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("取消") }

                    Button(
                        onClick = {
                            isSaving = true
                            db.collection("users").document(currentUser.uid)
                                .update(mapOf("displayName" to displayName, "bio" to bio))
                                .addOnSuccessListener {
                                    isSaving = false
                                    isEditing = false
                                    userData = userData?.copy(displayName = displayName, bio = bio)
                                    Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    isSaving = false
                                    Toast.makeText(context, "更新失敗", Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("儲存")
                    }
                }
            } else {
                // 選單
                Text("選單", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuButton(text = "編輯個人資料", icon = Icons.Default.Edit, onClick = { isEditing = true })
                    MenuButton(text = "好友", icon = Icons.Default.People, onClick = onNavigateToFriends)
                    MenuButton(text = "我的行程", icon = Icons.Default.DateRange, onClick = onNavigateToSchedule)
                    // 電子郵件（純顯示，不可點擊）
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("電子郵件", fontSize = 11.sp, color = Color.Gray)
                                Text(currentUser.email ?: "無", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    MenuButton(
                        text = "登出帳號",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        onClick = { auth.signOut(); onLogout() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = label, fontSize = 12.sp, color = color.copy(alpha = 0.5f))
    }
}


@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (contentColor == MaterialTheme.colorScheme.error) contentColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}
