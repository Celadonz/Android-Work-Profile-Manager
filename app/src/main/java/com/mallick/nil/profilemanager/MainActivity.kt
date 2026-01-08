package com.mallick.nil.profilemanager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mallick.nil.profilemanager.ui.theme.ProfileManagerTheme
import com.mallick.nil.profilemanager.ui.theme.Purple40
import com.mallick.nil.profilemanager.ui.theme.Pink40

class MainActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private var isProfileOwner by mutableStateOf(false)

    private val provisionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Provisioning started", Toast.LENGTH_SHORT).show()
            updateProfileStatus()
        } else {
            Toast.makeText(this, "Provisioning failed or cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        updateProfileStatus()

        enableEdgeToEdge()
        setContent {
            ProfileManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        isProfileOwner = isProfileOwner,
                        onProvisionClick = { provisionManagedProfile() },
                        onInstallAppClick = { packageName -> installAppInProfile(packageName) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateProfileStatus()
    }

    private fun updateProfileStatus() {
        isProfileOwner = isProfileOwnerOrManaged()
    }

    private fun isProfileOwnerOrManaged(): Boolean {
        if (devicePolicyManager.isProfileOwnerApp(packageName)) return true
        
        val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
        return userManager.userProfiles.size > 1
    }

    private fun provisionManagedProfile() {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponentName)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }

        if (intent.resolveActivity(packageManager) != null) {
            provisionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Provisioning not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installAppInProfile(packageName: String) {
        if (devicePolicyManager.isProfileOwnerApp(this.packageName)) {
             // Already managed or something
        }
        try {
            devicePolicyManager.enableSystemApp(adminComponentName, packageName)
            Toast.makeText(this, "Enabled $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error enabling $packageName: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // PROVISION_REQUEST_CODE is no longer needed but kept for reference or removed
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isProfileOwner: Boolean,
    onProvisionClick: () -> Unit,
    onInstallAppClick: (String) -> Unit
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(Pink40, Purple40),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(brush = gradientBrush)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isProfileOwner) "Status: Profile Owner" else "Status: Not Provisioned",
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )
        
        if (!isProfileOwner) {
            Button(onClick = onProvisionClick) {
                Text(text = "Set up Work Profile")
            }
        } else {
            Text(
                text = "Work Profile is active!",
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Quick Actions",
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center
            )
            Button(onClick = { onInstallAppClick("com.android.chrome") }) {
                Text(text = "Enable Chrome in Work Profile")
            }
            
            Button(
                onClick = { onInstallAppClick("com.google.android.apps.maps") },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Enable Maps in Work Profile")
            }
        }
    }
}