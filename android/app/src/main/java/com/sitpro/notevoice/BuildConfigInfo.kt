package com.sitpro.notevoice

import android.content.Context

/** Version shown in Settings, read from the installed package (no BuildConfig generation needed). */
object BuildConfigInfo {
    fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
