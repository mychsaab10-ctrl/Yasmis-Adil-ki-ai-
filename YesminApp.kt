package com.yesmin.ai

import android.app.Application

class YesminApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Yesmin.init(this)
    }
}
