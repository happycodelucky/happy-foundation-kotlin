package com.happycodelucky.foundation.text

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.native.ShouldRefineInSwift

/**
 * Takes the string if not empty
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun String?.takeIfNotEmpty(): String? =
    takeIf { !it.isNullOrEmpty() }

/**
 * As the Kotlin stdlib function `String.format(vararg args: Any?)` is only available on a JVM target,
 * expect a platform-specific implementation.
 */
expect fun String.format(vararg args: Any?): String
