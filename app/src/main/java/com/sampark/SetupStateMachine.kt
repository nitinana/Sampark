package com.sampark

enum class SetupState {
    WELCOME,
    REQUESTING_PERMISSIONS,
    PERMISSION_DENIED,
    SETUP_COMPLETE
}

class SetupStateMachine(private val prefs: AppPreferences) {

    var state: SetupState = SetupState.WELCOME
        private set

    fun onStartTapped() {
        state = SetupState.REQUESTING_PERMISSIONS
    }

    fun onPermissionsGranted() {
        prefs.setupComplete = true
        state = SetupState.SETUP_COMPLETE
    }

    fun onPermissionDenied() {
        state = SetupState.PERMISSION_DENIED
    }

    fun onRetryPermissions() {
        state = SetupState.REQUESTING_PERMISSIONS
    }
}
