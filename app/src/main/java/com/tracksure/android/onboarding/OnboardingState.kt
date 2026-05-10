package com.tracksure.android.onboarding

enum class OnboardingState {
    CHECKING,
    WELCOME,
    BLUETOOTH_CHECK,
    LOCATION_CHECK,
    BATTERY_OPTIMIZATION_CHECK,
    PERMISSION_EXPLANATION,
    PERMISSION_REQUESTING,
    INITIALIZING,
    COMPLETE,
    ERROR
}