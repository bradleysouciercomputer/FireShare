package com.fireshare.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var addressText: TextView
    private lateinit var pinText: TextView
    private lateinit var qrImage: ImageView
    private lateinit var mediaGrid: GridLayout
    private lateinit var mediaCount: TextView
    private lateinit var serverButton: TextView

    private var mediaServer: MediaServer? = null
    private var accessPin = ""
    private var localAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 13, 23)
        window.navigationBarColor = Color.rgb(8, 13, 23)
        buildInterface()
        startSharing()
        refreshLibrary()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 13, 23))
            isFillViewport = true
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(38), dp(26), dp(38), dp(40))
        }
        scroll.addView(content)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "F"
            textSize = 28f
            setTextColor(Color.rgb(11, 20, 36))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.rgb(120, 169, 255))
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), 0, 0, 0)
        }
        brand.addView(label("FIRESHARE", 24f, Color.WHITE, true))
        brand.addView(label("Your media, on the big screen", 14f, 0xFF9EABC0.toInt()))
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("LOCAL WI-FI", 13f, 0xFF8AB4F8.toInt(), true))
        content.addView(header)
        addSpace(24)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26), dp(22), dp(26), dp(22))
            background = roundedBackground(0xFF14243B.toInt(), 22)
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        left.addView(label("Send photos & videos to your TV", 29f, Color.WHITE, true))
        addInto(left, dp(12))
        left.addView(label(
            "Open the address below on a phone or computer connected to the same Wi-Fi network. Uploads are saved on this Fire TV.",
            16f,
            0xFFBCC8D8.toInt()
        ))
        addInto(left, dp(18))
        statusText = label("Starting your private sharing session…", 15f, 0xFF9FB4D2.toInt(), true)
        left.addView(statusText)
        addInto(left, dp(13))
        addressText = label("Finding your TV address…", 23f, 0xFF8AB4F8.toInt(), true)
        left.addView(addressText)
        addInto(left, dp(6))
        pinText = label("TV access PIN: ------", 17f, Color.WHITE, true)
        left.addView(pinText)
        addInto(left, dp(17))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serverButton = actionButton("Stop sharing") {
            if (mediaServer == null) startSharing() else stopSharing()
        }
        actions.addView(serverButton, LinearLayout.LayoutParams(dp(190), dp(52)))
        val refreshButton = actionButton("Refresh library") { refreshLibrary() }
        val refreshParams = LinearLayout.LayoutParams(dp(190), dp(52)).apply { leftMargin = dp(12) }
        actions.addView(refreshButton, refreshParams)
        left.addView(actions)
        hero.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val qrColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), 0, dp(10))
        }
        qrImage = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "Scan to open FireShare on your phone"
        }
        qrColumn.addView(qrImage, LinearLayout.LayoutParams(dp(210), dp(210)))
        addInto(qrColumn, dp(8))
        qrColumn.addView(label("SCAN TO CONNECT", 12f, 0xFFDCEBFF.toInt(), true))
        hero.addView(qrColumn, LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(hero)

        addSpace(28)
        val sectionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sectionHeader.addView(label("Your library", 24f, Color.WHITE, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mediaCount = label("0 ITEMS", 13f, 0xFF9EABC0.toInt(), true)
        sectionHeader.addView(mediaCount)
        content.addView(sectionHeader)
        addSpace(7)
        content.addView(label("Select any photo or video to open it. New uploads appear here automatically after you refresh.", 14f, 0xFF9EABC0.toInt()))
        addSpace(15)

        mediaGrid = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        content.addView(mediaGrid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        addSpace(24)
        val footer = label(
            "REMOTE TIP  •  Use the directional pad to move, Select to open, and Back to return.",
            13f,
            0xFF75859D.toInt()
        )
        content.addView(footer)
    }

    private fun startSharing() {
        if (mediaServer != null) return
        try {
            if (accessPin.isBlank()) accessPin = (SecureRandom().nextInt(900000) + 100000).toString()
            val server = MediaServer(this, accessPin)
            server.start(5000, false)
            mediaServer = server
            localAddress = findLocalIpv4()
            statusText.text = "Sharing is ON  •  Keep FireShare open while uploading"
            addressText.text = localAddress?.let { "http://$it:${MediaServer.PORT}" }
                ?: "Connect this TV to Wi-Fi"
            pinText.text = "TV access PIN: $accessPin"
            serverButton.text = "Stop sharing"
            updateQr()
        } catch (error: Exception) {
            statusText.text = "Could not start sharing: ${error.message ?: "unknown error"}"
            Toast.makeText(this, "Could not start the upload server.", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSharing() {
        mediaServer?.stop()
        mediaServer = null
        statusText.text = "Sharing is OFF  •  Start sharing to receive files"
        addressText.text = "Upload server stopped"
        qrImage.setImageDrawable(null)
        serverButton.text = "Start sharing"
    }

    private fun updateQr() {
        val ip = localAddress ?: return
        val url = "http://$ip:${MediaServer.PORT}"
        try {
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300)
            val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            for (x in 0 until 300) {
                for (y in 0 until 300) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            qrImage.setImageBitmap(bitmap)
        } catch (error: Exception) {
            android.util.Log.w("FireShare", "QR generation failed", error)
        }
    }

    private fun refreshLibrary() {
        if (!::mediaGrid.isInitialized) return
        val directory = File(filesDir, "media").apply { mkdirs() }
        val files = directory.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        mediaGrid.removeAllViews()
        mediaCount.text = "${files.size} ${if (files.size == 1) "ITEM" else "ITEMS"}"
        if (files.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(28), dp(24), dp(28))
                background = roundedBackground(0xFF111C2C.toInt(), 16)
            }
            empty.addView(label("Your library is waiting", 20f, Color.WHITE, true))
            addInto(empty, dp(7))
            empty.addView(label("Upload a few photos or videos from your phone or computer to see them here.", 14f, 0xFF9EABC0.toInt()))
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(0, 4)
            ).apply {
                width = 0
                columnSpec = GridLayout.spec(0, 4, 1f)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            mediaGrid.addView(empty, lp)
            return
        }

        files.forEachIndexed { index, file ->
            val tile = createMediaTile(file)
            val row = index / 4
            val column = index % 4
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = dp(184)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            mediaGrid.addView(tile, params)
        }
    }

    private fun createMediaTile(file: File): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = getDrawable(R.drawable.card_focus)
            isFocusable = true
            isClickable = true
            contentDescription = "Open ${file.name}"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, MediaViewerActivity::class.java)
                    .putExtra(MediaViewerActivity.EXTRA_NAME, file.name))
            }
        }
        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF0A111D.toInt())
            val bitmap = createThumbnail(file)
            if (bitmap != null) setImageBitmap(bitmap)
        }
        tile.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(123)
        ))
        val name = label(file.name, 13f, Color.WHITE, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(3), dp(8), dp(3), 0)
        }
        tile.addView(name, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val type = if (file.extension.lowercase(Locale.US) in VIDEO_EXTENSIONS) "VIDEO" else "PHOTO"
        tile.addView(label(type, 10f, 0xFF8AB4F8.toInt(), true).apply {
            setPadding(dp(3), dp(3), dp(3), 0)
        })
        return tile
    }

    private fun createThumbnail(file: File): Bitmap? {
        return try {
            if (file.extension.lowercase(Locale.US) in VIDEO_EXTENSIONS) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 520 || bounds.outHeight / sample > 360) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }
        } catch (error: Exception) {
            android.util.Log.w("FireShare", "Could not create thumbnail for ${file.name}", error)
            null
        }
    }

    private fun findLocalIpv4(): String? {
        return try {
            val addresses = NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filter { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.map { it.hostAddress }
                ?: emptyList()
            addresses.firstOrNull { it?.startsWith("192.168.") == true }
                ?: addresses.firstOrNull { it?.startsWith("10.") == true }
                ?: addresses.firstOrNull()
        } catch (error: Exception) {
            android.util.Log.w("FireShare", "Could not read local IP address", error)
            null
        }
    }

    private fun actionButton(title: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.card_focus)
            isFocusable = true
            isClickable = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { action() }
        }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun addSpace(height: Int) {
        content.addView(View(this), LinearLayout.LayoutParams(1, height))
    }

    private fun addInto(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, height))
    }

    private fun roundedBackground(color: Int, radius: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (::mediaGrid.isInitialized) refreshLibrary()
    }

    override fun onDestroy() {
        mediaServer?.stop()
        mediaServer = null
        super.onDestroy()
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp", "mpeg", "mpg")
    }
}
