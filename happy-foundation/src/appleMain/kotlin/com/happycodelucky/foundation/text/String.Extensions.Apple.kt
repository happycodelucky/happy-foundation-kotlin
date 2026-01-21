package com.happycodelucky.foundation.text

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

// Translation of Java's String.format(vararg args) to NSString.stringWithFormat
// https://stackoverflow.com/questions/64495182/kotlin-native-ios-string-formatting-with-vararg/64499248#64499248
actual fun String.format(vararg args: Any?): String {
    var returnString = ""

    val regEx = "%[\\d|.]*[sdf]|%".toRegex()

    val singleFormats =
        regEx
            .findAll(this)
            .map {
                it.groupValues.first()
            }.toMutableList()

    val newStrings = this.split(regEx).toMutableList()
    for (arg in args) {
        val newString = newStrings.removeFirst()
        val singleFormat = singleFormats.removeFirstOrNull()
        returnString +=
            when (arg) {
                is Double -> {
                    NSString.stringWithFormat(newString + singleFormat, arg)
                }
                is Int -> {
                    NSString.stringWithFormat(newString + singleFormat, arg)
                }
                else -> {
                    NSString.stringWithFormat("$newString%@", arg)
                }
            }
    }

    if (newStrings.isNotEmpty()) {
        returnString += newStrings.joinToString("")
    }

    return returnString
}
