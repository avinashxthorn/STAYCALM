package com.staycalm.focus

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URLEncoder

/**
 * Key-free, first-party YouTube mobile website rather than scraping or a fake
 * YouTube Data API. The user's search and watch requests go directly to YouTube.
 * This does not read pages, inject scripts, remove ads or extract media streams.
 */
internal object MiniYouTubeNavigation {
    const val HOME = "https://m.youtube.com/"

    fun searchUrl(query: String): String =
        "https://m.youtube.com/results?search_query=" +
            URLEncoder.encode(query.trim().take(180), "UTF-8")

    fun isYouTubeDestination(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true) || uri.userInfo != null) return false
        return uri.host?.lowercase() in setOf(
            "youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtu.be", "consent.youtube.com"
        )
    }
}

/**
 * Open a mini YouTube browser above the Focus page. Search and watch on
 * YouTube's own website; retain all player controls, ads and outbound links.
 * User must interact to play. Never allow hidden/background playback.
 */
@Composable
internal fun MiniYouTubeBrowser(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(MiniYouTubeNavigation.HOME) }
    var loading by remember { mutableStateOf(true) }

    val browser = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = WebChromeClient()
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    if (!request.isForMainFrame) return false
                    val url = request.url.toString()
                    if (MiniYouTubeNavigation.isYouTubeDestination(url)) return false
                    // Keep YouTube navigation in the mini browser but honor links
                    // to other sites by offering the normal Android handler.
                    // The YouTube page/player is never modified or obscured.
                    runCatching {
                        if (request.url.scheme == "https") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        } else {
                            error = "This link cannot be opened safely."
                        }
                    }.onFailure { error = "Android could not open this link." }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    loading = true
                    currentUrl = url
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                    currentUrl = url
                    canGoBack = view.canGoBack()
                }
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    fun submitSearch() {
        if (query.isBlank()) {
            error = "Type a video, podcast or topic to search."
            return
        }
        error = null
        browser.loadUrl(MiniYouTubeNavigation.searchUrl(query))
    }

    fun goBackOrDismiss() {
        if (browser.canGoBack()) browser.goBack() else onDismiss()
    }

    BackHandler { goBackOrDismiss() }
    DisposableEffect(browser) {
        val restore = {
            // Do not resume a video invisibly after returning from another app.
            // Return to search/home and let the user choose to play again.
            val destination = if (currentUrl.contains("/results?"))
                currentUrl else MiniYouTubeNavigation.HOME
            browser.loadUrl(destination)
        }
        StudyVideoVisibility.attach(browser, restore)
        browser.loadUrl(MiniYouTubeNavigation.HOME)
        onDispose { StudyVideoVisibility.detach(browser) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(.96f)
                .fillMaxHeight(.86f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, lightRose)
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { goBackOrDismiss() }) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = roseInk)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("mini YouTube", fontWeight = FontWeight.Bold, color = plumInk)
                        Text("Search and watch while your focus timer runs",
                            fontSize = 11.sp, color = mutedInk)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close mini YouTube", tint = roseInk)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(180) },
                        singleLine = true,
                        label = { Text("Search YouTube") },
                        placeholder = { Text("Study podcasts, lectures, music…") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(onClick = { submitSearch() }, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = roseInk)) {
                        Icon(Icons.Rounded.Search, "Search YouTube")
                    }
                }
                if (error != null) Text(error.orEmpty(), fontSize = 11.sp, color = roseInk)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = roseInk)
                AndroidView(
                    factory = { browser },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Text("YouTube's website opens here. Video playback stops when you close this window or leave STAYCALM; no background YouTube audio.",
                    fontSize = 11.sp, color = mutedInk)
            }
        }
    }
}
