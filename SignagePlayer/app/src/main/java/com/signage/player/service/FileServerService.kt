package com.signage.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.signage.player.R
import com.signage.player.utils.MediaFileManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileServerService : Service() {

    companion object {
        const val ACTION_MEDIA_UPDATED = "com.signage.player.MEDIA_UPDATED"
        private const val PORT = 8080
        private const val CHANNEL_ID = "signage_server"
        private const val TAG = "FileServerService"
    }

    private var server: SignageHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        server = SignageHttpServer(PORT) { fileName, inputStream ->
            saveFile(fileName, inputStream)
        }
        try {
            server?.start()
            Log.d(TAG, "Server started on port $PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
        }
    }

    private fun saveFile(fileName: String, inputStream: InputStream) {
        val dir = MediaFileManager.getMediaDirectory(this)
        val outFile = File(dir, sanitizeFileName(fileName))
        FileOutputStream(outFile).use { fos ->
            inputStream.copyTo(fos)
        }
        Log.d(TAG, "Saved: ${outFile.absolutePath}")
        sendBroadcast(Intent(ACTION_MEDIA_UPDATED))
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Signage Server", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Signage Player")
            .setContentText("Upload server running on port $PORT")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }
}

class SignageHttpServer(
    port: Int,
    private val onFileReceived: (String, InputStream) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> serveUploadPage()
            session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun serveUploadPage(): Response {
        val html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Signage Upload</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, sans-serif; background: #0a0a0a; color: #fff; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; }
  .card { background: #1a1a1a; border-radius: 20px; padding: 32px; width: 100%; max-width: 440px; border: 1px solid #333; }
  h1 { font-size: 24px; font-weight: 700; margin-bottom: 8px; }
  p { color: #888; font-size: 14px; margin-bottom: 24px; }
  .drop-zone { border: 2px dashed #444; border-radius: 12px; padding: 40px 20px; text-align: center; cursor: pointer; transition: all 0.2s; margin-bottom: 16px; }
  .drop-zone:hover, .drop-zone.drag-over { border-color: #00d4ff; background: rgba(0,212,255,0.05); }
  .drop-zone-icon { font-size: 48px; margin-bottom: 12px; }
  .drop-zone-text { color: #666; font-size: 14px; }
  .drop-zone-text span { color: #00d4ff; }
  input[type=file] { display: none; }
  .file-list { margin-bottom: 16px; max-height: 200px; overflow-y: auto; }
  .file-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: #222; border-radius: 8px; margin-bottom: 6px; font-size: 13px; }
  .file-icon { font-size: 18px; }
  .file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .file-size { color: #666; font-size: 11px; }
  .btn { width: 100%; padding: 14px; background: #00d4ff; color: #000; border: none; border-radius: 12px; font-size: 16px; font-weight: 700; cursor: pointer; transition: opacity 0.2s; }
  .btn:hover { opacity: 0.9; }
  .btn:disabled { opacity: 0.4; cursor: not-allowed; }
  .progress { margin-top: 16px; display: none; }
  .progress-bar { height: 4px; background: #333; border-radius: 2px; overflow: hidden; }
  .progress-fill { height: 100%; background: #00d4ff; width: 0%; transition: width 0.3s; }
  .status { text-align: center; font-size: 13px; color: #888; margin-top: 10px; }
  .success { color: #00ff88; }
  .error { color: #ff4444; }
</style>
</head>
<body>
<div class="card">
  <h1>📡 Signage Upload</h1>
  <p>Upload videos & photos to your TV display</p>
  <div class="drop-zone" id="dropZone">
    <div class="drop-zone-icon">🎬</div>
    <div class="drop-zone-text">Drop files here or <span>browse</span></div>
    <input type="file" id="fileInput" multiple accept="video/*,image/*">
  </div>
  <div class="file-list" id="fileList"></div>
  <button class="btn" id="uploadBtn" disabled>Upload to TV</button>
  <div class="progress" id="progressContainer">
    <div class="progress-bar"><div class="progress-fill" id="progressFill"></div></div>
    <div class="status" id="statusText">Uploading...</div>
  </div>
</div>
<script>
  const dropZone = document.getElementById('dropZone');
  const fileInput = document.getElementById('fileInput');
  const fileList = document.getElementById('fileList');
  const uploadBtn = document.getElementById('uploadBtn');
  const progressContainer = document.getElementById('progressContainer');
  const progressFill = document.getElementById('progressFill');
  const statusText = document.getElementById('statusText');
  let selectedFiles = [];

  dropZone.addEventListener('click', () => fileInput.click());
  dropZone.addEventListener('dragover', e => { e.preventDefault(); dropZone.classList.add('drag-over'); });
  dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
  dropZone.addEventListener('drop', e => { e.preventDefault(); dropZone.classList.remove('drag-over'); addFiles(e.dataTransfer.files); });
  fileInput.addEventListener('change', e => addFiles(e.target.files));

  function addFiles(files) {
    selectedFiles = [...selectedFiles, ...Array.from(files)];
    renderFileList();
    uploadBtn.disabled = selectedFiles.length === 0;
  }

  function renderFileList() {
    fileList.innerHTML = selectedFiles.map((f, i) => {
      const icon = f.type.startsWith('video') ? '🎬' : '🖼️';
      const size = f.size < 1024*1024 ? (f.size/1024).toFixed(1)+'KB' : (f.size/1024/1024).toFixed(1)+'MB';
      return '<div class="file-item"><span class="file-icon">'+icon+'</span><span class="file-name">'+f.name+'</span><span class="file-size">'+size+'</span></div>';
    }).join('');
  }

  uploadBtn.addEventListener('click', async () => {
    uploadBtn.disabled = true;
    progressContainer.style.display = 'block';
    for (let i = 0; i < selectedFiles.length; i++) {
      const f = selectedFiles[i];
      statusText.textContent = 'Uploading ' + (i+1) + '/' + selectedFiles.length + ': ' + f.name;
      progressFill.style.width = ((i / selectedFiles.length) * 100) + '%';
      const fd = new FormData();
      fd.append('file', f, f.name);
      try {
        await fetch('/upload', { method: 'POST', body: fd });
      } catch(e) {}
    }
    progressFill.style.width = '100%';
    statusText.className = 'status success';
    statusText.textContent = '✓ All files uploaded to TV!';
    selectedFiles = [];
    fileList.innerHTML = '';
    uploadBtn.disabled = true;
  });
</script>
</body>
</html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        return try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            val tmpFilePath = files["file"]
            val fileName = session.parameters["file"]?.firstOrNull() ?: "upload_${System.currentTimeMillis()}"

            if (tmpFilePath != null) {
                val tmpFile = File(tmpFilePath)
                onFileReceived(fileName, tmpFile.inputStream())
                tmpFile.delete()
            }

            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }
}
