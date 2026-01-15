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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.edit


class MainActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private lateinit var sharedPreferences: SharedPreferences

    private var isProfileOwner by mutableStateOf(false)
    private var isAdminActive by mutableStateOf(false)
    private var isRunningInWorkProfile by mutableStateOf(false)
    private var hasWorkProfile by mutableStateOf(false)
    private var hasOtherProfiles by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private var showBottomSheet by mutableStateOf(false)
    private var showSecureFolderDialog by mutableStateOf(false)
    private var isSecureFolderConfigured by mutableStateOf(false)
    private var showEnableAppsSheet by mutableStateOf(false)

    companion object {
        internal const val PREFS_NAME = "ProfileManagerPrefs"
        internal const val KEY_SECURE_FOLDER_ACTIVE = "secure_folder_active"
        internal const val KEY_SECURE_FOLDER_CONFIGURED = "secure_folder_configured"
        internal const val KEY_MANUAL_THRESHOLD_ENABLED = "manual_threshold_enabled"
        internal const val KEY_MANUAL_THRESHOLD_VALUE = "manual_threshold_value"
    }

    // Polling mechanism
    private val pollingHandler = Handler(Looper.getMainLooper())
    private var isPolling = false
    private val workProfilePollingRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return

            // Capture the previous work profile state before updating to detect when it's newly created
            val hadWorkProfile = hasWorkProfile

            // Update the state variables
            updateProfileStatus()

            // If work profile was just detected and we're in parent profile
            if (hasWorkProfile && !hadWorkProfile && !isRunningInWorkProfile) {
                stopWorkProfilePolling()
            } else if (hasWorkProfile && !isRunningInWorkProfile) {
                stopWorkProfilePolling()
            } else {
                // Work profile not detected yet - continue polling by rescheduling this runnable
                // Creates a continuous loop that checks every 2 seconds until work profile appears
                pollingHandler.postDelayed(this, 2000)
                Log.i("ProfileManager", "Polling restarted")
            }
        }
    }

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
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Check if Secure Folder setting is configured
        isSecureFolderConfigured = sharedPreferences.getBoolean(KEY_SECURE_FOLDER_CONFIGURED, false)

        // If not configured, show dialog at startup
        if (!isSecureFolderConfigured) {
            showSecureFolderDialog = true
        }

        enableEdgeToEdge()

        // Start polling for work profile creation
        startWorkProfilePolling()

        setContent {
            ProfileManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        // Only show FAB after Secure Folder is configured
                        if (isSecureFolderConfigured) {
                            FloatingActionButton(onClick = { showBottomSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    // Only show content after Secure Folder is configured
                    if (isSecureFolderConfigured) {
                        Column(modifier = Modifier.padding(innerPadding)) {
                            MainScreen(
                                modifier = Modifier,
                                isProfileOwner = isProfileOwner,
                                isAdminActive = isAdminActive,
                                isRunningInWorkProfile = isRunningInWorkProfile,
                                hasWorkProfile = hasWorkProfile,
                                hasOtherProfiles = hasOtherProfiles,
                                isLoading = isLoading,
                                isSamsungDevice = isSamsungDevice(),
                                onProvisionClick = { provisionManagedProfile() },
                                onActivateAdminClick = { activateDeviceAdmin() },
                                onDeleteProfileClick = { deleteWorkProfile() },
                                onEnableAppsClick = { showEnableAppsSheet = true }
                            )
                        }

                        if (showBottomSheet) {
                            SettingsBottomSheet(
                                sharedPreferences = sharedPreferences,
                                onDismiss = { showBottomSheet = false }
                            )
                        }

                        if (showEnableAppsSheet) {
                            EnableAppsBottomSheet(
                                devicePolicyManager = devicePolicyManager,
                                adminComponentName = adminComponentName,
                                onDismiss = { showEnableAppsSheet = false }
                            )
                        }
                    }

                    if (showSecureFolderDialog) {
                        // Add a gradient background for the initial configuration screen
                        val dialogGradientBrush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1A237E), // Deep Blue
                                Color(0xFF283593), // Indigo
                                Color(0xFF3949AB)  // Blue
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(brush = dialogGradientBrush)
                        ) {
                            // Empty background layer
                        }

                        SecureFolderConfigDialog(
                            onConfirm = { isActive ->
                                sharedPreferences.edit {
                                    putBoolean(KEY_SECURE_FOLDER_ACTIVE, isActive)
                                    putBoolean(KEY_SECURE_FOLDER_CONFIGURED, true)
                                }
                                isSecureFolderConfigured = true
                                showSecureFolderDialog = false

                                // Update profile status with the new threshold setting
                                updateProfileStatus()
                            },
                            onDismiss = {
                                // Don't allow dismissing without making a choice at startup
                                // User must choose to proceed
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWorkProfilePolling()
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
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val profileCount = userManager.userProfiles.size
        val isProfileOwner = dpm.isProfileOwnerApp(packageName)

        // Check if manual threshold is enabled
        val isManualThresholdEnabled = sharedPreferences.getBoolean(KEY_MANUAL_THRESHOLD_ENABLED, false)

        val threshold = if (isManualThresholdEnabled) {
            // Use manual threshold value (default to 3 if not set)
            sharedPreferences.getInt(KEY_MANUAL_THRESHOLD_VALUE, 3)
        } else {
            // Check if Secure Folder is active from SharedPreferences
            val isSecureFolderActive = sharedPreferences.getBoolean(KEY_SECURE_FOLDER_ACTIVE, false)
            // If Secure Folder is active: profileCount > 3 (owner + system + secure folder = 3, work profile makes it 4)
            // If Secure Folder is not active: profileCount > 2 (owner + system = 2, work profile makes it 3)
            if (isSecureFolderActive) 3 else 2
        }

        val hasProfile = profileCount > threshold && !isProfileOwner
        return hasProfile
    }

    fun Context.isRunningInWorkProfile(): Boolean {
        val userManager = getSystemService(UserManager::class.java)
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // API 30+ has a direct, public API.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isManagedProfile = userManager.isManagedProfile
            return isManagedProfile
        }

        // For older APIs, check if we're the profile owner
        val isProfileOwner = dpm.isProfileOwnerApp(packageName)
        return isProfileOwner
    }

    private fun updateProfileStatus() {
        val isOwner = devicePolicyManager.isProfileOwnerApp(packageName)

        val isActive = devicePolicyManager.isAdminActive(adminComponentName)

        val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
        val profiles = userManager.userProfiles

        val hasOthers = profiles.any { it != android.os.Process.myUserHandle() }

        val runningInWork = isRunningInWorkProfile()

        val hasWork = hasWorkProfile()

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

        startProvisioning()
    }

    private fun startProvisioning() {
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

    private fun startWorkProfilePolling() {
        if (!isRunningInWorkProfile && !hasWorkProfile) {
            isPolling = true
            pollingHandler.postDelayed(workProfilePollingRunnable, 2000)
        }
    }

    private fun stopWorkProfilePolling() {
        isPolling = false
        pollingHandler.removeCallbacks(workProfilePollingRunnable)
    }
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
    onDeleteProfileClick: () -> Unit,
    onEnableAppsClick: () -> Unit
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
                )
            } else {
                Text(
                    text = "This app is running in the Main Profile.",
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = if (isProfileOwner && isRunningInWorkProfile) {
                    "Work Profile is active and managed by this app!"
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
                    Button(
                        onClick = onEnableAppsClick,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(text = "Enable Apps")
                    }

                    Button(
                        onClick = onDeleteProfileClick,
                        modifier = Modifier.padding(top = 16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Text(text = "Delete Work Profile", color = Color.White)
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

@Composable
fun SecureFolderConfigDialog(
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Secure Folder Configuration")
        },
        text = {
            Text(
                text = "Do you have Samsung Secure Folder active on this device?\n\n" +
                        "This setting helps the app correctly detect if a Work Profile exists."
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(true) }
            ) {
                Text("Yes, I have Secure Folder")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onConfirm(false) }
            ) {
                Text("No, I don't have Secure Folder")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    sharedPreferences: SharedPreferences,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var isSecureFolderActive by remember {
        mutableStateOf(sharedPreferences.getBoolean(MainActivity.KEY_SECURE_FOLDER_ACTIVE, false))
    }
    var isManualThresholdEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean(MainActivity.KEY_MANUAL_THRESHOLD_ENABLED, false))
    }
    var manualThresholdValue by remember {
        mutableStateOf(sharedPreferences.getInt(MainActivity.KEY_MANUAL_THRESHOLD_VALUE, 3).toString())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Settings",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Secure Folder Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Secure Folder",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (isSecureFolderActive) "Active" else "Inactive",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = isSecureFolderActive,
                    enabled = !isManualThresholdEnabled,
                    onCheckedChange = { newValue ->
                        isSecureFolderActive = newValue
                        sharedPreferences.edit {
                            putBoolean(
                                MainActivity.KEY_SECURE_FOLDER_ACTIVE,
                                newValue
                            )
                            putBoolean(MainActivity.KEY_SECURE_FOLDER_CONFIGURED, true)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Threshold Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Manual Threshold",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Override automatic detection",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = isManualThresholdEnabled,
                    onCheckedChange = { newValue ->
                        isManualThresholdEnabled = newValue
                        sharedPreferences.edit {
                            putBoolean(MainActivity.KEY_MANUAL_THRESHOLD_ENABLED, newValue)
                            if (newValue) {
                                // Set default threshold to 3 when enabling
                                putInt(MainActivity.KEY_MANUAL_THRESHOLD_VALUE, 3)
                                manualThresholdValue = "3"
                            }
                        }
                    }
                )
            }

            // Manual Threshold Input Field (shown when manual threshold is enabled)
            if (isManualThresholdEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualThresholdValue,
                    onValueChange = { newValue ->
                        // Only allow digits
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            manualThresholdValue = newValue
                            // Save to SharedPreferences if valid
                            newValue.toIntOrNull()?.let { intValue ->
                                sharedPreferences.edit {
                                    putInt(MainActivity.KEY_MANUAL_THRESHOLD_VALUE, intValue)
                                }
                            }
                        }
                    },
                    label = { Text("Threshold Value") },
                    placeholder = { Text("Enter threshold (e.g., 3)") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "Work profile is detected if profileCount > threshold",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnableAppsBottomSheet(
    devicePolicyManager: DevicePolicyManager,
    adminComponentName: ComponentName,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // List of apps to show - for now just Samsung Gallery
    data class AppToEnable(
        val displayName: String,
        val packageName: String,
        var isEnabled: Boolean = false
    )

    var apps by remember {
        mutableStateOf(listOf(
            AppToEnable("Samsung Gallery", "com.sec.android.gallery3d", false)
        ))
    }

    // Check current enabled state of apps
    androidx.compose.runtime.LaunchedEffect(Unit) {
        apps = apps.map { app ->
            val isFullyEnabled = try {
                // Check if app is hidden
                val isHidden = devicePolicyManager.isApplicationHidden(adminComponentName, app.packageName)

                if (isHidden) {
                    // If hidden, definitely not enabled
                    false
                } else {
                    // If not hidden, check if it's truly enabled by checking if it's launchable
                    // An app in headless mode won't have a launch intent
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    val hasLaunchIntent = launchIntent != null

                    android.util.Log.d("EnableApps", "App ${app.packageName}: hidden=$isHidden, hasLaunchIntent=$hasLaunchIntent")

                    // App is fully enabled only if not hidden AND has a launch intent
                    hasLaunchIntent
                }
            } catch (e: Exception) {
                android.util.Log.e("EnableApps", "Error checking app state: ${e.message}")
                false
            }
            app.copy(isEnabled = isFullyEnabled)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Enable Apps",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // App list
            apps.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = app.displayName,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = app.packageName,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = app.isEnabled,
                        onCheckedChange = { newValue ->
                            try {
                                if (newValue) {
                                    // Enable the app with full UI access
                                    android.util.Log.d("EnableApps", "Starting to enable ${app.packageName} with UI access")

                                    // Step 1: First unhide the application (must be done before enabling)
                                    val unhideResult = devicePolicyManager.setApplicationHidden(
                                        adminComponentName,
                                        app.packageName,
                                        false
                                    )
                                    android.util.Log.d("EnableApps", "Unhide result: $unhideResult")

                                    // Step 2: Enable the system app with UI components
                                    // Use the Intent-based enableSystemApp which enables the app with its launcher
                                    try {
                                        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                                        launchIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                                        launchIntent.setPackage(app.packageName)

                                        // This enables the app with its launcher activity
                                        devicePolicyManager.enableSystemApp(adminComponentName, launchIntent)
                                        android.util.Log.d("EnableApps", "System app enabled with launcher intent")
                                    } catch (e: Exception) {
                                        android.util.Log.w("EnableApps", "Could not enable with intent, trying package name: ${e.message}")
                                        try {
                                            devicePolicyManager.enableSystemApp(adminComponentName, app.packageName)
                                            android.util.Log.d("EnableApps", "System app enabled with package name")
                                        } catch (e2: Exception) {
                                            android.util.Log.w("EnableApps", "Could not enable system app: ${e2.message}")
                                        }
                                    }

                                    // Step 3: Clear any application restrictions
                                    try {
                                        devicePolicyManager.setApplicationRestrictions(
                                            adminComponentName,
                                            app.packageName,
                                            android.os.Bundle()
                                        )
                                        android.util.Log.d("EnableApps", "Application restrictions cleared")
                                    } catch (e: Exception) {
                                        android.util.Log.w("EnableApps", "Could not clear restrictions: ${e.message}")
                                    }

                                    // Step 4: Try to grant necessary permissions for Gallery
                                    if (app.packageName == "com.sec.android.gallery3d") {
                                        val permissions = mutableListOf(
                                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )

                                        // Add READ_MEDIA permissions for API 33+
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                                            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                                        }

                                        permissions.forEach { permission ->
                                            try {
                                                devicePolicyManager.setPermissionGrantState(
                                                    adminComponentName,
                                                    app.packageName,
                                                    permission,
                                                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                                                )
                                                android.util.Log.d("EnableApps", "Granted permission: $permission")
                                            } catch (e: Exception) {
                                                android.util.Log.w("EnableApps", "Could not grant permission $permission: ${e.message}")
                                            }
                                        }
                                    }

                                    // Step 5: Verify the app is now launchable
                                    val pm = context.packageManager
                                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                    val hasLaunchIntent = launchIntent != null

                                    android.util.Log.d("EnableApps", "After enabling: hasLaunchIntent=$hasLaunchIntent")

                                    // Update the state
                                    apps = apps.map {
                                        if (it.packageName == app.packageName) {
                                            it.copy(isEnabled = hasLaunchIntent)
                                        } else {
                                            it
                                        }
                                    }

                                    if (hasLaunchIntent) {
                                        Toast.makeText(
                                            context,
                                            "${app.displayName} enabled with UI access",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "${app.displayName} enabled but UI may not be available. Check logs.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }

                                } else {
                                    // Disable the app (hide it)
                                    val hideResult = devicePolicyManager.setApplicationHidden(
                                        adminComponentName,
                                        app.packageName,
                                        true
                                    )

                                    if (hideResult) {
                                        apps = apps.map {
                                            if (it.packageName == app.packageName) {
                                                it.copy(isEnabled = false)
                                            } else {
                                                it
                                            }
                                        }

                                        Toast.makeText(
                                            context,
                                            "${app.displayName} disabled",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Failed to disable ${app.displayName}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                android.util.Log.e("EnableApps", "Error toggling app", e)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

