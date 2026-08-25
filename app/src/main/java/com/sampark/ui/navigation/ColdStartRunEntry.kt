package com.sampark.ui.navigation

/**
 * Decides whether a run route (RUN_TRANSLATE / RUN_ROLLBACK) is being entered as
 * the app's *cold-start* destination — i.e. `AppRouter.resolveStartDestination()`
 * sent us straight there because a previous process left `phase == RUNNING`.
 *
 * That case must show the paused prompt and wait for a fresh, explicit Resume tap
 * rather than silently picking up where the killed process left off. Every other
 * way of reaching those routes (permission just granted, rollback just confirmed,
 * "translate again" from Home) is a deliberate tap and starts running immediately.
 *
 * The cold-start answer is single-use: once the cold-start entry has been
 * consumed, later visits to the same route within this process are ordinary
 * deliberate navigations and get `false`.
 */
internal class ColdStartRunEntry(private val startDestination: String) {

    private var consumed = false

    fun isColdStartEntryFor(route: String): Boolean {
        if (consumed || startDestination != route) return false
        consumed = true
        return true
    }
}
