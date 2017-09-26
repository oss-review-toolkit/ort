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
        /**
         * The name of the package.
         */
        val name: String,

        /**
         * The namespace of the package, for example the group id in Maven or the scope in NPM.
         */
        val namespace: String,

        /**
         * The version of the package.
         */
        val version: String,

        /**
         * A hash to uniquely identify the package in a machine-readable way. This *could* be implemented by simply
         * hashing the human-readable properties that uniquely identify the package. Note that this is different from
         * the package hash for the binary artifact of the package.
         */
        @JsonProperty("package_hash")
        val packageHash: String,

        /**
         * The list of references to packages this package depends on. Note that this list depends on the scope in
         * which this package reference is used.
         */
        val dependencies: List<PackageReference>
) {
    val identifier = "$namespace:$name:$version"

    fun dependsOn(pkg: Package): Boolean {
        return dependsOn(pkg.identifier)
    }

    fun dependsOn(packageIdentifier: String): Boolean {
        return dependencies.find { pkgRef ->
            // Strip the package manager part from the packageIdentifier because it is not part of the PackageReference.
            pkgRef.identifier == packageIdentifier.substringAfter(":") || pkgRef.dependsOn(packageIdentifier)
        } != null
    }
}
