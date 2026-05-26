package ntou.project.grouping.pages

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun NewPostPage(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var selectedLatLng by remember { mutableStateOf(LatLng(25.1502, 121.7761)) }
    var selectedPlaceName by remember { mutableStateOf("點擊選取活動位置") }
    var selectedFullAddress by remember { mutableStateOf("") }

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
                    Text(text = selectedFullAddress.ifBlank { "點擊開啟地圖選擇地點" }, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        val calendar = remember { Calendar.getInstance() }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        val timePickerState = rememberTimePickerState()
        var showDatePicker by remember { mutableStateOf(false) }
        var showTimePicker by remember { mutableStateOf(false) }

        if (showDatePicker) {
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                TextButton(onClick = { 
                    calendar.timeInMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    showDatePicker = false
                    showTimePicker = true 
                }) { Text("下一步") }
            }) { DatePicker(state = datePickerState) }
        }
        if (showTimePicker) {
            AlertDialog(onDismissRequest = { showTimePicker = false }, confirmButton = {
                TextButton(onClick = {
                    calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    calendar.set(Calendar.MINUTE, timePickerState.minute)
                    eventTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(calendar.time)
                    showTimePicker = false
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
                val post = Post(
                    title = title, content = content, authorId = user.uid, authorName = user.displayName ?: "匿名用戶",
                    authorAvatarUrl = user.photoUrl?.toString() ?: "",
                    tags = selectedTags.toList(), latitude = selectedLatLng.latitude, longitude = selectedLatLng.longitude,
                    locationName = if (selectedFullAddress.contains(selectedPlaceName)) selectedFullAddress else "$selectedPlaceName ($selectedFullAddress)",
                    eventTime = eventTime, maxParticipants = maxParticipants.toIntOrNull() ?: 0
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
    var sessionToken = remember { AutocompleteSessionToken.newInstance() }

    var currentLatLng by remember { mutableStateOf(initialLatLng) }
    var currentName by remember { mutableStateOf("未命名位置") }
    var currentAddress by remember { mutableStateOf("請點擊地圖選取") }
    var hasLocationPicked by remember { mutableStateOf(false) } // 控制紅點顯示
    
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<AutocompletePrediction>()) }
    var isSearching by remember { mutableStateOf(false) }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 16f)
    }

    // 啟動時：自動抓取目前位置並移鏡頭，但不自動下標籤 (移除紅點)
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
        hasLocationPicked = true // 標記為已選取
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
