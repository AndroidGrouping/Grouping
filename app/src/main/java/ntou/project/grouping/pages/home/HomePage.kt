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
import com.google.maps.android.compose.*
import ntou.project.grouping.models.Post

@SuppressLint("MissingPermission")
@Composable
fun HomePage(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var posts by remember { mutableStateOf(listOf<Post>()) }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val defaultLocation = LatLng(25.1502, 121.7761)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 15f)
    }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
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

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
                }
            }
        }
    }

    DisposableEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {}
                @Deprecated("Deprecated in Java", ReplaceWith(""))
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 20f, locationListener)
            } catch (e: SecurityException) { e.printStackTrace() }

            onDispose { locationManager.removeUpdates(locationListener) }
        } else { onDispose {} }
    }

    LaunchedEffect(Unit) {
        db.collection("posts").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                posts = snapshot.toObjects(Post::class.java)
            }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.wrapContentSize()
    ) {
        // 使用 Surface 替代複雜的 Box 修飾符，能更穩定地置中
        Surface(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 4.dp
        ) {
            SubcomposeAsyncImage(
                model = post.authorAvatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${post.authorName}&background=random" },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = post.authorName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = post.authorName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            )
        }
        
        Canvas(
            modifier = Modifier.size(16.dp, 10.dp).offset(y = (-2).dp)
        ) {
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
