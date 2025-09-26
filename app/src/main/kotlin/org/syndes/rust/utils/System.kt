package org.syndes.rust.utils

import android.app.Activity

object System {
    @JvmStatic
    fun exitFromApp(activity: Activity) {
        activity.finish()
        kotlin.system.exitProcess(0)
    }
}
