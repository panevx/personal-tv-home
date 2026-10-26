package dev.panev.tvhome.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/** Only exists so the system lets us read active media sessions. */
class MediaListenerService : NotificationListenerService()

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    val position: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val duration: Long,
    val pkg: String,
) {
    fun livePosition(now: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) position + ((now - positionUpdatedAt) * speed).toLong() else position
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }
}

class MediaRepository(private val context: Context, private val scope: CoroutineScope) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var started = false
    private var artUri: String? = null
    private var uriArt: Bitmap? = null

    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    var hasAccess by mutableStateOf(false)
        private set

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        choose(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
            if (state?.state != PlaybackState.STATE_PLAYING) refreshSessions()
        }
        override fun onSessionDestroyed() = refreshSessions()
    }

    /** Safe to call repeatedly; picks up access as soon as it's granted. */
    fun start() {
        if (started) {
            refreshSessions()
            return
        }
        try {
            val sessions = manager.getActiveSessions(listener)
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            started = true
            hasAccess = true
            choose(sessions)
        } catch (_: SecurityException) {
            hasAccess = false
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    private fun refreshSessions() {
        runCatching { choose(manager.getActiveSessions(listener)) }
    }

    private fun choose(sessions: List<MediaController>) {
        val pick = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.sessionToken == controller?.sessionToken }
            ?: sessions.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = pick
            pick?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || meta == null || title.isNullOrBlank()) {
            nowPlaying = null
            return
        }
        val state = c.playbackState
        val embedded = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val uri = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        if (embedded == null && uri != null && uri != artUri) loadArt(uri)

        nowPlaying = NowPlaying(
            title = title,
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
            album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            art = embedded ?: uriArt.takeIf { uri != null && uri == artUri },
            playing = state?.state == PlaybackState.STATE_PLAYING,
            position = state?.position ?: 0L,
            positionUpdatedAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it != 0f } ?: 1f,
            duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            pkg = c.packageName,
        )
    }

    private fun loadArt(uri: String) {
        artUri = uri
        uriArt = null
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = Uri.parse(uri)
                    val stream = if (parsed.scheme == "http" || parsed.scheme == "https") {
                        URL(uri).openStream()
                    } else {
                        context.contentResolver.openInputStream(parsed)
                    }
                    stream?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (artUri == uri) {
                uriArt = bmp
                publish()
            }
        }
    }
}
