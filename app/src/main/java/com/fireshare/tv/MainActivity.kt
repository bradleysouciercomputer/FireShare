package com.fireshare.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var mediaServer: MediaServer? = null
    private var accessPin by mutableStateOf("")
    private var localAddress by mutableStateOf<String?>(null)
    private var isSharing by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Starting your private sharing session…")
    private var mediaFiles by mutableStateOf<List<File>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(15, 20, 28)
        window.navigationBarColor = android.graphics.Color.rgb(15, 20, 28)

        setContent {
            FireShareTheme {
                FireShareScreen(
                    mediaFiles = mediaFiles,
                    isSharing = isSharing,
                    statusMessage = statusMessage,
                    address = localAddress?.let { "http://" + it + ":" + MediaServer.PORT },
                    accessPin = accessPin,
                    onToggleSharing = {
                        if (mediaServer == null) startSharing() else stopSharing()
                    },
                    onRefresh = { refreshLibrary() },
                    onOpen = { file ->
                        startActivity(
                            android.content.Intent(this, MediaViewerActivity::class.java)
                                .putExtra(MediaViewerActivity.EXTRA_NAME, file.name)
                        )
                    },
                    onDelete = { file -> deleteMediaFile(file) }
                )
            }
        }

        startSharing()
        refreshLibrary()
    }

    private fun startSharing() {
        if (mediaServer != null) return
        try {
            if (accessPin.isBlank()) {
                accessPin = (SecureRandom().nextInt(900000) + 100000).toString()
            }
            val server = MediaServer(this, accessPin)
            server.start(5000, false)
            mediaServer = server
            localAddress = findLocalIpv4()
            isSharing = true
            statusMessage = if (localAddress == null) {
                "Sharing is running. Connect this TV to Wi-Fi to show its address."
            } else {
                "Ready for uploads • Keep FireShare open while transferring"
            }
        } catch (error: Exception) {
            isSharing = false
            statusMessage = "Could not start sharing: " + (error.message ?: "unknown error")
            android.util.Log.e("FireShare", "Could not start sharing server", error)
        }
    }

    private fun stopSharing() {
        mediaServer?.stop()
        mediaServer = null
        isSharing = false
        statusMessage = "Sharing is paused. Start sharing when you want to receive files."
    }

    private fun refreshLibrary() {
        val directory = File(filesDir, "media").apply { mkdirs() }
        mediaFiles = directory.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun deleteMediaFile(file: File): Boolean {
        return try {
            val mediaDirectory = File(filesDir, "media").canonicalFile
            val target = File(mediaDirectory, file.name).canonicalFile
            val deleted = target.parentFile == mediaDirectory && target.isFile && target.delete()
            refreshLibrary()
            deleted
        } catch (error: Exception) {
            android.util.Log.e("FireShare", "Could not delete " + file.name, error)
            false
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

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    override fun onDestroy() {
        mediaServer?.stop()
        mediaServer = null
        super.onDestroy()
    }
}

@Composable
fun FireShareTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { dynamicDarkColorScheme(context) }.getOrElse { FireShareDarkColors }
    } else {
        FireShareDarkColors
    }
    MaterialTheme(colorScheme = colors) {
        // TV controls are visually sized for a remote. Avoid Material's invisible
        // 48dp touch-target expansion so focus outlines wrap the visible control.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            content()
        }
    }
}

private val TvControlShape: Shape = RoundedCornerShape(50)

private val FireShareDarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0B305F),
    primaryContainer = Color(0xFF294B78),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFB8C8E2),
    onSecondary = Color(0xFF23354F),
    secondaryContainer = Color(0xFF344A67),
    onSecondaryContainer = Color(0xFFD5E3FF),
    background = Color(0xFF0F141C),
    onBackground = Color(0xFFE1E6EF),
    surface = Color(0xFF0F141C),
    onSurface = Color(0xFFE1E6EF),
    surfaceVariant = Color(0xFF252D3A),
    onSurfaceVariant = Color(0xFFC2CAD7),
    outline = Color(0xFF8994A5),
    outlineVariant = Color(0xFF414B5A),
    inverseSurface = Color(0xFFE1E6EF),
    inverseOnSurface = Color(0xFF252D3A),
    inversePrimary = Color(0xFF365F98),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private enum class MediaFilter { ALL, PHOTOS, VIDEOS }

