package com.here.provenanceanalyzer.model

data class Scope(
        val name: String,
        val delivered: Boolean,
        val dependencies: List<Dependency>
)
