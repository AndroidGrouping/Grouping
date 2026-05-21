package ntou.project.grouping.pages.auth

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

@Composable
fun LoginPage(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    var isLoading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        // 準備使用者資料
                        val userData = hashMapOf(
                            "uid" to firebaseUser.uid,
                            "displayName" to (firebaseUser.displayName ?: ""),
                            "email" to (firebaseUser.email ?: ""),
                            "photoUrl" to (firebaseUser.photoUrl?.toString() ?: "")
                        )

                        // 寫入 Firestore users 集合，使用 uid 作為 Document ID
                        // 使用 merge 避免覆蓋掉現有的好友列表等其他資料
                        db.collection("users").document(firebaseUser.uid)
                            .set(userData, SetOptions.merge())
                            .addOnCompleteListener { 
                                isLoading = false
                                onLoginSuccess()
                            }
                    } else {
                        isLoading = false
                        onLoginSuccess()
                    }
                } else {
                    isLoading = false
                    Toast.makeText(context, "Firebase 登入失敗: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            isLoading = false
            Toast.makeText(context, "Google 登入失敗 (代碼 ${e.statusCode})", Toast.LENGTH_LONG).show()
            Log.e("LoginPage", "Google sign in failed: ${e.statusCode}")
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "發生錯誤: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "歡迎來到 Grouping",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isLoading = true
                    // 直接使用從 google-services.json 提取的 Web Client ID
                    val webClientId = "672360197151-cr3a2k3velqujvush6rlak7jkfcl38ot.apps.googleusercontent.com"
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(context, gso)
                    launcher.launch(client.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("使用 Google 帳號登入")
            }
        }
    }
}
