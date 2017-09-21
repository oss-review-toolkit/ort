package com.here.provenanceanalyzer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A human-readable reference to a software package. Strictly speaking, a [packageHash] would be enough to uniquely
 * refer to a package in a machine readable form. However, to make the serialized reference more readable for human
 * beings, also add the name, namespace and version properties of the referred package. A package reference can itself
 * refer to other packages that happen to be dependencies of this package.
 */
@JsonIgnoreProperties("identifier")
data class PackageReference(
        val name: String,
        val namespace: String,
        val version: String,
        @JsonProperty("package_hash")
        val packageHash: String,
        val dependencies: List<PackageReference>
) {
    val identifier = "$namespace:$name:$version"
}
