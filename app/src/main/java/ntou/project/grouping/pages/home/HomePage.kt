package ntou.project.grouping.pages.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ntou.project.grouping.models.Post

@SuppressLint("MissingPermission")
@Composable
fun HomePage(
    paddingValues: PaddingValues,
    targetPost: Post? = null,
    onTargetHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    var posts by remember { mutableStateOf(listOf<Post>()) }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // 鏡頭狀態
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(25.1502, 121.7761), 15f)
    }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // --- 1. 處理跳轉邏輯 (僅在有 targetPost 時觸發) ---
    LaunchedEffect(targetPost) {
        if (targetPost != null && targetPost.latitude != 0.0) {
            // 跳轉前，如果目前還在預設的海大，先抓取位置瞬移一下
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    if (cameraPositionState.position.target.latitude == 25.1502) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                    }
                }
                
                // 平滑移動到目標地點
                scope.launch {
                    delay(100)
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(targetPost.latitude, targetPost.longitude), 17f)
                    )
                    onTargetHandled() // 執行後 targetPost 變為 null，但此 block 不會再執行 else
                }
            }
        }
    }

    // --- 2. 處理初始定位 (僅執行一次) ---
    var isFirstLocationSet by remember { mutableStateOf(false) }
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted && !isFirstLocationSet && targetPost == null) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                    isFirstLocationSet = true
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Firestore 監聽
    DisposableEffect(locationPermissionGranted) {
        var firestoreListener: ListenerRegistration? = null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {}
            override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
            override fun onProviderEnabled(p0: String) {}
            override fun onProviderDisabled(p0: String) {}
        }

        if (locationPermissionGranted) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 20f, locationListener)
            } catch (e: SecurityException) { e.printStackTrace() }

            firestoreListener = db.collection("posts").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    posts = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Post::class.java)?.copy(id = doc.id)
                    }
                }
            }
        }

        onDispose {
            locationManager.removeUpdates(locationListener)
            firestoreListener?.remove()
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
            uiSettings = MapUiSettings(myLocationButtonEnabled = locationPermissionGranted)
        ) {
            posts.forEach { post ->
                if (post.latitude != 0.0 && post.longitude != 0.0) {
                    key(post.id) {
                        MarkerComposable(
                            state = MarkerState(position = LatLng(post.latitude, post.longitude)),
                            title = post.title,
                            anchor = Offset(0.5f, 1f)
                        ) {
                            PostMarker(post)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostMarker(post: Post) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 4.dp
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Canvas(modifier = Modifier.size(16.dp, 10.dp).offset(y = (-2).dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(path, color = Color.White)
            drawPath(path, color = Color.LightGray, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        }
    }
}
