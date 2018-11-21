package com.here.ort.model.config

import java.util.*

data class CopyrightBlacklist(val items: Set<String> = setOf()) {
    constructor(vararg items: String) : this(items.toSortedSet())
}

