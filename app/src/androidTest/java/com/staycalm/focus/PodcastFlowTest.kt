package com.staycalm.focus

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PodcastFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val episodes = (1..25).map { PodcastEpisode("Episode $it", "https://example.org/$it.mp3") }
    @Before fun reset() {
        ui.runOnUiThread {
            val context = ui.activity
            (context.application as AccountTestApplication).identity = "podcast-test"
            AccountData.finishLogin(context)
            context.getSharedPreferences("study_media_${AccountData.root(context).name}", 0).edit().clear().commit()
            SoundService.clearPodcast(context)
        }
    }
    private fun hub(loader: suspend (String) -> PodcastFeedPage) {
        ui.runOnUiThread { ui.activity.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FocusMediaHub(false, false, {}, loader)
                }
            }
        } }
    }
    private fun find() {
        ui.onNodeWithTag("rss-link").performScrollTo().performTextInput("https://example.org/feed.xml")
        ui.onNodeWithText("Find episodes").performScrollTo().performClick()
    }
    @Test fun readsAllFeedEpisodesDeduplicatesAndFindsNextPage() {
        val items = (1..35).joinToString("") { "<item><title>Episode $it</title><enclosure url=\"$it.mp3\" type=\"audio/mpeg\"/></item>" }
        val xml = """<rss xmlns:atom="http://www.w3.org/2005/Atom"><channel><atom:link rel="next" href="page2.xml"/>$items<item><title>Duplicate</title><enclosure url="1.mp3" type="audio/mpeg"/></item><item><title>Video</title><enclosure url="video.mp4" type="video/mp4"/></item></channel></rss>"""
        val page = PodcastFeeds.parse(xml.byteInputStream(), "https://example.org/feed.xml")
        assertEquals(35, page.episodes.size)
        assertEquals("Episode 35", page.episodes.last().title)
        assertEquals("https://example.org/35.mp3", page.episodes.last().audioUrl)
        assertEquals("https://example.org/page2.xml", page.nextUrl)
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Atom episode</title><link rel="enclosure" type="audio/mpeg" href="episode.mp3"/></entry></feed>"""
        assertEquals("Atom episode", PodcastFeeds.parse(atom.byteInputStream(), "https://example.org/feed").episodes.single().title)
    }
    @Test fun showsTenAtATimeAndClearRemovesResultsPlayerAndSavedLink() {
        hub { PodcastFeedPage(episodes) }
        find()
        ui.onNodeWithText("Episode 10").assertExists()
        ui.onNodeWithText("Episode 11").assertDoesNotExist()
        ui.onNodeWithTag("more-episodes").performScrollTo().performClick()
        ui.onNodeWithText("Episode 20").assertExists()
        ui.onNodeWithText("Episode 21").assertDoesNotExist()
        ui.onNodeWithTag("more-episodes").performScrollTo().performClick()
        ui.onNodeWithText("Episode 25").assertExists()
        ui.onNodeWithTag("more-episodes").assertDoesNotExist()
        ui.runOnUiThread { AudioPlayback.state = AudioStatus(id = "podcast", title = "Playing private episode") }
        ui.onNodeWithText("Playing private episode").assertExists()
        ui.onNodeWithTag("clear-rss").performScrollTo().performClick()
        ui.onNodeWithTag("rss-link").assertTextEquals("Podcast RSS feed (https://…)", "")
        ui.onNodeWithText("Episode 1").assertDoesNotExist()
        ui.onNodeWithTag("podcast-player").assertDoesNotExist()
        assertFalse(ui.activity.getSharedPreferences("study_media_${AccountData.root(ui.activity).name}", 0).contains("rss_link"))
    }
    @Test fun clearingDuringFetchRejectsLateResults() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        hub { withContext(NonCancellable) { started.complete(Unit); release.await(); PodcastFeedPage(episodes) } }
        find()
        ui.waitUntil(5_000) { started.isCompleted }
        ui.onNodeWithTag("clear-rss").performScrollTo().performClick()
        release.complete(Unit)
        ui.waitForIdle()
        ui.onNodeWithText("Episode 1").assertDoesNotExist()
        ui.onNodeWithText("Loading episodes…").assertDoesNotExist()
        ui.onNodeWithTag("more-episodes").assertDoesNotExist()
    }
    @Test fun clearYoutubeRemovesInvalidLinkAndError() {
        hub { PodcastFeedPage(emptyList()) }
        ui.onNodeWithTag("youtube-link").performScrollTo().performTextInput("invalid")
        ui.onNodeWithText("Show video player").performScrollTo().performClick()
        ui.onNodeWithText("Paste an HTTPS YouTube video link (not a channel or playlist).").assertExists()
        ui.onNodeWithTag("clear-youtube").performScrollTo().performClick()
        ui.onNodeWithText("Paste an HTTPS YouTube video link (not a channel or playlist).").assertDoesNotExist()
        ui.onNodeWithTag("youtube-link").assertTextEquals("Paste a YouTube video or podcast link", "")
    }
    @Test fun playerNamesEpisodeAndSendsTransportCommands() {
        val commands = mutableListOf<String>()
        ui.runOnUiThread { ui.activity.setContent { MaterialTheme {
            PodcastPlayerControls(AudioStatus(id = "podcast", playing = true, duration = 300_000,
                position = 60_000, title = "Science today")) { command, _ -> commands += command }
        } } }
        ui.onNodeWithText("Science today").assertIsDisplayed()
        ui.onNodeWithContentDescription("Back 30 seconds").performClick()
        ui.onNodeWithContentDescription("Pause podcast").performClick()
        ui.onNodeWithContentDescription("Forward 30 seconds").performClick()
        ui.onNodeWithContentDescription("Stop podcast").performClick()
        assertEquals(listOf("rewind", "pause", "forward", "stop"), commands)
    }
}
