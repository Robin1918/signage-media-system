package com.signage.player.service

import android.app.Service
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import com.signage.player.utils.MediaFileManager

class MediaScannerService : Service() {
    private var observer: FileObserver? = null

    override fun onCreate() {
        super.onCreate()
        val dir = MediaFileManager.getMediaDirectory(this)
        observer = object : FileObserver(dir.absolutePath, CREATE or DELETE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) sendBroadcast(Intent(FileServerService.ACTION_MEDIA_UPDATED))
            }
        }
        observer?.startWatching()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { observer?.stopWatching(); super.onDestroy() }
}
