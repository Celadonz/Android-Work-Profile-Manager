package com.mallick.nil.profilemanager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mallick.nil.profilemanager.ui.theme.Pink40
import com.mallick.nil.profilemanager.ui.theme.ProfileManagerTheme
import com.mallick.nil.profilemanager.ui.theme.Purple40
import com.mallick.nil.profilemanager.ui.theme.PurpleGrey40
import com.mallick.nil.profilemanager.ui.theme.PurpleGrey80


class MainActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private var isProfileOwner by mutableStateOf(false)
    private var isAdminActive by mutableStateOf(false)
    private var isRunningInWorkProfile by mutableStateOf(false)
    private var hasWorkProfile by mutableStateOf(false)
    private var hasOtherProfiles by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private lateinit var prefs: SharedPreferences

    private val provisionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Provisioning started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Provisioning failed or cancelled.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        enableEdgeToEdge()
        setContent {
            ProfileManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        isProfileOwner = isProfileOwner,
                        isAdminActive = isAdminActive,
                        isRunningInWorkProfile = isRunningInWorkProfile,
                        hasWorkProfile = hasWorkProfile,
                        hasOtherProfiles = hasOtherProfiles,
                        isLoading = isLoading,
                        isSamsungDevice = isSamsungDevice(),
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

    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    fun Context.hasWorkProfile(): Boolean {
        val userManager = getSystemService(UserManager::class.java)
        // Owner profile will see exactly one extra profile if Work Profile exists
        return userManager.userProfiles.size > 3
    }

    fun Context.isRunningInWorkProfile(): Boolean {
        if (!hasWorkProfile()) return false

        val userManager = getSystemService(UserManager::class.java)

        // API 30+ has a direct, public API.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return userManager.isManagedProfile
        }

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isInWorkProfile = dpm.isProfileOwnerApp(packageName)

        return isInWorkProfile
    }

    private fun updateProfileStatus() {
        val isOwner = devicePolicyManager.isProfileOwnerApp(packageName)
        val isActive = devicePolicyManager.isAdminActive(adminComponentName)
        val runningInWork = isRunningInWorkProfile()
        val hasWork = hasWorkProfile()

        val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
        val profiles = userManager.userProfiles
        val hasOthers = profiles.any { it != android.os.Process.myUserHandle() }

        isProfileOwner = isOwner
        isAdminActive = isActive
        isRunningInWorkProfile = runningInWork
        hasOtherProfiles = hasOthers
        hasWorkProfile = hasWork
    }

    private fun provisionManagedProfile() {
        if (!isAdminActive) {
            Toast.makeText(this, "Please enable Device Admin first (click 'Activate Admin')", Toast.LENGTH_LONG).show()
            activateDeviceAdmin()
            return
        }

        isLoading = true
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponentName)
            // EXTRA_PROVISIONING_SKIP_ENCRYPTION is deprecated in API 33, but fine for older
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }

        if (intent.resolveActivity(packageManager) != null) {
            provisionLauncher.launch(intent)
        } else {
            isLoading = false
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
    hasWorkProfile: Boolean,
    hasOtherProfiles: Boolean,
    isLoading: Boolean,
    isSamsungDevice: Boolean,
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
        if (!isSamsungDevice) {
            Text(
                text = "This application only works on Samsung devices.",
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
        } else {
            if (isRunningInWorkProfile) {
                Text(
                    text = "This app is running in the Work Profile.",
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center,
                    color = PurpleGrey40
                )
            } else {
                Text(
                    text = "This app is running in the Main Profile.",
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center,
                    color = PurpleGrey80
                )
            }

            Text(
                text = if (isProfileOwner) {
                    "This app is the owner of the Work Profile."
                } else if (hasWorkProfile) {
                    "A Work Profile already exists."
                } else if (isAdminActive) {
                    "This app is a Device Admin. You can set up a Work Profile now."
                } else run {
                    "This app is not a Device Admin. Please set it as a Device Admin to set up the Work Profile."
                },
                modifier = Modifier.padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )

            if (!isRunningInWorkProfile) {
                if (hasWorkProfile) {
                    Text(
                        text = "If you already set up a profile, look for this app with a briefcase icon in your app drawer.",
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                } else {
                    if (hasOtherProfiles) {
                        Text(
                            text = "Warning: Another profile (Work Profile, Dual Messenger or Secure Folder) detected. " +
                                    "Android typically allows only ONE work profile. Provisioning may fail.",
                            color = Color.Yellow,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isAdminActive) {
                    if (!hasWorkProfile) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            Button(onClick = onProvisionClick) {
                                Text(text = "Set up Work Profile")
                            }
                        }
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
}