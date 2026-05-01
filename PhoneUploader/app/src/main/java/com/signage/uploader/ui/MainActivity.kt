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

    private lateinit var etAddress: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvConnStatus: TextView
    private lateinit var btnPick: Button
    private lateinit var tvSelected: TextView
    private lateinit var lvFiles: ListView
    private lateinit var btnUpload: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView

    private lateinit var prefs: SharedPreferences
    private val uris = mutableListOf<Uri>()
    private val names = mutableListOf<String>()
    private var adapter: ArrayAdapter<String>? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            uris.clear(); names.clear()
            result.data?.clipData?.let { c ->
                for (i in 0 until c.itemCount) addUri(c.getItemAt(i).uri)
            } ?: result.data?.data?.let { addUri(it) }
            adapter?.notifyDataSetChanged()
            tvSelected.text = "${uris.size} file(s) selected"
            btnUpload.isEnabled = uris.isNotEmpty()
        }
    }

    private fun addUri(uri: Uri) {
        uris.add(uri)
        val name = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "file_${uris.size}"
        names.add(name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        etAddress = findViewById(R.id.etTvAddress)
        btnConnect = findViewById(R.id.btnConnect)
        tvConnStatus = findViewById(R.id.tvConnStatus)
        btnPick = findViewById(R.id.btnPickFiles)
        tvSelected = findViewById(R.id.tvSelected)
        lvFiles = findViewById(R.id.lvFiles)
        btnUpload = findViewById(R.id.btnUpload)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)

        etAddress.setText(prefs.getString("ip", "192.168.1."))

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        lvFiles.adapter = adapter

        btnConnect.setOnClickListener {
            val ip = etAddress.text.toString().trim()
            prefs.edit().putString("ip", ip).apply()
            testConn(ip)
        }

        btnPick.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "image/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            picker.launch(Intent.createChooser(i, "Pick Media"))
        }

        btnUpload.setOnClickListener { doUpload() }

        requestPerms()
    }

    private fun testConn(ip: String) {
        tvConnStatus.text = "Testing..."
        scope.launch {
            val ok = withContext(Dispatchers.IO) { UploadManager.test("http://$ip:8080") }
            tvConnStatus.text = if (ok) "Connected to TV" else "Cannot reach TV - check IP and WiFi"
            tvConnStatus.setTextColor(if (ok) 0xFF00CC66.toInt() else 0xFFFF4444.toInt())
        }
    }

    private fun doUpload() {
        val ip = etAddress.text.toString().trim()
        if (ip.isEmpty()) { Toast.makeText(this, "Enter TV IP first", Toast.LENGTH_SHORT).show(); return }
        btnUpload.isEnabled = false
        btnPick.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = uris.size

        scope.launch {
            var ok = 0
            uris.forEachIndexed { i, uri ->
                tvProgress.text = "Uploading ${i+1}/${uris.size}: ${names[i]}"
                progressBar.progress = i + 1
                val success = withContext(Dispatchers.IO) {
                    UploadManager.upload(this@MainActivity, "http://$ip:8080", uri, names[i])
                }
                if (success) ok++
            }
            tvProgress.text = if (ok == uris.size) "Done! All files sent to TV!" else "$ok/${uris.size} uploaded"
            progressBar.visibility = View.GONE
            btnPick.isEnabled = true
            uris.clear(); names.clear()
            adapter?.notifyDataSetChanged()
            btnUpload.isEnabled = false
        }
    }

    private fun requestPerms() {
        val p = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.READ_MEDIA_VIDEO)
            p.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        ActivityCompat.requestPermissions(this, p.toTypedArray(), 1)
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
