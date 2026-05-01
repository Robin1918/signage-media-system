package com.signage.player.service

import android.app.Service
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import com.signage.player.utils.MediaFileManager

class MediaScannerService : Service() {

    private var fileObserver: FileObserver? = null

    override fun onCreate() {
        super.onCreate()
        val mediaDir = MediaFileManager.getMediaDirectory(this)
        fileObserver = object : FileObserver(mediaDir.absolutePath, CREATE or DELETE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) {
                    Log.d("MediaScanner", "File change detected: $path")
                    sendBroadcast(Intent(FileServerService.ACTION_MEDIA_UPDATED))
                }
            }
        }
        fileObserver?.startWatching()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fileObserver?.stopWatching()
        super.onDestroy()
    }
}
