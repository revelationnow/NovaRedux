package io.github.revelationnow.novaredux.connect

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button

// What this app is for, what it sends and to whom: opened from the gear on
// its App info page, or from NovaRedux's search settings.
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<Button>(R.id.about_source).setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_PAGE))) }
            finish()
        }
        findViewById<Button>(R.id.about_done).setOnClickListener { finish() }
    }

    private companion object {
        const val SOURCE_PAGE = "https://github.com/revelationnow/NovaRedux/tree/main/connect"
    }
}
