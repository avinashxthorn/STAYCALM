package com.staycalm.focus

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class PodcastEpisode(val title: String, val audioUrl: String)
internal data class PodcastFeedPage(val episodes: List<PodcastEpisode>, val nextUrl: String? = null)

/** Read every audio enclosure supplied by an RSS/Atom page; display pagination is separate. */
internal object PodcastFeeds {
    private const val MAX_FEED_BYTES = 16 * 1024 * 1024
    fun load(feed: String): PodcastFeedPage {
        var link = StudyMediaLinks.secureAudioUrl(feed) ?: error("Enter a valid HTTPS podcast RSS link.")
        repeat(6) { hop ->
            val conn = URL(link).openConnection() as HttpsURLConnection
            conn.connectTimeout = 12_000; conn.readTimeout = 12_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            try {
                if (conn.responseCode in listOf(301, 302, 303, 307, 308)) {
                    require(hop < 5) { "This feed redirects too many times." }
                    link = resolve(link, conn.getHeaderField("Location")) ?: error("This feed redirected to an unsupported link.")
                } else {
                    require(conn.responseCode in 200..299) { "The podcast publisher could not provide this feed. Try again later." }
                    val bytes = ByteArrayOutputStream()
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(32_768)
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            require(bytes.size() + count <= MAX_FEED_BYTES) { "This feed is too large to load. Try its paginated RSS link." }
                            bytes.write(buffer, 0, count)
                        }
                    }
                    return parse(ByteArrayInputStream(bytes.toByteArray()), link)
                }
            } finally { conn.disconnect() }
        }
        error("This feed could not be loaded.")
    }
    private fun resolve(base: String, candidate: String?): String? = candidate?.let {
        runCatching { StudyMediaLinks.secureAudioUrl(URI(base).resolve(it).toString()) }.getOrNull()
    }
    internal fun parse(input: InputStream, base: String): PodcastFeedPage {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // No document declarations or external entities are processed.
        parser.setInput(input, null)
        val episodes = linkedMapOf<String, PodcastEpisode>()
        var itemDepth = -1
        var title = ""
        var enclosure: String? = null
        var next: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':')?.lowercase().orEmpty()
            if (event == XmlPullParser.START_TAG) {
                when (name) {
                    "item", "entry" -> { itemDepth = parser.depth; title = ""; enclosure = null }
                    "title" -> if (itemDepth >= 0 && parser.depth == itemDepth + 1) title = parser.nextText().take(240)
                    "enclosure", "content", "link" -> {
                        val url = parser.getAttributeValue(null, "url") ?: parser.getAttributeValue(null, "href")
                        val mime = parser.getAttributeValue(null, "type").orEmpty()
                        val rel = parser.getAttributeValue(null, "rel").orEmpty()
                        if (itemDepth >= 0 && (mime.startsWith("audio/") ||
                                (name == "enclosure" || rel == "enclosure") && mime.isBlank()))
                            enclosure = resolve(base, url) ?: enclosure
                        if (itemDepth < 0 && name == "link" && rel == "next") next = resolve(base, url)
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.depth == itemDepth && name in listOf("item", "entry")) {
                enclosure?.let { episodes.putIfAbsent(it, PodcastEpisode(title.ifBlank { "Podcast episode" }, it)) }
                itemDepth = -1
            }
            event = parser.next()
        }
        return PodcastFeedPage(episodes.values.toList(), next)
    }
}
