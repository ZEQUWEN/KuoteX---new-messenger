package com.example.ui

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder

private const val TAG = "NavigationExtensions"

/**
 * Safely navigates to the given route, preventing multiple rapid navigations,
 * duplicate destinations on top of the stack, and invalid lifecycle transition states.
 */
fun NavController.navigateSafe(route: String, builder: (NavOptionsBuilder.() -> Unit)? = null) {
    try {
        val currentRoute = currentBackStackEntry?.destination?.route
        if (currentRoute == route) {
            return
        }

        val lifecycleState = currentBackStackEntry?.lifecycle?.currentState
        // Ensure lifecycle is at least STARTED / RESUMED to avoid transition race conditions
        if (lifecycleState == null || lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            if (builder != null) {
                navigate(route, builder)
            } else {
                navigate(route) {
                    launchSingleTop = true
                }
            }
        } else {
            Log.w(TAG, "Skipped navigation to $route because lifecycle is $lifecycleState")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Prevented navigation crash to $route: ${e.message}", e)
    }
}

/**
 * Navigates to a top-level section (Drawer items, main tabs).
 * Pops up to the start destination (chat_list) saving state and launches as single top.
 */
fun NavController.navigateToTopLevel(route: String) {
    try {
        val currentRoute = currentBackStackEntry?.destination?.route
        if (currentRoute == route) {
            return
        }

        val lifecycleState = currentBackStackEntry?.lifecycle?.currentState
        if (lifecycleState == null || lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            navigate(route) {
                popUpTo(graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Prevented top-level navigation crash to $route: ${e.message}", e)
    }
}

/**
 * Safely pops the back stack, avoiding double-pop crashes and transitions on missing entries.
 */
fun NavController.popBackStackSafe(): Boolean {
    return try {
        val lifecycleState = currentBackStackEntry?.lifecycle?.currentState
        if (lifecycleState == null || lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            popBackStack()
        } else {
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Prevented popBackStack crash: ${e.message}", e)
        false
    }
}
