package com.happycodelucky.foundation

import kotlin.native.ObjCName

/**
 * Entities that are intrinsically identifiable
 */
@ObjCName("HappyIdentifiable")
interface Identifiable {
    /**
     * Returns string identifier
     *
     * FIXME: Apple platform use Any
     */
    val id: String
}

/**
 * Identifier function
 */
fun Identifiable.identifier(): String = id
