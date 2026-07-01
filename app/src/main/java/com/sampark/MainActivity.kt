package com.sampark

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var repo: ContactsRepository
    private lateinit var transliterator: Transliterator
    private lateinit var setupMachine: SetupStateMachine

    private val allScreenIds = listOf(
        R.id.screenWelcome, R.id.screenPermissionDenied, R.id.screenScanning,
        R.id.screenScanComplete, R.id.screenActive, R.id.screenInactive
    )

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            setupMachine.onPermissionsGranted()
            startScan()
        } else {
            setupMachine.onPermissionDenied()
            render(AppScreen.SETUP)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = SharedPreferencesAppPreferences(this)
        repo = AndroidContactsRepository(this)
        transliterator = IcuTransliterator()
        setupMachine = SetupStateMachine(prefs)

        setupClickListeners()

        val screen = resolveAppScreen(prefs, repo)
        when (screen) {
            AppScreen.SCANNING -> startScan()
            else -> render(screen)
        }
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            setupMachine.onStartTapped()
            requestPermissions.launch(
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            )
        }

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            setupMachine.onRetryPermissions()
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            render(resolveAppScreen(prefs, repo))
        }

        findViewById<Button>(R.id.btnRollback).setOnClickListener {
            startRollback()
        }

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            startScan()
        }
    }

    private fun startScan() {
        prefs.scanInProgress = true
        showScreen(R.id.screenScanning)
        CoroutineScope(Dispatchers.IO).launch {
            val count = runScan(repo, transliterator)
            prefs.scanInProgress = false
            ScanWorker.enqueue(applicationContext)
            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.tvScanCount).text =
                    "$count नावे मराठीत बदलली"
                showScreen(R.id.screenScanComplete)
            }
        }
    }

    private fun startRollback() {
        showScreen(R.id.screenScanning)
        CoroutineScope(Dispatchers.IO).launch {
            runRollback(repo)
            ScanWorker.cancel(applicationContext)
            withContext(Dispatchers.Main) {
                render(resolveAppScreen(prefs, repo))
            }
        }
    }

    private fun render(screen: AppScreen) {
        val screenId = when (screen) {
            AppScreen.SETUP -> R.id.screenWelcome
            AppScreen.SCANNING -> R.id.screenScanning
            AppScreen.ACTIVE -> R.id.screenActive
            AppScreen.INACTIVE -> R.id.screenInactive
        }
        showScreen(screenId)
    }

    private fun showScreen(id: Int) {
        val root = findViewById<FrameLayout>(android.R.id.content)
            .getChildAt(0) as FrameLayout
        allScreenIds.forEach { screenId ->
            root.findViewById<View>(screenId)?.visibility =
                if (screenId == id) View.VISIBLE else View.GONE
        }
    }
}
