package ntou.project.grouping.pages.user

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import ntou.project.grouping.models.User

@Composable
fun UserPage(
    paddingValues: PaddingValues,
    onNavigateToFriends: () -> Unit
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

    // 從 Firestore 讀取使用者資料
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    userData = user
                    displayName = user?.displayName ?: ""
                    bio = user?.bio ?: ""
                    isLoading = false
                }
                .addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "讀取資料失敗", Toast.LENGTH_SHORT).show()
                }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (currentUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("請先登入")
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 大頭貼 (僅限檢視)
            Surface(
                modifier = Modifier
                    .size(120.dp)
                    .border(4.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (!userData?.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = userData?.photoUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 1. 暱稱 (可編輯)
            ProfileInfoSection(
                label = "暱稱",
                value = if (isEditing) displayName else userData?.displayName ?: "未設定",
                isEditing = isEditing,
                onValueChange = { displayName = it }
            )

            // 2. 自我介紹 (可編輯)
            ProfileInfoSection(
                label = "自我介紹",
                value = if (isEditing) bio else if (userData?.bio.isNullOrEmpty()) "尚未填寫自我介紹" else userData?.bio!!,
                isEditing = isEditing,
                isMultiLine = true,
                onValueChange = { bio = it }
            )

            // 3. 電子郵件 (僅限檢視)
            ProfileInfoSection(label = "電子郵件", value = currentUser.email ?: "無", isReadOnly = true)

            // 4. 使用者 ID (僅限檢視)
            ProfileInfoSection(label = "使用者 ID", value = currentUser.uid, isReadOnly = true)

            Spacer(modifier = Modifier.height(32.dp))

            if (!isEditing) {
                // 按鈕區塊
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuButton(text = "編輯個人資料", icon = Icons.Default.Edit) {
                        isEditing = true
                    }
                    MenuButton(text = "好友", icon = Icons.Default.Person) {
                        onNavigateToFriends()
                    }
                    MenuButton(text = "我的行程", icon = Icons.Default.DateRange) {
                        Toast.makeText(context, "功能尚未實作", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // 儲存與取消按鈕
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            isEditing = false
                            displayName = userData?.displayName ?: ""
                            bio = userData?.bio ?: ""
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            val updates = mapOf(
                                "displayName" to displayName,
                                "bio" to bio
                            )
                            db.collection("users").document(currentUser.uid)
                                .update(updates)
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
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("儲存")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ProfileInfoSection(
    label: String,
    value: String,
    isEditing: Boolean = false,
    isReadOnly: Boolean = false,
    isMultiLine: Boolean = false,
    onValueChange: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        if (isEditing && !isReadOnly) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .then(if (isMultiLine) Modifier.height(100.dp) else Modifier),
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isReadOnly) FontWeight.Normal else FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = Color.LightGray)
    }
}

@Composable
fun MenuButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
