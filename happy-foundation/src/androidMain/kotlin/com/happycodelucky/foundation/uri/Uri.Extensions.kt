package com.happycodelucky.foundation.uri

import android.net.Uri
import androidx.annotation.DrawableRes

/**
 * Indicates if the URI a file URL
 */
val Uri.isHttp: Boolean
    get() = scheme?.compareTo("https", true) == 0 || scheme?.compareTo("http", true) == 0

/**
 * Indicates if the URI a file URL
 */
val Uri.isFile: Boolean
    get() = scheme?.compareTo("file", true) == 0

/**
 * Indicates if the URI is a Content Provider URI
 */
val Uri.isContentResource: Boolean
    get() = scheme?.compareTo("content", true) == 0

/**
 * Indicates if the URI an Android resource URI
 */
val Uri.isResource: Boolean
    get() = scheme?.compareTo("android.resource", true) == 0

/**
 * Resource package name
 *
 * @see isResource
 * @see isContentResource
 */
val Uri.packageName: String?
    get() = if (isResource || isContentResource) authority else null

/**
 * Resource ID
 */
val Uri.resourceId: Int?
    get() = if (isResource) pathSegments.lastOrNull()?.toIntOrNull() else null

/**
 * Converts the Uri to a KMP URI
 */
fun Uri.toKmpUri(): com.eygraber.uri.Uri = this.toKmpUri()

/**
 * Creates a resource URI
 *
 * @param resource Resource ID
 * @param packageName Optional name of package
 */
fun resourceUri(
    @DrawableRes resource: Int,
    packageName: String? = null,
): Uri =
    Uri
        .Builder()
        .scheme("android.resource")
        .authority(packageName)
        .path(resource.toString())
        .build()
