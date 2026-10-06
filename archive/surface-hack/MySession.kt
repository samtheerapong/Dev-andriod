package com.example.carbrowser

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/** One session per car connection; it starts straight into the browser screen. */
class MySession : Session() {

    override fun onCreateScreen(intent: Intent): Screen = BrowserScreen(carContext)
}
