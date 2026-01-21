package com.happycodelucky.foundation.text

// For JVM, passthrough to Java's String.format(vararg args)
actual fun String.format(vararg args: Any?): String = String.format(this, *args)
