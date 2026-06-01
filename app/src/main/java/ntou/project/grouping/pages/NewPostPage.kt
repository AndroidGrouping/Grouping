package ntou.project.grouping.pages

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
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
fun NewPostPage(
    paddingValues: PaddingValues,
    onPostCreated: (Post) -> Unit = {}
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var selectedLatLng by remember { mutableStateOf(LatLng(25.1502, 121.7761)) }
    var selectedPlaceName by remember { mutableStateOf("點擊選取活動位置") }
    var selectedFullAddress by remember { mutableStateOf("") }

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }
    var isUnlimited by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var isPosting by remember { mutableStateOf(false) }

    val titleLimit = 30
    val contentLimit = 500

    // 驗證狀態
    var titleError by remember { mutableStateOf(false) }
    var contentError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    var timeError by remember { mutableStateOf(false) }
    var endTimeError by remember { mutableStateOf(false) }

    // 時間狀態
    var eventTime by remember { mutableStateOf("") }
    var eventEndTime by remember { mutableStateOf("") }

    var showMapPicker by remember { mutableStateOf(false) }

    val isFormValid by remember {
        derivedStateOf {
            title.isNotBlank() && title.length <= titleLimit &&
                    content.isNotBlank() && content.length <= contentLimit &&
                    selectedFullAddress.isNotBlank() &&
                    eventTime.isNotBlank() &&
                    eventEndTime.isNotBlank()
        }
    }

    if (showMapPicker) {
        LocationPickerDialog(
            initialLatLng = selectedLatLng,
            onDismiss = { showMapPicker = false },
            onLocationConfirm = { latLng, name, addr ->
                selectedLatLng = latLng
                selectedPlaceName = name
                selectedFullAddress = addr
                locationError = false
                showMapPicker = false
            }
        )
    }

    // --- 連續時間選擇器邏輯 ---
    val startCalendar = remember { Calendar.getInstance() }
    val endCalendar = remember { Calendar.getInstance() }

    val selectableDates = remember {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return utcTimeMillis >= calendar.timeInMillis
            }
            override fun isSelectableYear(year: Int): Boolean {
                return year >= Calendar.getInstance().get(Calendar.YEAR)
            }
        }
    }

    val startDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
        selectableDates = selectableDates
    )
    val endDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
        selectableDates = selectableDates
    )
    val startTimePickerState = rememberTimePickerState()
    val endTimePickerState = rememberTimePickerState()

    var pickerStep by remember { mutableStateOf(0) } // 1:開始日期, 2:開始時間, 3:結束日期, 4:結束時間

    if (pickerStep == 1) {
        DatePickerDialog(
            onDismissRequest = { pickerStep = 0 },
            confirmButton = {
                TextButton(onClick = {
                    startCalendar.timeInMillis = startDatePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    pickerStep = 2
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { pickerStep = 0 }) { Text("取消") }
            }
        ) { DatePicker(state = startDatePickerState, title = { Text("選擇開始日期", modifier = Modifier.padding(16.dp)) }) }
    } else if (pickerStep == 2) {
        AlertDialog(
            onDismissRequest = { pickerStep = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val tempStartCalendar = startCalendar.clone() as Calendar
                    tempStartCalendar.set(Calendar.HOUR_OF_DAY, startTimePickerState.hour)
                    tempStartCalendar.set(Calendar.MINUTE, startTimePickerState.minute)
                    tempStartCalendar.set(Calendar.SECOND, 0)
                    tempStartCalendar.set(Calendar.MILLISECOND, 0)

                    if (tempStartCalendar.timeInMillis < System.currentTimeMillis()) {
                        Toast.makeText(context, "開始時間不能早於現在", Toast.LENGTH_SHORT).show()
                    } else {
                        startCalendar.timeInMillis = tempStartCalendar.timeInMillis
                        eventTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(startCalendar.time)
                        timeError = false

                        // 預設結束日期為開始日期，結束時間預設為開始時間
                        endDatePickerState.selectedDateMillis = startCalendar.timeInMillis
                        pickerStep = 3
                    }
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { pickerStep = 1 }) { Text("上一步") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("選擇開始時間", fontWeight = FontWeight.Bold)
                    TimePicker(state = startTimePickerState)
                }
            }
        )
    } else if (pickerStep == 3) {
        DatePickerDialog(
            onDismissRequest = { pickerStep = 0 },
            confirmButton = {
                TextButton(onClick = {
                    endCalendar.timeInMillis = endDatePickerState.selectedDateMillis ?: startCalendar.timeInMillis
                    pickerStep = 4
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { pickerStep = 2 }) { Text("上一步") }
            }
        ) { DatePicker(state = endDatePickerState, title = { Text("選擇結束日期", modifier = Modifier.padding(16.dp)) }) }
    } else if (pickerStep == 4) {
        AlertDialog(
            onDismissRequest = { pickerStep = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val tempEndCalendar = endCalendar.clone() as Calendar
                    tempEndCalendar.set(Calendar.HOUR_OF_DAY, endTimePickerState.hour)
                    tempEndCalendar.set(Calendar.MINUTE, endTimePickerState.minute)

                    // 驗證：結束時間不得早於或等於開始時間
                    if (tempEndCalendar.timeInMillis <= startCalendar.timeInMillis) {
                        Toast.makeText(context, "結束時間必須晚於開始時間", Toast.LENGTH_SHORT).show()
                    } else {
                        endCalendar.timeInMillis = tempEndCalendar.timeInMillis
                        eventEndTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(endCalendar.time)
                        endTimeError = false
                        pickerStep = 0
                    }
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerStep = 3 }) { Text("上一步") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("選擇結束時間", fontWeight = FontWeight.Bold)
                    TimePicker(state = endTimePickerState)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = "建立新活動", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = {
                if (it.length <= titleLimit) {
                    title = it
                    if (it.isNotBlank()) titleError = false
                }
            },
            label = { Text("活動標題") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            isError = titleError,
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (titleError) {
                        Text("活動標題不能為空", color = MaterialTheme.colorScheme.error)
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text("${title.length}/$titleLimit", style = MaterialTheme.typography.bodySmall)
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            onClick = { showMapPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .let { if (locationError) it.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp)) else it },
            shape = RoundedCornerShape(12.dp),
            border = if (locationError) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (locationError) MaterialTheme.colorScheme.error else Color.Red, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = selectedPlaceName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = if (locationError) MaterialTheme.colorScheme.error else Color.Unspecified)
                    Text(text = selectedFullAddress.ifBlank { "點擊開啟地圖選擇地點" }, fontSize = 12.sp, color = if (locationError) MaterialTheme.colorScheme.error else Color.Gray, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }
        }
        if (locationError) {
            Text(
                text = "請選擇活動位置",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 開始時間
        OutlinedTextField(
            value = eventTime,
            onValueChange = { },
            label = { Text("開始時間") },
            modifier = Modifier.fillMaxWidth().clickable { pickerStep = 1 },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = if (timeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (timeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                disabledLabelColor = if (timeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = if (timeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
            shape = RoundedCornerShape(12.dp),
            isError = timeError,
            supportingText = {
                if (timeError) {
                    Text("請選擇有效的開始時間", color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 結束時間
        OutlinedTextField(
            value = eventEndTime,
            onValueChange = { },
            label = { Text("結束時間") },
            modifier = Modifier.fillMaxWidth().clickable { pickerStep = 3 },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = if (endTimeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (endTimeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                disabledLabelColor = if (endTimeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            leadingIcon = { Icon(Icons.Default.EventAvailable, contentDescription = null, tint = if (endTimeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
            shape = RoundedCornerShape(12.dp),
            isError = endTimeError,
            supportingText = {
                if (endTimeError) {
                    Text("結束時間需晚於開始時間", color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "不限人數", fontWeight = FontWeight.Bold)
            Switch(
                checked = isUnlimited,
                onCheckedChange = {
                    isUnlimited = it
                    if (it) maxParticipants = "0" else if (maxParticipants == "0") maxParticipants = ""
                }
            )
        }

        AnimatedVisibility(visible = !isUnlimited) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxParticipants,
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxParticipants = it },
                    label = { Text("人數上限") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = content,
            onValueChange = {
                if (it.length <= contentLimit) {
                    content = it
                    if (it.isNotBlank()) contentError = false
                }
            },
            label = { Text("詳細內容") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(12.dp),
            isError = contentError,
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (contentError) {
                        Text("活動內容不能為空", color = MaterialTheme.colorScheme.error)
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text("${content.length}/$contentLimit", style = MaterialTheme.typography.bodySmall)
                }
            }
        )
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
                
                // 這裡其實因為按鈕 enabled 邏輯，大部分錯誤狀況應該不會觸發，但保留作為保險
                titleError = title.isBlank()
                contentError = content.isBlank()
                locationError = selectedFullAddress.isBlank()
                timeError = eventTime.isBlank()
                endTimeError = eventEndTime.isBlank()

                if (user == null || !isFormValid) {
                    if (user == null) Toast.makeText(context, "請先登入", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isPosting = true
                val post = Post(
                    title = title, content = content, authorId = user.uid, authorName = user.displayName ?: "匿名用戶",
                    authorAvatarUrl = user.photoUrl?.toString() ?: "",
                    tags = selectedTags.toList(), latitude = selectedLatLng.latitude, longitude = selectedLatLng.longitude,
                    locationName = if (selectedFullAddress.contains(selectedPlaceName)) selectedFullAddress else "$selectedPlaceName ($selectedFullAddress)",
                    eventTime = eventTime, eventEndTime = eventEndTime, 
                    maxParticipants = if (isUnlimited) 0 else (maxParticipants.toIntOrNull() ?: 0),
                    participants = listOf(user.uid)
                )
                db.collection("posts").add(post).addOnSuccessListener { docRef ->
                    isPosting = false; Toast.makeText(context, "發布成功！", Toast.LENGTH_SHORT).show()
                    val finalPost = post.copy(id = docRef.id)
                    onPostCreated(finalPost)

                    title = ""; content = ""; eventTime = ""; eventEndTime = ""; maxParticipants = ""; isUnlimited = false; selectedTags = emptySet()
                    selectedPlaceName = "點擊選取活動位置"; selectedFullAddress = ""
                }.addOnFailureListener { isPosting = false }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isPosting && isFormValid
        ) {
            if (isPosting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Text("確認發布", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

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
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    if (!Places.isInitialized()) {
        Places.initialize(context, "AIzaSyBaQxDTVxo9IUh4UnzDHn-262sSY_OD_bA")
    }
    val placesClient = remember { Places.createClient(context) }
    var sessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }

    var currentLatLng by remember { mutableStateOf(initialLatLng) }
    var currentName by remember { mutableStateOf("未命名位置") }
    var currentAddress by remember { mutableStateOf("請點擊地圖選取") }
    var hasLocationPicked by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<AutocompletePrediction>()) }
    var isSearching by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 16f)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userLatLng, 16f))
                }
            }
        }
    }

    fun updateInfo(latLng: LatLng, overrideName: String? = null) {
        currentLatLng = latLng
        hasLocationPicked = true
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

    fun selectPrediction(prediction: AutocompletePrediction) {
        val placeFields = listOf(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS)
        val request = FetchPlaceRequest.newInstance(prediction.placeId, placeFields)
        placesClient.fetchPlace(request).addOnSuccessListener { response ->
            val place = response.place
            val target = place.latLng ?: return@addOnSuccessListener
            updateInfo(target, place.name)
            searchQuery = ""
            suggestions = emptyList()
            sessionToken = AutocompleteSessionToken.newInstance()
            scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 17f)) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    if (it.length < 2) { suggestions = emptyList(); return@OutlinedTextField }
                                    isSearching = true
                                    val mapCenter = cameraPositionState.position.target
                                    val bias = RectangularBounds.newInstance(
                                        LatLng(mapCenter.latitude - 0.2, mapCenter.longitude - 0.2),
                                        LatLng(mapCenter.latitude + 0.2, mapCenter.longitude + 0.2)
                                    )
                                    val request = FindAutocompletePredictionsRequest.builder().setQuery(it).setSessionToken(sessionToken).setLocationBias(bias).build()
                                    placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
                                        suggestions = response.autocompletePredictions
                                        isSearching = false
                                    }.addOnFailureListener { isSearching = false }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("搜尋店名或地址") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; suggestions = emptyList() }) { Icon(Icons.Default.Clear, null) }
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
                    onPOIClick = { poi ->
                        val request = FetchPlaceRequest.newInstance(poi.placeId, listOf(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS))
                        placesClient.fetchPlace(request).addOnSuccessListener { response ->
                            updateInfo(response.place.latLng ?: poi.latLng, response.place.name ?: poi.name)
                        }
                    },
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    if (hasLocationPicked) {
                        Marker(state = MarkerState(position = currentLatLng))
                    }
                }

                AnimatedVisibility(visible = suggestions.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.White.copy(alpha = 0.98f)) {
                        LazyColumn {
                            items(suggestions) { prediction ->
                                ListItem(
                                    headlineContent = { Text(prediction.getPrimaryText(null).toString(), fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text(prediction.getSecondaryText(null).toString(), fontSize = 12.sp, color = Color.Gray) },
                                    leadingContent = { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { selectPrediction(prediction) }
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "選取的地點", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = currentName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(text = currentAddress, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 2)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onLocationConfirm(currentLatLng, currentName, currentAddress) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = hasLocationPicked
                        ) {
                            Text("就選這裡", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
