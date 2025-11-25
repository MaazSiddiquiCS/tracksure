package com.tracksure.android

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.tracksure.android.mesh.BluetoothMeshService
import com.tracksure.android.services.MeshForegroundService
import com.tracksure.android.onboarding.*
import com.tracksure.android.ui.MapScreen
import com.tracksure.android.ui.MapViewModel
import com.tracksure.android.ui.theme.BitchatTheme
import com.tracksure.android.nostr.PoWPreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var onboardingCoordinator: OnboardingCoordinator
    private lateinit var bluetoothStatusManager: BluetoothStatusManager
    private lateinit var locationStatusManager: LocationStatusManager
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager

    // Core mesh service - managed at app level
    private val mainViewModel: MainViewModel by viewModels()

    // Switched from ChatViewModel to MapViewModel
    // Switched from ChatViewModel to MapViewModel
    private val mapViewModel: MapViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                // --- FIX 3: Singleton Synchronization Logic ---
                var service = MeshForegroundService.instance

                if (service == null) {
                    Log.d("MainActivity", "Initializing MeshService from Activity (Fresh Start)")
                    service = BluetoothMeshService(application)
                    // CRITICAL: Set the singleton NOW so the Service picks it up later
                    MeshForegroundService.instance = service
                } else {
                    Log.d("MainActivity", "Connecting to existing MeshService (Restart/Rotation)")
                }

                @Suppress("UNCHECKED_CAST")
                return MapViewModel(application, service!!) as T
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for modern Android look
        enableEdgeToEdge()

        // Initialize permission management
        permissionManager = PermissionManager(this)
        // Initialize core mesh service first
        bluetoothStatusManager = BluetoothStatusManager(
            activity = this,
            context = this,
            onBluetoothEnabled = ::handleBluetoothEnabled,
            onBluetoothDisabled = ::handleBluetoothDisabled
        )
        locationStatusManager = LocationStatusManager(
            activity = this,
            context = this,
            onLocationEnabled = ::handleLocationEnabled,
            onLocationDisabled = ::handleLocationDisabled
        )
        batteryOptimizationManager = BatteryOptimizationManager(
            activity = this,
            context = this,
            onBatteryOptimizationDisabled = ::handleBatteryOptimizationDisabled,
            onBatteryOptimizationFailed = ::handleBatteryOptimizationFailed
        )
        onboardingCoordinator = OnboardingCoordinator(
            activity = this,
            permissionManager = permissionManager,
            onOnboardingComplete = ::handleOnboardingComplete,
            onOnboardingFailed = ::handleOnboardingFailed
        )

        setContent {
            BitchatTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    OnboardingFlowScreen(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                    )
                }
            }
        }

        // Collect state changes in a lifecycle-aware manner
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.onboardingState.collect { state ->
                    handleOnboardingStateChange(state)
                }
            }
        }

        // Only start onboarding process if we're in the initial CHECKING state
        if (mainViewModel.onboardingState.value == OnboardingState.CHECKING) {
            checkOnboardingStatus()
        }
    }

    @Composable
    private fun OnboardingFlowScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val onboardingState by mainViewModel.onboardingState.collectAsState()
        val bluetoothStatus by mainViewModel.bluetoothStatus.collectAsState()
        val locationStatus by mainViewModel.locationStatus.collectAsState()
        val batteryOptimizationStatus by mainViewModel.batteryOptimizationStatus.collectAsState()
        val errorMessage by mainViewModel.errorMessage.collectAsState()
        val isBluetoothLoading by mainViewModel.isBluetoothLoading.collectAsState()
        val isLocationLoading by mainViewModel.isLocationLoading.collectAsState()
        val isBatteryOptimizationLoading by mainViewModel.isBatteryOptimizationLoading.collectAsState()

        DisposableEffect(context, bluetoothStatusManager) {

            val receiver = bluetoothStatusManager.monitorBluetoothState(
                context = context,
                bluetoothStatusManager = bluetoothStatusManager,
                onBluetoothStateChanged = { status ->
                    if (status == BluetoothStatus.ENABLED && onboardingState == OnboardingState.BLUETOOTH_CHECK) {
                        checkBluetoothAndProceed()
                    }
                }
            )

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                    Log.d("BluetoothStatusUI", "BroadcastReceiver unregistered")
                } catch (e: IllegalStateException) {
                    Log.w("BluetoothStatusUI", "Receiver was not registered")
                }
            }
        }

        when (onboardingState) {
            OnboardingState.PERMISSION_REQUESTING -> {
                InitializingScreen(modifier)
            }

            OnboardingState.BLUETOOTH_CHECK -> {
                BluetoothCheckScreen(
                    modifier = modifier,
                    status = bluetoothStatus,
                    onEnableBluetooth = {
                        mainViewModel.updateBluetoothLoading(true)
                        bluetoothStatusManager.requestEnableBluetooth()
                    },
                    onRetry = {
                        checkBluetoothAndProceed()
                    },
                    isLoading = isBluetoothLoading
                )
            }

            OnboardingState.LOCATION_CHECK -> {
                LocationCheckScreen(
                    modifier = modifier,
                    status = locationStatus,
                    onEnableLocation = {
                        mainViewModel.updateLocationLoading(true)
                        locationStatusManager.requestEnableLocation()
                    },
                    onRetry = {
                        checkLocationAndProceed()
                    },
                    isLoading = isLocationLoading
                )
            }

            OnboardingState.BATTERY_OPTIMIZATION_CHECK -> {
                BatteryOptimizationScreen(
                    modifier = modifier,
                    status = batteryOptimizationStatus,
                    onDisableBatteryOptimization = {
                        mainViewModel.updateBatteryOptimizationLoading(true)
                        batteryOptimizationManager.requestDisableBatteryOptimization()
                    },
                    onRetry = {
                        checkBatteryOptimizationAndProceed()
                    },
                    onSkip = {
                        // Skip battery optimization and proceed
                        proceedWithPermissionCheck()
                    },
                    isLoading = isBatteryOptimizationLoading
                )
            }

            OnboardingState.PERMISSION_EXPLANATION -> {
                PermissionExplanationScreen(
                    modifier = modifier,
                    permissionCategories = permissionManager.getCategorizedPermissions(),
                    onContinue = {
                        mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_REQUESTING)
                        onboardingCoordinator.requestPermissions()
                    }
                )
            }

            OnboardingState.CHECKING, OnboardingState.INITIALIZING, OnboardingState.COMPLETE -> {
                // Set up back navigation handling for the map screen
                val backCallback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // Let MapViewModel handle navigation state (e.g. closing user sheet)
                        val handled = mapViewModel.handleBackPressed()
                        if (!handled) {
                            // If MapViewModel doesn't handle it, disable this callback
                            // and let the system handle it (which will exit the app)
                            this.isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            this.isEnabled = true
                        }
                    }
                }

                // Add the callback
                onBackPressedDispatcher.addCallback(this, backCallback)

                // Use MapScreen instead of ChatScreen
                MapScreen(
                    viewModel = mapViewModel,
                    onNavigateToChat = { peerID ->
                        // Future: Could launch a simple Chat Activity or Dialog here
                        Log.d("MainActivity", "Requested chat with $peerID")
                    }
                )
            }

            OnboardingState.ERROR -> {
                InitializationErrorScreen(
                    modifier = modifier,
                    errorMessage = errorMessage,
                    onRetry = {
                        mainViewModel.updateOnboardingState(OnboardingState.CHECKING)
                        checkOnboardingStatus()
                    },
                    onOpenSettings = {
                        onboardingCoordinator.openAppSettings()
                    }
                )
            }
        }
    }

    private fun handleOnboardingStateChange(state: OnboardingState) {
        when (state) {
            OnboardingState.COMPLETE -> {
                Log.d("MainActivity", "Onboarding completed - app ready")
            }
            OnboardingState.ERROR -> {
                Log.e("MainActivity", "Onboarding error state reached")
            }
            else -> {}
        }
    }

    private fun checkOnboardingStatus() {
        Log.d("MainActivity", "Checking onboarding status")
        lifecycleScope.launch {
            delay(500)
            checkBluetoothAndProceed()
        }
    }

    /**
     * Check Bluetooth status and proceed with onboarding flow
     */
    private fun checkBluetoothAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            Log.d("MainActivity", "First-time launch, skipping Bluetooth check")
            proceedWithPermissionCheck()
            return
        }

        bluetoothStatusManager.logBluetoothStatus()
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())

        when (mainViewModel.bluetoothStatus.value) {
            BluetoothStatus.ENABLED -> checkLocationAndProceed()
            BluetoothStatus.DISABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            BluetoothStatus.NOT_SUPPORTED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
        }
    }

    private fun proceedWithPermissionCheck() {
        Log.d("MainActivity", "Proceeding with permission check")
        lifecycleScope.launch {
            delay(200)
            if (permissionManager.isFirstTimeLaunch()) {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            } else if (permissionManager.areAllPermissionsGranted()) {
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            } else {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
        }
    }

    private fun handleBluetoothEnabled() {
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(BluetoothStatus.ENABLED)
        checkLocationAndProceed()
    }

    private fun checkLocationAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            proceedWithPermissionCheck()
            return
        }

        locationStatusManager.logLocationStatus()
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        when (mainViewModel.locationStatus.value) {
            LocationStatus.ENABLED -> checkBatteryOptimizationAndProceed()
            LocationStatus.DISABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            LocationStatus.NOT_AVAILABLE -> {
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    private fun handleLocationEnabled() {
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(LocationStatus.ENABLED)
        checkBatteryOptimizationAndProceed()
    }

    private fun handleLocationDisabled(message: String) {
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        when {
            mainViewModel.locationStatus.value == LocationStatus.NOT_AVAILABLE -> {
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
            }
        }
    }

    private fun handleBluetoothDisabled(message: String) {
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())

        when {
            mainViewModel.bluetoothStatus.value == BluetoothStatus.NOT_SUPPORTED -> {
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            message.contains("Permission") && permissionManager.isFirstTimeLaunch() -> {
                proceedWithPermissionCheck()
            }
            message.contains("Permission") -> {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
            }
        }
    }

    private fun handleOnboardingComplete() {
        mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
        val serviceIntent = Intent(this, MeshForegroundService::class.java)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        lifecycleScope.launch {
            delay(500)
            mainViewModel.updateOnboardingState(OnboardingState.COMPLETE)
        }

        val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
        val currentLocationStatus = locationStatusManager.checkLocationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }

        when {
            currentBluetoothStatus != BluetoothStatus.ENABLED -> {
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            currentLocationStatus != LocationStatus.ENABLED -> {
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            currentBatteryOptimizationStatus == BatteryOptimizationStatus.ENABLED -> {
                mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            }
        }
    }

    private fun handleOnboardingFailed(message: String) {
        mainViewModel.updateErrorMessage(message)
        mainViewModel.updateOnboardingState(OnboardingState.ERROR)
    }

    private fun checkBatteryOptimizationAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            proceedWithPermissionCheck()
            return
        }

        if (BatteryOptimizationPreferenceManager.isSkipped(this)) {
            proceedWithPermissionCheck()
            return
        }

        batteryOptimizationManager.logBatteryOptimizationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)

        when (currentBatteryOptimizationStatus) {
            BatteryOptimizationStatus.DISABLED, BatteryOptimizationStatus.NOT_SUPPORTED -> {
                proceedWithPermissionCheck()
            }
            BatteryOptimizationStatus.ENABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
        }
    }

    private fun handleBatteryOptimizationDisabled() {
        mainViewModel.updateBatteryOptimizationLoading(false)
        mainViewModel.updateBatteryOptimizationStatus(BatteryOptimizationStatus.DISABLED)
        proceedWithPermissionCheck()
    }

    private fun handleBatteryOptimizationFailed(message: String) {
        mainViewModel.updateBatteryOptimizationLoading(false)
        val currentStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentStatus)
        mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
    }

    private fun stopServices() {
        Log.d("MainActivity", "Stopping services...")
        val serviceIntent = Intent(this, MeshForegroundService::class.java)
        stopService(serviceIntent)
    }

    private fun initializeApp() {
        Log.d("MainActivity", "Starting app initialization")
        mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)

        // 1. Start the service
        val serviceIntent = Intent(this, MeshForegroundService::class.java)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        lifecycleScope.launch {
            try {
                delay(1000)
                PoWPreferenceManager.init(this@MainActivity)

                if (!permissionManager.areAllPermissionsGranted()) {
                    val missing = permissionManager.getMissingPermissions()
                    handleOnboardingFailed("Some permissions were revoked...")
                    return@launch
                }
                // 2. WIRE UP THE DELEGATE HERE
                // We access the meshService directly from the ViewModel
                val meshService = mapViewModel.meshService

                meshService.delegate = object : com.tracksure.android.mesh.BluetoothMeshDelegate {
                    override fun didUpdatePeerLocation(peerID: String, locationPayload: String) {
                        // BRIDGE: Service -> ViewModel
                        mapViewModel.handleLocationPacket(peerID, locationPayload)
                    }

                    // Implement other required members as empty stubs (Map doesn't need them yet)
                    override fun didReceiveMessage(message: com.tracksure.android.model.BitchatMessage) {}
                    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {}
                    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {}
                    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {}
                    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
                    override fun getNickname(): String? = "Me" // Or fetch from prefs
                    override fun isFavorite(peerID: String): Boolean = false
                    override fun didUpdatePeerList(peers: List<String>) {
                        mapViewModel.updatePeerList(peers)
                    }
                }
                val currentPeers = meshService.getActivePeerIDs()
                if (currentPeers.isNotEmpty()) {
                    mapViewModel.updatePeerList(currentPeers)
                }
                mainViewModel.updateOnboardingState(OnboardingState.COMPLETE)
                Log.d("MainActivity", "Mesh service delegate connected")

                handleNotificationIntent(intent)
                delay(500)
                mainViewModel.updateOnboardingState(OnboardingState.COMPLETE)

            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize app", e)
                handleOnboardingFailed("Failed to initialize: ${e.message}")
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            handleNotificationIntent(intent)
        }
    }


    override fun onResume() {
        super.onResume()
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            // Use mapViewModel for background state management
            mapViewModel.setAppBackgroundState(false)

            val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
            if (currentBluetoothStatus != BluetoothStatus.ENABLED) {
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                return
            }
            val currentLocationStatus = locationStatusManager.checkLocationStatus()
            if (currentLocationStatus != LocationStatus.ENABLED) {
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            // Use mapViewModel for background state management
            mapViewModel.setAppBackgroundState(true)
        }
    }

    /**
     * Handle intents from notification clicks - simplified for Map view
     */
    private fun handleNotificationIntent(intent: Intent) {
        val shouldOpenPrivateChat = intent.getBooleanExtra(
            com.tracksure.android.ui.NotificationManager.EXTRA_OPEN_PRIVATE_CHAT,
            false
        )
        val shouldOpenGeohashChat = intent.getBooleanExtra(
            com.tracksure.android.ui.NotificationManager.EXTRA_OPEN_GEOHASH_CHAT,
            false
        )

        when {
            shouldOpenPrivateChat -> {
                val peerID = intent.getStringExtra(com.tracksure.android.ui.NotificationManager.EXTRA_PEER_ID)
                if (peerID != null) {
                    Log.d("MainActivity", "Opening private chat notification from $peerID")
                    // Select the peer on the map
                    mapViewModel.selectPeer(peerID)
                    mapViewModel.clearNotificationsForSender(peerID)
                }
            }

            shouldOpenGeohashChat -> {
                val geohash = intent.getStringExtra(com.tracksure.android.ui.NotificationManager.EXTRA_GEOHASH)
                if (geohash != null) {
                    Log.d("MainActivity", "Opening geohash notification #$geohash")
                    // Set current context for notification handling
                    mapViewModel.setCurrentGeohash(geohash)
                    mapViewModel.clearNotificationsForGeohash(geohash)

                    // Logic to zoom map to this geohash could go here
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { locationStatusManager.cleanup() } catch (e: Exception) {}
        stopServices()
    }
}
