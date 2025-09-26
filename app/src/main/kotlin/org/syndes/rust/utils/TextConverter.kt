package org.syndes.rust.utils

object TextConverter {
    private const val RN = "\r\n"
    private const val N = "\n"
    private const val R = "\r"
    const val WINDOWS = "windows"
    const val UNIX = "unix"
    const val MACOS = "macos"

    fun applyEndings(value: String, to: String): String {
        var v = value
        when (to) {
            WINDOWS -> {
                // normalize then convert unix->windows
                v = v.replace(RN, N)
                v = v.replace(R, N)
                v = v.replace(N, RN)
                return v
            }
            UNIX -> {
                v = v.replace(RN, N)
                v = v.replace(R, N)
                return v
            }
            MACOS -> {
                v = v.replace(RN, N)
                v = v.replace(R, N)
                v = v.replace(N, R)
                return v
            }
            else -> return v
        }
    }
}
