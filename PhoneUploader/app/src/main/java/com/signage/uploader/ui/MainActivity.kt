package com.signage.uploader.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.signage.uploader.R
import com.signage.uploader.network.UploadManager
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var etTvAddress: EditText
    private lateinit var btnSave: Button
    private lateinit var btnPickFiles: Button
    private lateinit var btnUpload: Button
    private lateinit var lvFiles: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvConnStatus: TextView

    private lateinit var prefs: SharedPreferences
    private val selectedUris = mutableListOf<Uri>()
    private var fileAdapter: ArrayAdapter<String>? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            selectedUris.clear()
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) selectedUris.add(clip.getItemAt(i).uri)
            } ?: data?.data?.let { selectedUris.add(it) }
            refreshFileList()
            btnUpload.isEnabled = selectedUris.isNotEmpty()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uploader)

        prefs = getSharedPreferences("signage_prefs", MODE_PRIVATE)

        initViews()
        requestPermissions()

        // Restore saved IP
        etTvAddress.setText(prefs.getString("tv_ip", "192.168.1."))
    }

    private fun initViews() {
        etTvAddress = findViewById(R.id.etTvAddress)
        btnSave = findViewById(R.id.btnSave)
        btnPickFiles = findViewById(R.id.btnPickFiles)
        btnUpload = findViewById(R.id.btnUpload)
        lvFiles = findViewById(R.id.lvFiles)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        tvStatus = findViewById(R.id.tvStatus)
        tvConnStatus = findViewById(R.id.tvConnStatus)

        fileAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        lvFiles.adapter = fileAdapter

        btnSave.setOnClickListener {
            val ip = etTvAddress.text.toString().trim()
            prefs.edit().putString("tv_ip", ip).apply()
            testConnection(ip)
        }

        btnPickFiles.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "image/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickFilesLauncher.launch(Intent.createChooser(intent, "Select Media"))
        }

        btnUpload.setOnClickListener { startUpload() }
        btnUpload.isEnabled = false
    }

    private fun testConnection(ip: String) {
        tvConnStatus.text = "Testing..."
        tvConnStatus.setTextColor(getColor(android.R.color.darker_gray))
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                UploadManager.testConnection("http://$ip:8080")
            }
            if (ok) {
                tvConnStatus.text = "✓ Connected to TV"
                tvConnStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                tvConnStatus.text = "✗ Cannot reach TV — check IP and WiFi"
                tvConnStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun refreshFileList() {
        val names = selectedUris.map { uri ->
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else uri.lastPathSegment ?: "file"
            } ?: uri.lastPathSegment ?: "file"
        }
        fileAdapter?.clear()
        fileAdapter?.addAll(names)
        tvStatus.text = "${selectedUris.size} file(s) selected"
    }

    private fun startUpload() {
        val ip = etTvAddress.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter TV IP address first", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedUris.isEmpty()) return

        btnUpload.isEnabled = false
        btnPickFiles.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = selectedUris.size
        progressBar.progress = 0

        scope.launch {
            var success = 0
            selectedUris.forEachIndexed { index, uri ->
                tvProgress.text = "Uploading ${index + 1}/${selectedUris.size}..."
                val fileName = fileAdapter?.getItem(index) ?: "file_$index"
                val ok = withContext(Dispatchers.IO) {
                    UploadManager.uploadFile(
                        context = this@MainActivity,
                        serverUrl = "http://$ip:8080",
                        uri = uri,
                        fileName = fileName
                    )
                }
                if (ok) success++
                progressBar.progress = index + 1
            }

            tvProgress.text = "Done! $success/${selectedUris.size} uploaded"
            tvStatus.text = if (success == selectedUris.size) "✓ All files sent to TV!" else "⚠ Some files failed"
            progressBar.visibility = View.GONE
            btnPickFiles.isEnabled = true
            selectedUris.clear()
            fileAdapter?.clear()
            btnUpload.isEnabled = false
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
