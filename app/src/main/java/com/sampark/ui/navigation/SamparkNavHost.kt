package com.sampark.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sampark.AppContainer
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import com.sampark.domain.Routes
import com.sampark.ui.RunViewModel
import com.sampark.ui.screens.CompletionScreen
import com.sampark.ui.screens.HomeScreen
import com.sampark.ui.screens.PermissionDeniedScreen
import com.sampark.ui.screens.PermissionExplainerScreen
import com.sampark.ui.screens.RollbackConfirmScreen
import com.sampark.ui.screens.RunProgressScreen
import com.sampark.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

@Composable
fun SamparkNavHost(container: AppContainer, startDestination: String) {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        coroutineScope.launch {
            container.appStatusRepository.setPermissionRequestedBefore(true)
            if (granted) {
                container.appStatusRepository.setDirection(Direction.TRANSLATE)
                container.appStatusRepository.setPhase(Phase.RUNNING)
                navController.navigate(Routes.RUN_TRANSLATE) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            } else {
                navController.navigate(Routes.PERMISSION_DENIED) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onStart = { navController.navigate(Routes.PERMISSION_EXPLAINER) })
        }

        composable(Routes.PERMISSION_EXPLAINER) {
            PermissionExplainerScreen(
                onContinue = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )
        }

        composable(Routes.PERMISSION_DENIED) {
            PermissionDeniedScreen(
                onGoToSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                onRetry = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )
        }

        composable(Routes.RUN_TRANSLATE) {
            val viewModel: RunViewModel = viewModel(
                factory = container.runViewModelFactory(Direction.TRANSLATE)
            )
            RunProgressScreenRoute(
                direction = Direction.TRANSLATE,
                viewModel = viewModel,
                onFinished = { navController.navigate(Routes.COMPLETION) { popUpTo(Routes.RUN_TRANSLATE) { inclusive = true } } }
            )
        }

        composable(Routes.RUN_ROLLBACK) {
            val viewModel: RunViewModel = viewModel(
                factory = container.runViewModelFactory(Direction.ROLLBACK)
            )
            RunProgressScreenRoute(
                direction = Direction.ROLLBACK,
                viewModel = viewModel,
                onFinished = { navController.navigate(Routes.COMPLETION) { popUpTo(Routes.RUN_ROLLBACK) { inclusive = true } } }
            )
        }

        composable(Routes.COMPLETION) {
            CompletionScreen(
                summaryText = "पूर्ण झाले",
                onOk = { navController.navigate(Routes.HOME) { popUpTo(Routes.COMPLETION) { inclusive = true } } }
            )
        }

        composable(Routes.HOME) {
            var direction by remember { mutableStateOf(Direction.NONE) }
            LaunchedEffect(Unit) {
                container.appStatusRepository.direction.collect { direction = it }
            }
            HomeScreen(
                direction = direction,
                onRollbackOrTranslateAgain = {
                    if (direction == Direction.TRANSLATE) {
                        navController.navigate(Routes.ROLLBACK_CONFIRM)
                    } else {
                        coroutineScope.launch {
                            container.appStatusRepository.setDirection(Direction.TRANSLATE)
                            container.appStatusRepository.setPhase(Phase.RUNNING)
                            navController.navigate(Routes.RUN_TRANSLATE) { popUpTo(Routes.HOME) { inclusive = true } }
                        }
                    }
                }
            )
        }

        composable(Routes.ROLLBACK_CONFIRM) {
            RollbackConfirmScreen(
                onConfirm = {
                    coroutineScope.launch {
                        container.appStatusRepository.setDirection(Direction.ROLLBACK)
                        container.appStatusRepository.setPhase(Phase.RUNNING)
                        navController.navigate(Routes.RUN_ROLLBACK) { popUpTo(Routes.ROLLBACK_CONFIRM) { inclusive = true } }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun RunProgressScreenRoute(direction: Direction, viewModel: RunViewModel, onFinished: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(uiState.done, uiState.total) {
        if (uiState.total > 0 && uiState.done == uiState.total && !uiState.isPaused) {
            onFinished()
        }
    }

    RunProgressScreen(
        direction = direction,
        uiState = uiState,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = {
            viewModel.cancel()
            onFinished()
        }
    )
}
