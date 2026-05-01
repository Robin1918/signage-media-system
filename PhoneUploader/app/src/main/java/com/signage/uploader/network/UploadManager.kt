package com.signage.uploader.network

import android.content.Context
import android.net.Uri
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object UploadManager {

    fun testConnection(serverUrl: String): Boolean {
        return try {
            val url = URL(serverUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            false
        }
    }

    fun uploadFile(context: Context, serverUrl: String, uri: Uri, fileName: String): Boolean {
        var inputStream: InputStream? = null
        var conn: HttpURLConnection? = null
        var dos: DataOutputStream? = null

        return try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val fileBytes = inputStream.readBytes()

            val boundary = "----FormBoundary${System.currentTimeMillis()}"
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL("$serverUrl/upload")
            conn = url.openConnection() as HttpURLConnection
            conn.doInput = true
            conn.doOutput = true
            conn.useCaches = false
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            dos = DataOutputStream(conn.outputStream)

            // Write boundary + file header
            dos.writeBytes("$twoHyphens$boundary$lineEnd")
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd")
            dos.writeBytes("Content-Type: ${getMimeType(fileName)}$lineEnd")
            dos.writeBytes(lineEnd)

            // Write file bytes
            dos.write(fileBytes)
            dos.writeBytes(lineEnd)

            // Close boundary
            dos.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
            dos.flush()

            val responseCode = conn.responseCode
            responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            inputStream?.close()
            dos?.close()
            conn?.disconnect()
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
