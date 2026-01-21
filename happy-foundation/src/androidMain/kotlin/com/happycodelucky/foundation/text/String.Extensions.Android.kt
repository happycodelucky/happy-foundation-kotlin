package com.happycodelucky.foundation.text

// For Android, passthrough to Java's String.format(vararg args)
actual fun String.format(vararg args: Any?): String {
    return String.format(this, *args)
}
