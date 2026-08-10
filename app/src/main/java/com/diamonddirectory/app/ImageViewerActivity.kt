package com.diamonddirectory.app

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/** Full-screen viewer that shows a saved image at original size with pinch-zoom. */
class ImageViewerActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val path = intent.getStringExtra("path")
        if (path.isNullOrEmpty()) { finish(); return }

        val web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        web.settings.apply {
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            @Suppress("SetJavaScriptEnabled")
            allowFileAccess = true
        }
        val html = "<html><head><meta name='viewport' " +
            "content='width=device-width,initial-scale=1,maximum-scale=6'></head>" +
            "<body style='margin:0;background:#000;display:flex;align-items:center;" +
            "justify-content:center;min-height:100vh'>" +
            "<img src='file://$path' style='max-width:100%;height:auto'/></body></html>"
        web.loadDataWithBaseURL("file:///", html, "text/html", "utf-8", null)
        setContentView(web)
        title = "ફોટો"
    }
}
