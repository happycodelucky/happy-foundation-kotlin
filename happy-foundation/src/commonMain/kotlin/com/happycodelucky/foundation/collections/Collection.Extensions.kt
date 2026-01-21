package com.happycodelucky.foundation.collections

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Compares collections for content equality
 *
 * Example:
 *  listof("a", "b", "c") contentEquals listof("a", "b", "c") == true
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
infix fun <T> Collection<T>?.contentEquals(other: Collection<T>?): Boolean {
    if (this == other) {
        return true
    }

    // If this != other (null) then check if one is null
    if (this == null || other == null) {
        return false
    }

    if (this.size != other.size) {
        return false
    }

    this.forEachIndexed { index, it ->
        val otherIt = other.elementAt(index)
        if (Collection::class.isInstance(it) && Collection::class.isInstance(otherIt)) {
            if (!((it as Collection<*>) contentEquals (otherIt as Collection<*>))) {
                return false
            }
        }

        if (it != other.elementAt(index)) return false
    }

    return true
}

/**
 * Adds a [value] into the MutableCollection if it is not null. If the value is successfully
 * added into the MutableCollection, a true value is returned; otherwise a false value
 * is returned.
 */
fun <E> MutableCollection<E>.addIfNotNull(value: E?): Boolean =
    if (value != null) {
        add(value)
    } else {
        false
    }

/**
 * Find the first instance in the [Iterable] that is of type [T] and returns it, or null if none
 * are found
 */
inline fun <reified T> Iterable<*>.firstInstanceOrNull(): T? = firstOrNull { it is T }?.let { it as T }
