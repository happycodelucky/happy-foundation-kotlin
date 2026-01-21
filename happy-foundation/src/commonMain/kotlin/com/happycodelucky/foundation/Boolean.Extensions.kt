package com.happycodelucky.foundation

/**
 * Performs `block` when true
 */
fun Boolean.then(block: () -> Unit): Boolean {
    if (this) block()
    return this
}

/**
 * Performs `block` when false
 */
fun Boolean.otherwise(block: () -> Unit): Boolean {
    if (!this) block()
    return this
}

/**
 * Performs `block` when true, and not null
 */
fun Boolean?.then(block: () -> Unit): Boolean? {
    if (this == true) block()
    return this
}

/**
 * Performs `block` when false or null
 */
fun Boolean?.otherwise(block: () -> Unit): Boolean? {
    if (this == null || this == false) block()
    return this
}
