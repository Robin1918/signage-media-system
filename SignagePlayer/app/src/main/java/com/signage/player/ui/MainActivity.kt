package com.signage.player.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.signage.player.R
import com.signage.player.service.FileServerService
import com.signage.player.service.MediaScannerService
import com.signage.player.utils.MediaFileManager
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvFileCount: TextView
    private lateinit var tvCurrentFile: TextView
    private lateinit var playerFragment: PlayerFragment

    private val handler = Handler(Looper.getMainLooper())
    private val mediaRefreshRunnable = object : Runnable {
        override fun run() {
            refreshMediaInfo()
            handler.postDelayed(this, 5000)
        }
    }

    private val mediaUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == FileServerService.ACTION_MEDIA_UPDATED) {
                refreshMediaInfo()
                playerFragment.reloadMedia()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupFullscreen()
        initViews()
        requestPermissions()
        startServices()
        displayIpAddress()

        playerFragment = supportFragmentManager.findFragmentById(R.id.playerContainer) as? PlayerFragment
            ?: PlayerFragment().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.playerContainer, it)
                    .commit()
            }

        handler.post(mediaRefreshRunnable)
    }

    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvFileCount = findViewById(R.id.tvFileCount)
        tvCurrentFile = findViewById(R.id.tvCurrentFile)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        }
    }

    private fun startServices() {
        val serverIntent = Intent(this, FileServerService::class.java)
        startService(serverIntent)

        val scanIntent = Intent(this, MediaScannerService::class.java)
        startService(scanIntent)
    }

    private fun displayIpAddress() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        tvIpAddress.text = "Upload to: http://$ip:8080"
        tvStatus.text = "● LIVE"
    }

    private fun refreshMediaInfo() {
        val files = MediaFileManager.getMediaFiles(this)
        tvFileCount.text = "${files.size} files"
    }

    fun updateCurrentFile(name: String) {
        tvCurrentFile.text = name
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mediaUpdateReceiver, IntentFilter(FileServerService.ACTION_MEDIA_UPDATED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mediaUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(mediaRefreshRunnable)
    }
}
