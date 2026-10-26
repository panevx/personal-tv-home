package dev.panev.tvhome.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.panev.tvhome.LauncherViewModel
import dev.panev.tvhome.data.NowPlaying
import dev.panev.tvhome.ui.*
import dev.panev.tvhome.ui.components.*
import dev.panev.tvhome.ui.glass.glass
import kotlinx.coroutines.delay

private val ArtShape = RoundedCornerShape(22.dp)

/** The album art itself is the backdrop (blurred by the root); this screen floats the details over it. */
@Composable
fun PlayerScreen(vm: LauncherViewModel) {
    val np = vm.media.nowPlaying
    val playFocus = rememberEntryFocus(np == null)

    if (np == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                Icons.Rounded.MusicNote, "Nothing playing",
                if (vm.media.hasAccess) "Start music in any app and it appears here."
                else "Allow notification access for Personal TV Home in Appearance to see your music here.",
                action = "Back to Home", actionIcon = Icons.Rounded.Home, focusRequester = playFocus,
            ) { vm.goHome() }
        }
        return
    }

    val appName = vm.apps.find(np.pkg)?.label
    Row(
        Modifier.fillMaxSize().padding(horizontal = 84.dp, vertical = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = remember(np.art) { np.art?.asImageBitmap() }
        val motion = LocalMotion.current
        AnimatedContent(art, transitionSpec = { fadeIn(motion.fade(500)) togetherWith fadeOut(motion.fade(300)) }, label = "art") { img ->
            Box(
                Modifier.size(300.dp).shadow(36.dp, ArtShape).clip(ArtShape).glass(ArtShape),
                contentAlignment = Alignment.Center,
            ) {
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Ico(Icons.Rounded.MusicNote, size = 72.dp, tint = Ink.Secondary)
            }
        }
        Spacer(Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Txt(if (appName != null) "Now playing in $appName" else "Now playing", Type.Caption)
            Spacer(Modifier.height(10.dp))
            Txt(np.title, Type.Display, maxLines = 2)
            if (np.artist.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Txt(np.artist, Type.Headline.copy(fontSize = 21.sp, fontWeight = Type.Body.fontWeight), color = Ink.Secondary)
            }
            if (np.album.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Txt(np.album, Type.Caption)
            }
            Spacer(Modifier.height(30.dp))
            Progress(np)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.SkipPrevious, { vm.media.previous() }, size = 56.dp, iconSize = 26.dp)
                GlassIconButton(
                    if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    { vm.media.playPause() }, size = 72.dp, iconSize = 34.dp, focusRequester = playFocus,
                )
                GlassIconButton(Icons.Rounded.SkipNext, { vm.media.next() }, size = 56.dp, iconSize = 26.dp)
            }
        }
    }
}

@Composable
private fun Progress(np: NowPlaying) {
    if (np.duration <= 0) return
    val position by produceState(np.livePosition(), np) {
        while (true) {
            value = np.livePosition(SystemClock.elapsedRealtime())
            delay(500)
        }
    }
    val fraction = (position.toFloat() / np.duration).coerceIn(0f, 1f)
    Column(Modifier.widthIn(max = 520.dp)) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(PillShape).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(PillShape).background(Ink.Primary))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Txt(formatTime(position), Type.Numeric)
            Spacer(Modifier.weight(1f))
            Txt("-" + formatTime(np.duration - position), Type.Numeric)
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
