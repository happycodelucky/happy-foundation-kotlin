package com.happycodelucky.foundation

import kotlin.native.ObjCName

/**
 * Identifies a type base on enum type cases
 *
 * FIXME: Issue related to using an interface with generics here
 */
@ObjCName("HappyTypeIdentifiable")
interface TypeIdentifiable<T: Enum<T>> {
    /**
     * Type discriminator
     */
    val type: T
}

/**
 * Identifier function
 */
fun <T: Enum<T>> TypeIdentifiable<T>.typeIdentifier(): Any = type
