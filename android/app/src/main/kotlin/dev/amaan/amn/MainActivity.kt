package dev.amaan.amn

import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    /** A widget tap can open a page directly; see PrayerWidget.EXTRA_ROUTE in amn_widgets. */
    override fun getInitialRoute(): String? = intent.getStringExtra("dev.amaan.amn.ROUTE") ?: super.getInitialRoute()
}
