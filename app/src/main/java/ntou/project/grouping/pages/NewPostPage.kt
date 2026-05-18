package ntou.project.grouping.pages

import android.location.Address
import android.location.Geocoder
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import ntou.project.grouping.models.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostPage(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    // --- 最終選定的地點資訊 ---
    var selectedLatLng by remember { mutableStateOf(LatLng(25.1502, 121.7761)) }
    var selectedPlaceName by remember { mutableStateOf("點擊選取活動位置") }
    var selectedFullAddress by remember { mutableStateOf("") }

    // --- 其他表單狀態 ---
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var isPosting by remember { mutableStateOf(false) }
    var eventTime by remember { mutableStateOf("") }
    var showMapPicker by remember { mutableStateOf(false) }

    if (showMapPicker) {
        LocationPickerDialog(
            initialLatLng = selectedLatLng,
            onDismiss = { showMapPicker = false },
            onLocationConfirm = { latLng, name, addr ->
                selectedLatLng = latLng
                selectedPlaceName = name
                selectedFullAddress = addr
                showMapPicker = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = "建立新活動", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("活動標題") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(16.dp))

        // 地點欄位
        Card(
            onClick = { showMapPicker = true },
            modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = selectedPlaceName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (selectedFullAddress.isNotBlank()) {
                        Text(text = selectedFullAddress, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                    } else {
                        Text(text = "點擊開啟地圖選擇地點", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        val calendar = remember { Calendar.getInstance() }
        val datePickerState = rememberDatePickerState()
        val timePickerState = rememberTimePickerState()
        var showDatePicker by remember { mutableStateOf(false) }
        var showTimePicker by remember { mutableStateOf(false) }
        if (showDatePicker) {
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                TextButton(onClick = { calendar.timeInMillis = datePickerState.selectedDateMillis ?: 0L; showDatePicker = false; showTimePicker = true }) { Text("下一步") }
            }) { DatePicker(state = datePickerState) }
        }
        if (showTimePicker) {
            AlertDialog(onDismissRequest = { showTimePicker = false }, confirmButton = {
                TextButton(onClick = {
                    calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour); calendar.set(Calendar.MINUTE, timePickerState.minute)
                    eventTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(calendar.time); showTimePicker = false
                }) { Text("確定") }
            }, text = { Column { Text("選擇活動時間", fontWeight = FontWeight.Bold); TimePicker(state = timePickerState) } })
        }

        OutlinedTextField(value = eventTime, onValueChange = { }, label = { Text("活動時間") }, modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline), leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }, shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = maxParticipants, onValueChange = { if (it.all { it.isDigit() }) maxParticipants = it }, label = { Text("人數上限 (0 代表不限)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("詳細內容") }, modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "選擇標籤", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = listOf("羽球", "唱歌", "運動", "美食", "桌遊", "旅遊", "學習")
            items(categories) { tag ->
                FilterChip(selected = selectedTags.contains(tag), onClick = { selectedTags = if (selectedTags.contains(tag)) selectedTags - tag else selectedTags + tag }, label = { Text(tag) })
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                val user = auth.currentUser
                if (user == null || title.isBlank() || content.isBlank() || eventTime.isBlank() || selectedFullAddress.isBlank()) {
                    Toast.makeText(context, "請完整填寫資訊", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isPosting = true
                
                // 建立貼文物件時自動抓取使用者頭像
                val post = Post(
                    title = title, 
                    content = content, 
                    authorId = user.uid, 
                    authorName = user.displayName ?: "匿名用戶",
                    authorAvatarUrl = user.photoUrl?.toString() ?: "", // 新增：拉取大頭貼 URL
                    tags = selectedTags.toList(), 
                    latitude = selectedLatLng.latitude, 
                    longitude = selectedLatLng.longitude,
                    locationName = if (selectedFullAddress.contains(selectedPlaceName)) selectedFullAddress else "$selectedPlaceName ($selectedFullAddress)",
                    eventTime = eventTime, 
                    maxParticipants = maxParticipants.toIntOrNull() ?: 0
                )
                
                db.collection("posts").add(post).addOnSuccessListener {
                    isPosting = false; Toast.makeText(context, "發布成功！", Toast.LENGTH_SHORT).show()
                    title = ""; content = ""; eventTime = ""; maxParticipants = ""; selectedTags = emptySet()
                    selectedPlaceName = "點擊選取活動位置"; selectedFullAddress = ""
                }.addOnFailureListener { isPosting = false }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isPosting
        ) {
            if (isPosting) CircularProgressIndicator(color = Color.White) else Text("確認發布", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// --- 大重構：地點選擇專用對話框組件 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    initialLatLng: LatLng,
    onDismiss: () -> Unit,
    onLocationConfirm: (LatLng, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }

    var currentLatLng by remember { mutableStateOf(initialLatLng) }
    var currentName by remember { mutableStateOf("未命名位置") }
    var currentAddress by remember { mutableStateOf("請選擇地點") }
    
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<Address>()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 16f)
    }

    // 核心資訊抓取函數
    fun updateInfo(latLng: LatLng, overrideName: String? = null) {
        currentLatLng = latLng
        scope.launch(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        currentName = overrideName ?: addr.featureName ?: addr.thoroughfare ?: "未知地點"
                        currentAddress = addr.getAddressLine(0) ?: ""
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentName = overrideName ?: "選定地點"
                    currentAddress = "${latLng.latitude}, ${latLng.longitude}"
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { 
                                    searchQuery = it
                                    searchJob?.cancel()
                                    if (it.length < 2) { suggestions = emptyList(); return@OutlinedTextField }
                                    
                                    isSearching = true
                                    searchJob = scope.launch(Dispatchers.IO) {
                                        delay(400)
                                        try {
                                            // --- 加入經緯度偏差，優先搜尋地圖中心附近的店 ---
                                            val mapCenter = cameraPositionState.position.target
                                            @Suppress("DEPRECATION")
                                            val results = geocoder.getFromLocationName(
                                                it, 10,
                                                mapCenter.latitude - 0.2, mapCenter.longitude - 0.2,
                                                mapCenter.latitude + 0.2, mapCenter.longitude + 0.2
                                            )
                                            withContext(Dispatchers.Main) {
                                                suggestions = results ?: emptyList()
                                                isSearching = false
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) { isSearching = false }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("搜尋店名或地址") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; suggestions = emptyList() }) { Icon(Icons.Default.Clear, contentDescription = null) }
                                },
                                singleLine = true,
                                shape = CircleShape
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng -> updateInfo(latLng); suggestions = emptyList() },
                    onPOIClick = { poi -> updateInfo(poi.latLng, poi.name); suggestions = emptyList() },
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    Marker(state = MarkerState(position = currentLatLng))
                }

                // --- 全螢幕搜尋建議 ---
                AnimatedVisibility(
                    visible = suggestions.isNotEmpty() || (searchQuery.length >= 2 && !isSearching && suggestions.isEmpty()),
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.White.copy(alpha = 0.98f)) {
                        if (suggestions.isEmpty() && !isSearching) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                                    Text("找不到相關地點", color = Color.Gray)
                                    Text("請輸入更詳細的名稱 (例如: 基隆 麥當勞)", fontSize = 12.sp, color = Color.LightGray)
                                }
                            }
                        } else {
                            LazyColumn {
                                items(suggestions) { addr ->
                                    val name = addr.featureName ?: addr.getAddressLine(0)
                                    ListItem(
                                        headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text(addr.getAddressLine(0), fontSize = 12.sp, color = Color.Gray, maxLines = 2) },
                                        leadingContent = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        modifier = Modifier.clickable {
                                            val target = LatLng(addr.latitude, addr.longitude)
                                            updateInfo(target, addr.featureName)
                                            suggestions = emptyList(); searchQuery = ""
                                            scope.launch { cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(target, 17f)) }
                                        }
                                    )
                                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                                }
                            }
                        }
                    }
                }

                // --- 底部確認面板 ---
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "選取的地點", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = currentName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(text = currentAddress, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 2, lineHeight = 18.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onLocationConfirm(currentLatLng, currentName, currentAddress) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("就選這裡", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
