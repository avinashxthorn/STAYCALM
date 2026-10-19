package com.staycalm.focus

import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import java.net.URI
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Xml
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.lang.ref.WeakReference

/**
 * The official YouTube player is visible in the Focus screen only. Never download,
 * extract, split, hide or background-play YouTube audiovisual media.
 */
internal object StudyMediaLinks {
    /** Standard shared YouTube links only. Reject fake domains and non-video pages. */
    fun youtubeId(input: String): String? {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val id = when (host) {
            "youtu.be" -> parts.firstOrNull()
            "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com" ->
                when (parts.firstOrNull()) {
                    "watch" -> uri.rawQuery.orEmpty().split('&')
                        .firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                    "shorts", "live", "embed" -> parts.getOrNull(1)
                    else -> null
                }
            else -> null
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    /** HTTPS RSS/audio streams only; no extracted YouTube URLs. */
    fun secureAudioUrl(input: String): String? {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.host.equals("localhost", true) || uri.host == "127.0.0.1")
            return null
        return uri.toASCIIString()
    }
}

internal data class PodcastEpisode(val title: String, val audioUrl: String)

/** On-demand public RSS audio enclosures. Only HTTPS podcast feeds/audio; no scraping YouTube. */
internal object PodcastFeeds {
    fun load(feed: String): List<PodcastEpisode> {
        val link = StudyMediaLinks.secureAudioUrl(feed) ?: error("Enter a valid HTTPS podcast RSS link.")
        val conn = URL(link).openConnection() as HttpsURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
        try {
            val episodes = mutableListOf<PodcastEpisode>()
            conn.inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var inside = false
                var depth = 0
                var title = ""
                var enclosure: String? = null
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && episodes.size < 20) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name.substringAfter(':').lowercase()) {
                            "item", "entry" -> {
                                inside = true; depth = parser.depth
                                title = ""; enclosure = null
                            }
                            "title" -> if (inside && parser.depth == depth + 1)
                                title = parser.nextText().take(120)
                            "enclosure", "content", "link" -> if (inside) {
                                val url = parser.getAttributeValue(null, "url")
                                    ?: parser.getAttributeValue(null, "href")
                                val mime = parser.getAttributeValue(null, "type").orEmpty()
                                val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                if ((mime.startsWith("audio/") || parser.name == "enclosure" ||
                                    rel == "enclosure") && url != null)
                                    enclosure = StudyMediaLinks.secureAudioUrl(url)
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG &&
                        inside && parser.depth == depth &&
                        parser.name.substringAfter(':').lowercase() in listOf("item", "entry")) {
                        if (!enclosure.isNullOrBlank())
                            episodes.add(PodcastEpisode(title.ifBlank { "Podcast episode" }, enclosure!!))
                        inside = false
                    }
                    event = parser.next()
                }
            }
            return episodes
        } finally { conn.disconnect() }
    }
}

/**
 * Never keep the YouTube player running unseen. On app background blank its WebView;
 * on return reload the visible official embed, requiring the user to press Play again.
 */
internal object StudyVideoVisibility {
    private var active: WeakReference<WebView>? = null
    private var restore: (() -> Unit)? = null

    fun attach(web: WebView, reload: () -> Unit) {
        active = WeakReference(web)
        restore = reload
    }
    fun background() {
        active?.get()?.let { web ->
            web.onPause()
            web.loadUrl("about:blank")
        }
    }
    fun foreground() {
        val web = active?.get() ?: return
        restore?.invoke()
        web.onResume()
    }
    fun detach(web: WebView) {
        if (active?.get() === web) { active = null; restore = null }
        web.stopLoading()
        web.loadUrl("about:blank")
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }
}

