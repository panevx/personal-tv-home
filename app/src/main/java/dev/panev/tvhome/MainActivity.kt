package dev.panev.tvhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import dev.panev.tvhome.ui.LauncherRoot

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.onPackagesChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }

    override fun onStart() {
        super.onStart()
        setContent { LauncherRoot(vm) }
    }

    /**
     * While another app is in front, drop the whole UI with its wallpaper and artwork, roughly halving
     * what the launcher holds while you watch something. Just clearing the content isn't enough: the
     * view's last drawn frame still references the images. State lives in the ViewModel, so Home
     * comes back exactly where it was.
     */
    override fun onStop() {
        super.onStop()
        findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        vm.apps.clearArt()
        // The Java heap is tiny, so the GC that frees the images' native memory might not run for ages.
        window.decorView.postDelayed({ Runtime.getRuntime().gc() }, 1_000)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
