package com.here.ort.model.config

import java.util.SortedSet

data class CopyrightGarbage(val items: SortedSet<String> = sortedSetOf()) {
    constructor(vararg items: String) : this(items.toSortedSet())
}
