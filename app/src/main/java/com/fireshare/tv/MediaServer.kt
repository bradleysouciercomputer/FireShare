package com.fireshare.tv

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class MediaServer(
    private val appContext: Context,
    private val accessPin: String,
    port: Int = PORT
) : NanoHTTPD("0.0.0.0", port) {

    private val mediaDirectory = File(appContext.filesDir, "media").apply { mkdirs() }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" -> uploadPage()
                session.method == Method.GET && session.uri == "/app.js" -> uploadScript()
                session.method == Method.GET && session.uri == "/health" ->
                    textResponse(Response.Status.OK, "FireShare is ready")
                session.method == Method.GET && session.uri == "/api/media" -> {
                    if (!authorized(session)) forbidden()
                    else mediaList()
                }
                session.method == Method.POST && session.uri == "/upload" -> upload(session)
                session.method == Method.DELETE && session.uri.startsWith("/api/media/") ->
                    deleteMedia(session)
                session.method == Method.GET && session.uri.startsWith("/media/") ->
                    serveMedia(session)
                else -> textResponse(Response.Status.NOT_FOUND, "Not found")
            }
        } catch (error: Exception) {
            android.util.Log.e("FireShareServer", "Request failed: ${session.method} ${session.uri}", error)
            textResponse(Response.Status.INTERNAL_ERROR, "The request could not be completed.")
        }
    }

    private fun authorized(session: IHTTPSession): Boolean {
        return session.parameters["pin"]?.firstOrNull() == accessPin
    }

    private fun upload(session: IHTTPSession): Response {
        if (!authorized(session)) return forbidden()

        val uploadedParts = HashMap<String, String>()
        try {
            session.parseBody(uploadedParts)
        } catch (error: Exception) {
            android.util.Log.w("FireShareServer", "Could not read upload body", error)
            return textResponse(Response.Status.BAD_REQUEST, "The upload could not be read.")
        }

        val tempPath = uploadedParts["file"] ?: uploadedParts.values.firstOrNull()
            ?: return textResponse(Response.Status.BAD_REQUEST, "Choose a file first.")
        val tempFile = File(tempPath)
        if (!tempFile.isFile) return textResponse(Response.Status.BAD_REQUEST, "Uploaded file was not found.")

        val originalName = session.parameters["file"]?.firstOrNull()
            ?: session.parameters["filename"]?.firstOrNull()
            ?: "upload"
        val safeName = safeFileName(originalName)
        val extension = safeName.substringAfterLast('.', "").lowercase(Locale.US)
        if (extension !in ALLOWED_EXTENSIONS) {
            tempFile.delete()
            return textResponse(
                Response.Status.BAD_REQUEST,
                "FireShare supports common photo and video files (JPG, PNG, GIF, WEBP, MP4, MOV, MKV and more)."
            )
        }

        val destination = uniqueDestination(safeName)
        return try {
            tempFile.copyTo(destination, overwrite = false)
            tempFile.delete()
            jsonResponse(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("name", destination.name)
                    .put("size", destination.length())
                    .toString()
            )
        } catch (error: Exception) {
            tempFile.delete()
            android.util.Log.e("FireShareServer", "Saving upload failed", error)
            textResponse(Response.Status.INTERNAL_ERROR, "Could not save this file.")
        }
    }

    private fun mediaList(): Response {
        val files = mediaDirectory.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        val result = JSONArray()
        files.forEach { file ->
            val extension = file.extension.lowercase(Locale.US)
            val isVideo = extension in VIDEO_EXTENSIONS
            result.put(
                JSONObject()
                    .put("name", file.name)
                    .put("size", file.length())
                    .put("modified", file.lastModified())
                    .put("type", if (isVideo) "video" else "image")
                    .put("url", "/media/${java.net.URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")}")
            )
        }
        return jsonResponse(Response.Status.OK, result.toString())
    }

    private fun deleteMedia(session: IHTTPSession): Response {
        if (!authorized(session)) return forbidden()
        val encodedName = session.uri.removePrefix("/api/media/")
        val decodedName = try {
            URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            return textResponse(Response.Status.BAD_REQUEST, "Invalid file name.")
        }
        val target = File(mediaDirectory, File(decodedName).name)
        if (!target.isFile || target.parentFile?.canonicalFile != mediaDirectory.canonicalFile) {
            return textResponse(Response.Status.NOT_FOUND, "File not found.")
        }
        if (!target.delete()) return textResponse(Response.Status.INTERNAL_ERROR, "Could not delete this file.")
        return jsonResponse(Response.Status.OK, JSONObject().put("ok", true).toString())
    }

    private fun serveMedia(session: IHTTPSession): Response {
        if (!authorized(session)) return forbidden()
        val encodedName = session.uri.removePrefix("/media/")
        val decodedName = try {
            URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            return textResponse(Response.Status.BAD_REQUEST, "Invalid file name.")
        }
        val file = File(mediaDirectory, File(decodedName).name)
        if (!file.isFile || file.parentFile?.canonicalFile != mediaDirectory.canonicalFile) {
            return textResponse(Response.Status.NOT_FOUND, "File not found.")
        }
        val response = newFixedLengthResponse(
            Response.Status.OK,
            getMimeTypeForFile(file.name),
            FileInputStream(file),
            file.length()
        )
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("X-Content-Type-Options", "nosniff")
        return response
    }

    private fun uploadPage(): Response {
        val html = appContext.assets.open("upload/index.html")
            .bufferedReader()
            .use { it.readText() }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun uploadScript(): Response {
        val script = appContext.assets.open("upload/app.js")
            .bufferedReader()
            .use { it.readText() }
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/javascript; charset=utf-8",
            script
        ).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    }

    private fun uniqueDestination(name: String): File {
        val base = name.substringBeforeLast('.', name).ifBlank { "upload" }
        val extension = name.substringAfterLast('.', "")
        var candidate = File(mediaDirectory, name)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(mediaDirectory, "${base}_${suffix}.$extension")
            suffix++
        }
        return candidate
    }

    private fun safeFileName(input: String): String {
        val leaf = input.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = leaf
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .trim('.')
            .take(110)
        return cleaned.ifBlank { "upload.jpg" }
    }

    private fun forbidden() = jsonResponse(
        Response.Status.FORBIDDEN,
        JSONObject().put("error", "Incorrect or missing TV access PIN.").toString()
    )

    private fun textResponse(status: Response.IStatus, message: String): Response {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", message)
    }

    private fun jsonResponse(status: Response.IStatus, json: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", json)
    }

    companion object {
        const val PORT = 8765
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp", "mpeg", "mpg")
        private val ALLOWED_EXTENSIONS = VIDEO_EXTENSIONS + setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"
        )
    }
}