@Composable
internal fun FocusMediaHub(focusMode: Boolean, sessionActive: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("study_media", Context.MODE_PRIVATE) }
    var youtubeText by remember { mutableStateOf(prefs.getString("youtube_link", "").orEmpty()) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var showMiniYouTube by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var feedText by remember { mutableStateOf(prefs.getString("rss_link", "").orEmpty()) }
    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var fetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val audio = AudioPlayback.state
    fun playPodcast(episode: PodcastEpisode) {
        val intent = Intent(context, SoundService::class.java)
            .setAction("play_podcast")
            .putExtra("podcast_url", episode.audioUrl)
            .putExtra("podcast_title", episode.title)
        runCatching { context.startForegroundService(intent) }
            .onFailure { feedError = "Android could not start podcast playback. Try again." }
    }

    Surface(shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, lightRose)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Icon(Icons.Rounded.OndemandVideo, null, tint = roseInk)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("WATCH & LISTEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = roseInk)
                    Text("Study videos and background podcasts", fontWeight = FontWeight.Bold, color = plumInk)
                }
            }
            Text("Search YouTube and watch videos without leaving the Focus timer. No link needed.",
                fontSize = 12.sp, color = mutedInk)
            Button(onClick = {
                // Never leave an embedded YouTube video playing behind the mini browser.
                playingId = null
                showMiniYouTube = true
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Search, null)
                Spacer(Modifier.width(9.dp))
                Text("Open mini YouTube · Search & Watch")
            }
            Text("Or paste a specific video link below:", fontSize = 12.sp, color = mutedInk)
            Text("When you load a video, the official YouTube player connects to YouTube and may share playback/device data. It does not access your local planner.",
                fontSize = 11.sp, color = mutedInk)
            OutlinedTextField(
                value = youtubeText,
                onValueChange = { youtubeText = it.take(400) },
                label = { Text("Paste a YouTube video or podcast link") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = youtubeText.isNotBlank(),
                onClick = {
                    val id = StudyMediaLinks.youtubeId(youtubeText)
                    if (id == null) videoError = "Paste an HTTPS YouTube video link (not a channel or playlist)."
                    else {
                        videoError = null
                        prefs.edit().putString("youtube_link", youtubeText).apply()
                        playingId = id
                    }
                }) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Show video player") }
            if (videoError != null) Text(videoError.orEmpty(), color = roseInk, fontSize = 12.sp)
            val id = playingId
            if (id != null) {
                key(id) {
                    val web = remember(id) {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                // The embedded player may navigate its subframes freely.
                                // Keep the top-level STAYCALM view from becoming an
                                // unrestricted browser that bypasses the YouTube blocker.
                                override fun shouldOverrideUrlLoading(
                                    view: WebView, request: WebResourceRequest
                                ): Boolean {
                                    if (!request.isForMainFrame) return false
                                    val uri = request.url
                                    return !(uri.scheme == "https" &&
                                        (uri.host == "com.staycalm.focus" ||
                                            uri.host == "www.youtube.com" &&
                                            uri.path.orEmpty().startsWith("/embed/")))
                                }
                            }
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            setBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    }
                    val embed = remember(id) {
                        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"></head><body style="margin:0;background:#000"><iframe width="100%" height="100%" style="border:0;position:absolute;inset:0" src="https://www.youtube.com/embed/$id?playsinline=1" title="YouTube official player" allow="encrypted-media;picture-in-picture;fullscreen" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe></body></html>"""
                    }
                    val load = {
                        // HTTPS base URL provides a Referer, as required by YouTube's embeds.
                        web.loadDataWithBaseURL("https://com.staycalm.focus/", embed,
                            "text/html", "UTF-8", null)
                    }
                    DisposableEffect(web) {
                        StudyVideoVisibility.attach(web, load)
                        load()
                        onDispose { StudyVideoVisibility.detach(web) }
                    }
                    AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().height(220.dp))
                }
                OutlinedButton(onClick = { playingId = null }) { Text("Close YouTube player") }
                Text("YouTube video/audio plays only while the player is visible. Leaving STAYCALM pauses it.",
                    fontSize = 11.sp, color = mutedInk)
            }
            HorizontalDivider()
            Text("BACKGROUND PODCASTS · RSS AUDIO", fontWeight = FontWeight.Bold, color = roseInk,
                fontSize = 11.sp)
            Text("For listening with the screen locked, paste a public podcast RSS feed. Podcast audio uses its publisher's HTTPS enclosure—not YouTube audio.",
                fontSize = 12.sp, color = mutedInk)
            OutlinedTextField(
                value = feedText, onValueChange = { feedText = it.take(700) },
                label = { Text("Podcast RSS feed (https://…)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(enabled = !fetching && feedText.isNotBlank(), onClick = {
                fetching = true; feedError = null; episodes = emptyList()
                prefs.edit().putString("rss_link", feedText).apply()
                scope.launch {
                    try {
                        episodes = withContext(Dispatchers.IO) { PodcastFeeds.load(feedText) }
                        if (episodes.isEmpty()) feedError = "No public HTTPS audio episodes were found in this feed."
                    } catch (_: Exception) {
                        feedError = "Could not read that RSS feed. Check the link and your connection."
                    } finally { fetching = false }
                }
            }) { Text(if (fetching) "Loading episodes…" else "Find episodes") }
            if (feedError != null) Text(feedError.orEmpty(), fontSize = 12.sp, color = roseInk)
            episodes.forEach { episode ->
                Row(Modifier.fillMaxWidth().clickable { playPodcast(episode) }.padding(vertical = 7.dp)) {
                    Icon(Icons.Rounded.Headphones, null, tint = roseInk)
                    Spacer(Modifier.width(10.dp))
                    Text(episode.title, modifier = Modifier.weight(1f), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, color = plumInk, fontSize = 13.sp)
                    Icon(Icons.Rounded.PlayArrow, "Play ${episode.title}", tint = roseInk)
                }
            }
            if (audio.id == "podcast") {
                Text(if (audio.loading) "Loading podcast…" else if (audio.playing)
                    "Podcast playing in the background" else "Podcast paused",
                    color = roseInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (audio.error != null) Text(audio.error, color = roseInk, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        val action = if (audio.playing) "pause" else "play"
                        context.startService(Intent(context, SoundService::class.java).setAction(action))
                    }) { Text(if (audio.playing) "Pause podcast" else "Resume podcast") }
                    TextButton(onClick = {
                        context.startService(Intent(context, SoundService::class.java).setAction("stop"))
                    }) { Text("Stop") }
                }
            }
            if (!focusMode || !sessionActive)
                Text("Start a focus session to listen while studying. Playback uses the Android media notification.",
                    fontSize = 11.sp, color = mutedInk)
        }
    }
    if (showMiniYouTube) MiniYouTubeBrowser(onDismiss = { showMiniYouTube = false })
}
