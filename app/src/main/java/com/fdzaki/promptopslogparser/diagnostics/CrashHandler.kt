package com.fdzaki.promptopslogparser.diagnostics

import android.content.Context

/**
 * Wraps the system's default uncaught-exception handler so every crash is written to
 * [AppDiagnostics] first, then handed off to the default handler so the OS still shows
 * its normal "app has stopped" behavior — this handler never swallows the crash.
 */
class CrashHandler(
    private val appContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            AppDiagnostics.recordCrash(appContext, thread, throwable)
        } catch (e: Exception) {
            // Never let diagnostics recording itself interfere with normal crash handling.
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
