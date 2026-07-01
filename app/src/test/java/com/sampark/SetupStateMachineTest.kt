package com.sampark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// --- Fake ---

class FakeAppPreferences : AppPreferences {
    override var setupComplete: Boolean = false
    override var scanInProgress: Boolean = false
}

// --- Tests ---

class SetupStateMachineTest {

    private fun machine(prefs: AppPreferences = FakeAppPreferences()) =
        SetupStateMachine(prefs)

    @Test
    fun `initial state is WELCOME`() {
        assertEquals(SetupState.WELCOME, machine().state)
    }

    @Test
    fun `onStartTapped transitions from WELCOME to REQUESTING_PERMISSIONS`() {
        val m = machine()
        m.onStartTapped()
        assertEquals(SetupState.REQUESTING_PERMISSIONS, m.state)
    }

    @Test
    fun `onPermissionsGranted transitions to SETUP_COMPLETE`() {
        val m = machine()
        m.onStartTapped()
        m.onPermissionsGranted()
        assertEquals(SetupState.SETUP_COMPLETE, m.state)
    }

    @Test
    fun `onPermissionDenied transitions to PERMISSION_DENIED`() {
        val m = machine()
        m.onStartTapped()
        m.onPermissionDenied()
        assertEquals(SetupState.PERMISSION_DENIED, m.state)
    }

    @Test
    fun `onRetryPermissions transitions from PERMISSION_DENIED back to REQUESTING_PERMISSIONS`() {
        val m = machine()
        m.onStartTapped()
        m.onPermissionDenied()
        m.onRetryPermissions()
        assertEquals(SetupState.REQUESTING_PERMISSIONS, m.state)
    }

    @Test
    fun `onPermissionsGranted writes setupComplete to prefs`() {
        val prefs = FakeAppPreferences()
        val m = machine(prefs)
        m.onStartTapped()
        m.onPermissionsGranted()
        assertTrue(prefs.setupComplete)
    }
}