@Composable
private fun FireShareScreen(
    mediaFiles: List<File>,
    isSharing: Boolean,
    statusMessage: String,
    address: String?,
    accessPin: String,
    onToggleSharing: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (File) -> Unit,
    onDelete: (File) -> Boolean
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    val cancelFocusRequester = remember { FocusRequester() }

    val visibleFiles = remember(mediaFiles, filter) {
        mediaFiles.filter { file ->
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !isVideoFile(file)
                MediaFilter.VIDEOS -> isVideoFile(file)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(18.dp)
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderBar(isSharing = isSharing)

            UploadPanel(
                isSharing = isSharing,
                statusMessage = statusMessage,
                address = address,
                accessPin = accessPin,
                onToggleSharing = onToggleSharing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your library",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Media saved on this Fire TV",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = mediaFiles.size.toString() + if (mediaFiles.size == 1) " ITEM" else " ITEMS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filter == MediaFilter.ALL,
                    onClick = { filter = MediaFilter.ALL },
                    label = { Text("All media") },
                    modifier = Modifier.tvFocusRing(TvControlShape),
                    shape = TvControlShape
                )
                FilterChip(
                    selected = filter == MediaFilter.PHOTOS,
                    onClick = { filter = MediaFilter.PHOTOS },
                    label = { Text("Photos") },
                    modifier = Modifier.tvFocusRing(TvControlShape),
                    shape = TvControlShape
                )
                FilterChip(
                    selected = filter == MediaFilter.VIDEOS,
                    onClick = { filter = MediaFilter.VIDEOS },
                    label = { Text("Videos") },
                    modifier = Modifier.tvFocusRing(TvControlShape),
                    shape = TvControlShape
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = visibleFiles.size.toString() + " shown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (visibleFiles.isEmpty()) {
                EmptyLibrary(
                    isFiltered = filter != MediaFilter.ALL,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    visibleFiles.chunked(4).forEach { rowFiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowFiles.forEach { file ->
                                MediaGridCard(
                                    file = file,
                                    onOpen = { onOpen(file) },
                                    onDelete = { pendingDelete = file },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - rowFiles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        val file = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete this item?") },
            text = {
                Text(
                    "“" + file.name + "” will be permanently removed from this Fire TV."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = onDelete(file)
                        pendingDelete = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (deleted) "Item deleted from your TV"
                                else "Could not delete this item"
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.tvFocusRing(TvControlShape),
                    shape = TvControlShape
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier
                        .focusRequester(cancelFocusRequester)
                        .tvFocusRing(TvControlShape),
                    shape = TvControlShape
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
        LaunchedEffect(pendingDelete) {
            cancelFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun HeaderBar(isSharing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(17.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Tv,
                    contentDescription = null,
                    modifier = Modifier.size(27.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column {
            Text(
                text = "FireShare",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your media, on the big screen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            shape = TvControlShape,
            color = if (isSharing) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSharing) Icons.Rounded.CheckCircle else Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = if (isSharing) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (isSharing) "Sharing is on" else "Sharing is off",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSharing) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UploadPanel(
    isSharing: Boolean,
    statusMessage: String,
    address: String?,
    accessPin: String,
    onToggleSharing: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(address) {
        address?.let { createQrBitmap(it) }?.asImageBitmap()
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Send it to the big screen",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Upload photos and videos from a phone or computer on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OPEN IN YOUR BROWSER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = address ?: "Connect Fire TV to Wi-Fi",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(18.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TV PIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (accessPin.isBlank()) "------" else accessPin,
                                style = MaterialTheme.typography.titleLarge,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleSharing,
                        modifier = Modifier
                            .height(42.dp)
                            .tvFocusRing(TvControlShape),
                        shape = TvControlShape,
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        Icon(
                            imageVector = if (isSharing) Icons.Rounded.StopCircle else Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSharing) "Stop sharing" else "Start sharing")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .height(42.dp)
                            .tvFocusRing(TvControlShape),
                        shape = TvControlShape,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("Refresh")
                    }
                }
            }

            Column(
                modifier = Modifier.width(176.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    modifier = Modifier.size(146.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Scan to open the FireShare upload page",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(9.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = "SCAN TO CONNECT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(isFiltered: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFiltered) Icons.Rounded.Collections else Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(31.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isFiltered) "No matching media" else "Your library is ready for memories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = if (isFiltered) "Try another filter to see your files."
                else "Scan the QR code above to send photos and videos to this TV.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaGridCard(
    file: File,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnail by produceState<Bitmap?>(
        initialValue = null,
        key1 = file.absolutePath,
        key2 = file.lastModified()
    ) {
        value = withContext(Dispatchers.IO) { createMediaThumbnail(file) }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(206.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isVideoFile(file)) Icons.Rounded.Movie else Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(38.dp)
                    )
                }
                if (isVideoFile(file)) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = TvControlShape,
                        color = Color.Black.copy(alpha = 0.72f),
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Video",
                            modifier = Modifier.padding(5.dp).size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = (if (isVideoFile(file)) "VIDEO" else "PHOTO") + "  •  " + formatFileSize(file.length()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .tvFocusRing(TvControlShape),
                    shape = TvControlShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", fontSize = 12.sp)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusRing(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete " + file.name,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
internal fun Modifier.tvFocusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = shape
            ) else Modifier
        )
}

private fun createQrBitmap(value: String): Bitmap? {
    return try {
        val size = 320
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    } catch (error: Exception) {
        android.util.Log.w("FireShare", "Could not create QR code", error)
        null
    }
}

private fun createMediaThumbnail(file: File): Bitmap? {
    return try {
        if (isVideoFile(file)) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(
                    1_000_000,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            } finally {
                retriever.release()
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 520 || bounds.outHeight / sample > 360) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
    } catch (error: Exception) {
        android.util.Log.w("FireShare", "Could not create thumbnail for " + file.name, error)
        null
    }
}

private fun isVideoFile(file: File): Boolean {
    return file.extension.lowercase(Locale.US) in setOf(
        "mp4", "m4v", "mov", "mkv", "webm", "3gp", "mpeg", "mpg"
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> bytes.toString() + " B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
