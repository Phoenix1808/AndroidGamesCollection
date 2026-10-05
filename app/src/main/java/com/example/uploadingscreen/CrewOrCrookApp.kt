package com.example.uploadingscreen

import android.app.Application
import com.example.uploadingscreen.network.SessionManager

class CrewOrCrookApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // lets network code send the user to login/lobby from any screen
        SessionManager.attach(this)
    }
}
