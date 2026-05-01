package com.signage.uploader.network

import android.content.Context
import android.net.Uri
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UploadManager {

    fun test(url: String): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 4000; c.requestMethod = "GET"
        val code = c.responseCode; c.disconnect(); code == 200
    } catch (e: Exception) { false }

    fun upload(context: Context, serverUrl: String, uri: Uri, fileName: String): Boolean {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return false
            val boundary = "Boundary${System.currentTimeMillis()}"
            val CRLF = "\r\n"
            val url = URL("$serverUrl/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true; conn.doInput = true
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            val dos = DataOutputStream(conn.outputStream)
            dos.writeBytes("--$boundary$CRLF")
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$CRLF")
            dos.writeBytes("Content-Type: application/octet-stream$CRLF$CRLF")
            dos.write(bytes)
            dos.writeBytes("$CRLF--$boundary--$CRLF")
            dos.flush(); dos.close()
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}
