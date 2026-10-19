package com.staycalm.focus

import org.junit.Assert.*
import org.junit.Test

class StudyMediaLinksTest {
    @Test fun standardYoutubeVideoLinksAreAccepted() {
        val id = "M7lc1UVf-VE"
        assertEquals(id, StudyMediaLinks.youtubeId("https://www.youtube.com/watch?v=$id&t=2"))
        assertEquals(id, StudyMediaLinks.youtubeId("https://youtu.be/$id"))
        assertEquals(id, StudyMediaLinks.youtubeId("https://m.youtube.com/watch?v=$id"))
        assertEquals(id, StudyMediaLinks.youtubeId("https://www.youtube.com/shorts/$id"))
        assertEquals(id, StudyMediaLinks.youtubeId("https://youtube.com/live/$id"))
        assertEquals(id, StudyMediaLinks.youtubeId("https://www.youtube-nocookie.com/embed/$id"))
    }
    @Test fun fakeDomainsPlaylistsAndNonHttpsAreRejected() {
        assertNull(StudyMediaLinks.youtubeId("http://youtube.com/watch?v=M7lc1UVf-VE"))
        assertNull(StudyMediaLinks.youtubeId("https://youtube.com.evil.org/watch?v=M7lc1UVf-VE"))
        assertNull(StudyMediaLinks.youtubeId("https://www.youtube.com/playlist?list=123"))
        assertNull(StudyMediaLinks.youtubeId("https://www.youtube.com/watch?v=bad"))
        assertNull(StudyMediaLinks.youtubeId("javascript:alert(1)"))
        assertNull(StudyMediaLinks.youtubeId("https://www.youtube.com/@somepodcast"))
    }
    @Test fun onlySecurePodcastAudioLinksAreAccepted() {
        assertEquals("https://example.org/episode.mp3",
            StudyMediaLinks.secureAudioUrl("https://example.org/episode.mp3"))
        assertNull(StudyMediaLinks.secureAudioUrl("http://example.org/episode.mp3"))
        assertNull(StudyMediaLinks.secureAudioUrl("https://user:pass@example.org/episode.mp3"))
        assertNull(StudyMediaLinks.secureAudioUrl("file:///sdcard/episode.mp3"))
        assertNull(StudyMediaLinks.secureAudioUrl("https://localhost/podcast.xml"))
    }
    @Test fun miniYoutubeSearchUsesOfficialMobileResultsAndEncodesQuery() {
        assertEquals("https://m.youtube.com/", MiniYouTubeNavigation.HOME)
        assertEquals("https://m.youtube.com/results?search_query=biology+lecture+%26+podcast",
            MiniYouTubeNavigation.searchUrl("  biology lecture & podcast  "))
    }

    @Test fun miniYoutubeNavigationAcceptsOnlyOfficialYoutubeHosts() {
        for(url in listOf(
            "https://m.youtube.com/",
            "https://m.youtube.com/results?search_query=study",
            "https://www.youtube.com/watch?v=M7lc1UVf-VE",
            "https://consent.youtube.com/"
        )) assertTrue(MiniYouTubeNavigation.isYouTubeDestination(url))

        for(url in listOf(
            "http://youtube.com/watch?v=M7lc1UVf-VE",
            "https://youtube.com.evil.example/watch?v=M7lc1UVf-VE",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "https://notyoutube.com/",
            "https://name:password@youtube.com/"
        )) assertFalse(MiniYouTubeNavigation.isYouTubeDestination(url))
    }

}
