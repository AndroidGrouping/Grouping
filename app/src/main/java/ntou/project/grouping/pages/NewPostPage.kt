package ntou.project.grouping.pages

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import ntou.project.grouping.models.Post
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostPage(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context) 
    }

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") } // 人數上限狀態
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var isPosting by remember { mutableStateOf(false) }
    
    // --- 時間相關狀態 ---
    var eventTime by remember { mutableStateOf("") } 
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }
    
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )

    // --- 位置相關狀態 ---
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var locationName by remember { mutableStateOf("正在獲取位置...") }

    val allTags = listOf("羽球", "唱歌", "運動", "美食", "桌遊", "旅遊", "學習")

    // 獲取位置函數
    fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    locationName = "已標記當前位置"
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) fetchLocation()
        else locationName = "未授權定位權限"
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // --- 日期選擇器對話框 ---
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        calendar.timeInMillis = it
                        showDatePicker = false
                        showTimePicker = true // 選完日期接著選時間
                    }
                }) { Text("下一步") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- 時間選擇器對話框 ---
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    calendar.set(Calendar.MINUTE, timePickerState.minute)
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    eventTime = sdf.format(calendar.time)
                    showTimePicker = false
                }) { Text("確定") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("選擇活動時間", modifier = Modifier.padding(bottom = 16.dp))
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "建立新貼文", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("貼文標題") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 活動時間選擇欄位 ---
        OutlinedTextField(
            value = eventTime,
            onValueChange = { },
            label = { Text("活動時間") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
            enabled = false, // 禁止手動打字
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            placeholder = { Text("點擊選擇日期與時間") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 人數上限輸入 ---
        OutlinedTextField(
            value = maxParticipants,
            onValueChange = { if (it.all { char -> char.isDigit() }) maxParticipants = it },
            label = { Text("人數上限 (0 或不填代表不限)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("貼文內容") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 10
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = locationName, fontSize = 14.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "選擇標籤 (可多選)", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allTags) { tag ->
                val isSelected = selectedTags.contains(tag)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                    },
                    label = { Text(tag) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val user = auth.currentUser
                if (user == null || title.isBlank() || content.isBlank() || eventTime.isBlank()) {
                    Toast.makeText(context, "請完整填寫所有資訊", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isPosting = true
                val post = Post(
                    title = title,
                    content = content,
                    authorId = user.uid,
                    authorName = user.displayName ?: "匿名用戶",
                    tags = selectedTags.toList(),
                    latitude = latitude,
                    longitude = longitude,
                    locationName = locationName,
                    eventTime = eventTime,
                    maxParticipants = maxParticipants.toIntOrNull() ?: 0
                )

                db.collection("posts").add(post)
                    .addOnSuccessListener {
                        isPosting = false
                        Toast.makeText(context, "發布成功！", Toast.LENGTH_SHORT).show()
                        title = ""
                        content = ""
                        eventTime = ""
                        maxParticipants = ""
                        selectedTags = emptySet()
                    }
                    .addOnFailureListener { e ->
                        isPosting = false
                        Toast.makeText(context, "發布失敗：${e.message}", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isPosting
        ) {
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("發布貼文")
            }
        }
    }
}
