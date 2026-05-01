package com.signage.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.signage.player.utils.MediaFileManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileServerService : Service() {

    companion object {
        const val ACTION_MEDIA_UPDATED = "com.signage.player.MEDIA_UPDATED"
        private const val PORT = 8080
        private const val CHANNEL_ID = "signage_ch"
    }

    private var server: SignageServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification())
        server = SignageServer(PORT) { name, stream -> saveFile(name, stream) }
        try { server?.start() } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveFile(name: String, stream: InputStream) {
        val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val out = File(MediaFileManager.getMediaDirectory(this), safe)
        FileOutputStream(out).use { stream.copyTo(it) }
        sendBroadcast(Intent(ACTION_MEDIA_UPDATED))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Signage", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Signage Player")
            .setContentText("Upload server active on port 8080")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { server?.stop(); super.onDestroy() }
}

class SignageServer(port: Int, private val onFile: (String, InputStream) -> Unit) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> pageResponse()
            session.method == Method.POST && session.uri == "/upload" -> uploadResponse(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404")
        }
    }

    private fun uploadResponse(session: IHTTPSession): Response {
        return try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            val tmp = files["file"]
            val name = session.parameters["file"]?.firstOrNull()
                ?: "upload_${System.currentTimeMillis()}"
            if (tmp != null) {
                val f = File(tmp)
                onFile(name, f.inputStream())
                f.delete()
            }
            newFixedLengthResponse("OK")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    private fun pageResponse(): Response {
        val html = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Signage Upload</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:sans-serif;background:#111;color:#fff;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
.card{background:#1a1a1a;border-radius:16px;padding:28px;width:100%;max-width:420px;border:1px solid #333}
h1{font-size:22px;margin-bottom:6px}
p{color:#888;font-size:14px;margin-bottom:20px}
.drop{border:2px dashed #444;border-radius:10px;padding:36px;text-align:center;cursor:pointer;margin-bottom:14px;transition:.2s}
.drop:hover{border-color:#00d4ff;background:rgba(0,212,255,.05)}
input[type=file]{display:none}
.list{margin-bottom:14px;font-size:13px;color:#aaa}
.btn{width:100%;padding:14px;background:#00d4ff;color:#000;border:none;border-radius:10px;font-size:16px;font-weight:700;cursor:pointer}
.btn:disabled{opacity:.4}
.prog{margin-top:12px;display:none}
.bar{height:4px;background:#333;border-radius:2px;overflow:hidden}
.fill{height:100%;background:#00d4ff;width:0%;transition:.3s}
.msg{text-align:center;font-size:13px;color:#888;margin-top:8px}
</style></head><body>
<div class="card">
<h1>📡 Upload to TV</h1>
<p>Pick videos or photos — they play instantly</p>
<div class="drop" id="dz">
<div style="font-size:40px;margin-bottom:10px">🎬</div>
<div style="color:#666">Tap to pick files</div>
<input type="file" id="fi" multiple accept="video/*,image/*">
</div>
<div class="list" id="fl"></div>
<button class="btn" id="ub" disabled>Upload to TV</button>
<div class="prog" id="pc"><div class="bar"><div class="fill" id="pf"></div></div><div class="msg" id="sm"></div></div>
</div>
<script>
var dz=document.getElementById('dz'),fi=document.getElementById('fi'),fl=document.getElementById('fl'),
ub=document.getElementById('ub'),pc=document.getElementById('pc'),pf=document.getElementById('pf'),
sm=document.getElementById('sm'),sel=[];
dz.onclick=function(){fi.click()};
fi.onchange=function(){sel=[...sel,...Array.from(fi.files)];render()};
function render(){fl.innerHTML=sel.map(f=>'<div>'+f.name+'</div>').join('');ub.disabled=!sel.length}
ub.onclick=async function(){
ub.disabled=true;pc.style.display='block';
for(var i=0;i<sel.length;i++){
sm.textContent='Uploading '+(i+1)+'/'+sel.length+': '+sel[i].name;
pf.style.width=((i/sel.length)*100)+'%';
var fd=new FormData();fd.append('file',sel[i],sel[i].name);
try{await fetch('/upload',{method:'POST',body:fd})}catch(e){}
}
pf.style.width='100%';sm.textContent='✓ Done! Files playing on TV.';sel=[];fl.innerHTML='';ub.disabled=true;
};
</script></body></html>"""
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
