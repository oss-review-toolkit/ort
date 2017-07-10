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
        return buildString {
            append("$indent$group:$artifact:$version:$scope:$scm")
            append(System.lineSeparator())
            dependencies.forEach { dependency ->
                append(dependency.toString("$indent  "))
            }
        }
    }
}
