package com.mallick.nil.profilemanager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
    private var isAdminActive by mutableStateOf(false)
    private var isRunningInWorkProfile by mutableStateOf(false)
    private var hasOtherProfiles by mutableStateOf(false)

    private val provisionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Provisioning started", Toast.LENGTH_SHORT).show()
            updateProfileStatus()
        } else {
            Toast.makeText(this, "Provisioning failed or cancelled.", Toast.LENGTH_LONG).show()
        }
    }

    private fun canCreateWorkProfile(): Boolean {
        return true
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
                        isAdminActive = isAdminActive,
                        isRunningInWorkProfile = isRunningInWorkProfile,
                        hasOtherProfiles = hasOtherProfiles,
                        onProvisionClick = { provisionManagedProfile() },
                        onActivateAdminClick = { activateDeviceAdmin() },
                        onInstallAppClick = { packageName -> installAppInProfile(packageName) },
                        onDeleteProfileClick = { deleteWorkProfile() }
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
        val isOwner = devicePolicyManager.isProfileOwnerApp(packageName)
        val isActive = devicePolicyManager.isAdminActive(adminComponentName)
        
        val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
        val profiles = userManager.userProfiles
        
        val isMainProfile = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            !userManager.isManagedProfile
        } else {
            // Fallback for older APIs if needed, but isProfileOwnerApp usually suffices
            !devicePolicyManager.isProfileOwnerApp(packageName)
        }

        val hasOthers = profiles.any { it != android.os.Process.myUserHandle() }

        isProfileOwner = isOwner
        isAdminActive = isActive
        isRunningInWorkProfile = !isMainProfile
        hasOtherProfiles = hasOthers

        Log.e("ProfileManager", "$isProfileOwner $isActive $isRunningInWorkProfile $hasOthers")
    }

    private fun provisionManagedProfile() {
        if (!isAdminActive) {
            Toast.makeText(this, "Please enable Device Admin first (click 'Activate Admin')", Toast.LENGTH_LONG).show()
            activateDeviceAdmin()
            return
        }

        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponentName)
            // EXTRA_PROVISIONING_SKIP_ENCRYPTION is deprecated in API 33, but fine for older
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }

        if (intent.resolveActivity(packageManager) != null) {
            provisionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Provisioning not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun activateDeviceAdmin() {
        val adminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to set up the Work Profile.")
        }
        startActivity(adminIntent)
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

    private fun deleteWorkProfile() {
        try {
            if (devicePolicyManager.isProfileOwnerApp(packageName)) {
                devicePolicyManager.wipeData(0)
                Toast.makeText(this, "Work profile deletion initiated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "App is not the profile owner. Cannot delete profile directly.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error deleting work profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // PROVISION_REQUEST_CODE is no longer needed but kept for reference or removed
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isProfileOwner: Boolean,
    isAdminActive: Boolean,
    isRunningInWorkProfile: Boolean,
    hasOtherProfiles: Boolean,
    onProvisionClick: () -> Unit,
    onActivateAdminClick: () -> Unit,
    onInstallAppClick: (String) -> Unit,
    onDeleteProfileClick: () -> Unit
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
            text = if (isProfileOwner) {
                "This app is the owner of the Work Profile."
            } else if (isAdminActive) {
                "Device Admin is active and managing this profile."
            } else run {
                "Device Admin is not active. Please enable it to set up the Work Profile."
            },
            modifier = Modifier.padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )

        if (isRunningInWorkProfile) {
            Text(
                text = "This app is running INSIDE the Work Profile.",
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                color = androidx.compose.ui.graphics.Color.White
            )
        } else {
             Text(
                text = "This app is running in the MAIN Profile.",
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
        }
        
        if (!isProfileOwner && !isRunningInWorkProfile) {
            if (hasOtherProfiles) {
                Text(
                    text = "Warning: Another profile (Work Profile or Secure Folder) detected. " +
                    "Android typically allows only ONE work profile. Provisioning may fail.",
                    color = androidx.compose.ui.graphics.Color.Yellow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "If you already set up a profile, look for this app with a briefcase icon in your app drawer.",
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            
            if (isAdminActive) {
                Button(onClick = onProvisionClick) {
                    Text(text = "Set up Work Profile")
                }
            } else {
                Button(onClick = onActivateAdminClick) {
                    Text(text = "Set Up Device Admin")
                }
            }
        } else {
            if (isProfileOwner) {
                Text(
                    text = "Work Profile is active and managed by this app!",
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

                Button(
                    onClick = onDeleteProfileClick,
                    modifier = Modifier.padding(top = 32.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color.Red
                    )
                ) {
                    Text(text = "Delete Work Profile", color = androidx.compose.ui.graphics.Color.White)
                }
            } else {
                Text(
                    text = "You are in the Work Profile, but this instance is not the owner. " +
                           "This usually happens if provisioning was partially successful or " +
                           "if another app (like Knox) is managing this profile.",
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}