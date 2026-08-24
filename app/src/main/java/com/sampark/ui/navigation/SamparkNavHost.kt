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
import com.sampark.data.status.AppStatusRepository
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
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it }
        coroutineScope.launch {
            container.appStatusRepository.setPermissionRequestedBefore(true)
            if (granted) {
                container.appStatusRepository.setDirection(Direction.TRANSLATE)
                container.appStatusRepository.setPhase(Phase.RUNNING)
                navController.navigate(Routes.RUN_TRANSLATE) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            } else {
                navController.navigate(Routes.PERMISSION_DENIED) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    val requestContactsPermissions: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        )
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onStart = { navController.navigate(Routes.PERMISSION_EXPLAINER) })
        }

        composable(Routes.PERMISSION_EXPLAINER) {
            PermissionExplainerScreen(onContinue = requestContactsPermissions)
        }

        composable(Routes.PERMISSION_DENIED) {
            PermissionDeniedScreen(
                onGoToSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                onRetry = requestContactsPermissions
            )
        }

        composable(Routes.RUN_TRANSLATE) {
            val viewModel: RunViewModel = viewModel(
                factory = container.runViewModelFactory(Direction.TRANSLATE)
            )
            RunProgressScreenRoute(
                direction = Direction.TRANSLATE,
                viewModel = viewModel,
                appStatusRepository = container.appStatusRepository,
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
                appStatusRepository = container.appStatusRepository,
                onFinished = { navController.navigate(Routes.COMPLETION) { popUpTo(Routes.RUN_ROLLBACK) { inclusive = true } } }
            )
        }

        composable(Routes.COMPLETION) {
            CompletionScreen(
                summaryText = "पूर्ण झाले",
                onOk = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.HOME) {
            var direction by remember { mutableStateOf(Direction.NONE) }
            var directionLoaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                container.appStatusRepository.direction.collect {
                    direction = it
                    directionLoaded = true
                }
            }
            if (directionLoaded) {
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
private fun RunProgressScreenRoute(
    direction: Direction,
    viewModel: RunViewModel,
    appStatusRepository: AppStatusRepository,
    onFinished: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val phase by appStatusRepository.phase.collectAsState(initial = Phase.RUNNING)

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(phase) {
        if (phase == Phase.COMPLETED) {
            onFinished()
        }
    }

    RunProgressScreen(
        direction = direction,
        uiState = uiState,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = viewModel::cancel
    )
}
