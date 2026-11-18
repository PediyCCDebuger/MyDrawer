package com.pediycc.mydrawer

import android.app.Application
import com.pediycc.mydrawer.data.DebugLog

class MyDrawerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLog.log(this, "应用启动，versionName=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE}")
    }
}
