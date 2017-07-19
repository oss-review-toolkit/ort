package com.here.provenanceanalyzer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties("identifier")
data class Dependency(
        val name: String,
        val namespace: String,
        val version: String,
        @JsonProperty("package_hash")
        val packageHash: String,
        val dependencies: List<Dependency>
) {
    val identifier = "$namespace:$name:$version"
}
