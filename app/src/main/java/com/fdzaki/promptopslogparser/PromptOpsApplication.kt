package com.fdzaki.promptopslogparser

import android.app.Application
import com.fdzaki.promptopslogparser.diagnostics.CrashHandler

class PromptOpsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext, defaultHandler))
    }
}
