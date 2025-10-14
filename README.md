TrackSure for Android

[!WARNING]This software is in active development as a Final Year Project (FYP) and has not received external security review. It may contain vulnerabilities and may not meet its stated security goals. Do not use it for sensitive use cases until reviewed. Work in progress.

TrackSure is a decentralized, peer-to-peer location tracking app for Android, built to enable real-time location advertising and scanning using Bluetooth Low Energy (BLE) mesh networks. Originally inspired by the Bitchat project, TrackSure serves a distinct purpose: enabling location tracking solely through smartphones, leveraging BLE to create a mesh network for advertising and scanning location data. No internet, servers, or phone numbers are required—just pure, encrypted communication over Bluetooth.
Purpose
TrackSure is a Final Year Project (FYP) focused on developing a robust location tracking system. It uses BLE to create a mesh network where devices advertise and scan location data, enabling real-time tracking in a decentralized, privacy-first manner. The app runs a foreground service to ensure persistent location tracking, even when the app is in the background or the screen is off.
Features

✅ Decentralized Mesh Network: Automatic peer discovery and multi-hop location data relay over Bluetooth LE.
✅ Persistent Location Tracking: Background location advertising via MeshForegroundService for continuous operation.
✅ End-to-End Encryption: X25519 key exchange + AES-256-GCM for secure data transmission.
✅ Privacy First: No accounts, phone numbers, or persistent identifiers.
✅ Modern Android UI: Jetpack Compose with Material Design 3.
✅ Dark/Light Themes: Terminal-inspired aesthetic.
✅ Battery Optimization: Adaptive scanning and power management for efficient operation.

Android Setup
Prerequisites

Android Studio: 2024.2.1 or newer
Android SDK: API level 26 (Android 8.0) or higher
Kotlin: 1.8.0 or newer
Gradle: 7.0 or newer

Build Instructions

Clone the repository:
git clone https://github.com/yourusername/tracksure.git
cd tracksure


Open in Android Studio:

Select File > Open and navigate to the tracksure directory.


Build the project:
./gradlew build


Install on device:
./gradlew installDebug



Development Build
For development builds with debugging enabled:
./gradlew assembleDebug
adb install -r tracksure/build/outputs/apk/debug/tracksure-debug.apk

Android-Specific Requirements
Permissions
The app requires the following permissions (automatically requested):

Bluetooth: Core BLE functionality for mesh networking.
Location: Required for BLE scanning and location tracking on Android.
Foreground Service: Ensures persistent location tracking in the background.

Hardware Requirements

Bluetooth LE (BLE): Required for mesh networking.
Android 8.0+: API level 26 minimum.
RAM: 2GB recommended for optimal performance.

Usage

Install TrackSure on your Android device (requires Android 8.0+).
Grant permissions for Bluetooth, location, and foreground service when prompted.
Launch TrackSure to auto-start mesh networking and location tracking.
Connect automatically to nearby TrackSure devices via the BLE mesh network.
Monitor location data as devices advertise and scan within the mesh.

Technical Architecture
Core Components

TrackSureApplication.kt: Application-level initialization and dependency injection.
MainActivity.kt: Handles permissions and hosts the Jetpack Compose UI.
MeshForegroundService.kt: Manages persistent BLE mesh networking and location tracking in the background.
BluetoothMeshService.kt: Core BLE mesh networking (central + peripheral roles).
EncryptionService.kt: Cryptographic operations using BouncyCastle.
BinaryProtocol.kt: Binary packet encoding/decoding for efficient data transfer.
ChatScreen.kt: Jetpack Compose UI with Material Design 3.

Dependencies

Jetpack Compose: Modern declarative UI.
BouncyCastle: Cryptographic operations (X25519, Ed25519, AES-GCM).
Nordic BLE Library: Reliable Bluetooth LE operations.
Kotlin Coroutines: Asynchronous programming for mesh operations.
EncryptedSharedPreferences: Secure storage for user settings.

Mesh Networking

Each device acts as both client and peripheral.
Automatic peer discovery and connection management.
Store-and-forward for offline message delivery.
Adaptive duty cycling for battery optimization.

Android-Specific Optimizations

Foreground Service: Ensures persistent location tracking via MeshForegroundService.
Coroutine Architecture: Asynchronous operations for mesh networking.
Lifecycle-Aware: Proper handling of Android app lifecycle.
Battery Optimization: Adaptive scanning adjusts to battery state.

Security & Privacy
Encryption

Location Data: X25519 key exchange + AES-256-GCM encryption.
Digital Signatures: Ed25519 for data authenticity.
Forward Secrecy: New key pairs generated each session.

Privacy Features

No Registration: No accounts, emails, or phone numbers required.
Ephemeral by Default: Location data exists only in device memory.
Emergency Wipe: Triple-tap to instantly clear all data.

Performance & Efficiency
Battery Optimization

Adaptive Power Modes: Adjusts scanning based on battery level.
Performance mode: Full features when charging or >60% battery.
Balanced mode: Default operation (30-60% battery).
Power saver: Reduced scanning when <30% battery.
Ultra-low power: Emergency mode when <10% battery.


Background Efficiency: Power saving when app is backgrounded.

Network Efficiency

Optimized Bloom Filters: Faster duplicate detection with less memory.
Adaptive Connection Limits: Adjusts peer connections based on power mode.

Contributing
Contributions are welcome, especially for this FYP! Key areas for enhancement:

Performance: Improve battery optimization and mesh reliability.
UI/UX: Enhance Material Design 3 features.
Security: Strengthen cryptographic features.
Testing: Add unit and integration test coverage.
Documentation: Improve API and development guides.

Support & Issues

Bug Reports: Create an issue on GitHub with device info and logs.
Feature Requests: Start a discussion on GitHub.
Security Issues: Email security concerns privately.

License
This project is released into the public domain. See the LICENSE file for details.
