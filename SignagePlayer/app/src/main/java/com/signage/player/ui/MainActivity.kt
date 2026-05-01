package com.signage.player.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.signage.player.R
import com.signage.player.service.FileServerService
import com.signage.player.utils.MediaFileManager

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvFileCount: TextView
    private lateinit var tvCurrentFile: TextView

    private var playerFragment: PlayerFragment? = null
    private val handler = Handler(Looper.getMainLooper())

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshInfo()
            playerFragment?.reloadMedia()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        tvStatus = findViewById(R.id.tvStatus)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvFileCount = findViewById(R.id.tvFileCount)
        tvCurrentFile = findViewById(R.id.tvCurrentFile)

        requestStoragePermissions()
        startService(Intent(this, FileServerService::class.java))

        playerFragment = PlayerFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.playerContainer, playerFragment!!)
            .commit()

        showIpAddress()
        handler.postDelayed({ refreshInfo() }, 1000)
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun showIpAddress() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
            tvIpAddress.text = "Upload: http://$ip:8080"
        } catch (e: Exception) {
            tvIpAddress.text = "Check WiFi IP:8080"
        }
    }

    private fun refreshInfo() {
        val files = MediaFileManager.getMediaFiles(this)
        tvFileCount.text = "${files.size} files"
    }

    fun updateCurrentFile(name: String) {
        tvCurrentFile.text = name
    }

    private fun requestStoragePermissions() {
        val perms = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mediaReceiver, IntentFilter(FileServerService.ACTION_MEDIA_UPDATED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mediaReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
