package com.here.provenanceanalyzer.model

data class Dependency(
        val group: String? = null,
        val artifact: String,
        val version: String,
        val scope: String,
        val dependencies: List<Dependency> = listOf(),
        val scm: String? = null
) {
    override fun toString(): String {
        return toString("")
    }

    private fun toString(indent: String): String {
        val stringBuilder = StringBuilder("${indent}${group}:${artifact}:${version}:${scope}:${scm}\n")
        dependencies.forEach { dependency ->
            stringBuilder.append(dependency.toString("${indent}  "))
        }
        return stringBuilder.toString()
    }
}
