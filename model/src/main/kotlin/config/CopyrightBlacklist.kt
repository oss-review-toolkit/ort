package com.here.ort.model.config

import java.util.*

data class CopyrightBlacklist(val items: SortedSet<String> = sortedSetOf()) {
    constructor(vararg items: String) : this(items.toSortedSet())
}
