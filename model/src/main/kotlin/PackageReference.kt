package com.here.provenanceanalyzer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

import java.util.SortedSet

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
         * The list of references to packages this package depends on. Note that this list depends on the scope in
         * which this package reference is used.
         */
        val dependencies: SortedSet<PackageReference>
) : Comparable<PackageReference> {
    /**
     * The minimum human readable information to identify the package referred to. As references are specific to the
     * package manager, it is not explicitly included.
     */
    val identifier = "$namespace:$name:$version"

    /**
     * Returns whether the given package is a (transitive) dependency of this reference.
     */
    fun dependsOn(pkg: Package): Boolean {
        return dependsOn(pkg.identifier)
    }

    /**
     * Returns whether the package identified by [pkgId] is a (transitive) dependency of this reference.
     */
    fun dependsOn(pkgId: String): Boolean {
        return dependencies.find { pkgRef ->
            // Strip the package manager part from the packageIdentifier because it is not part of the PackageReference.
            pkgRef.identifier == pkgId.substringAfter(":") || pkgRef.dependsOn(pkgId)
        } != null
    }

    /**
     * A comparison function to sort package references by their identifier.
     */
    override fun compareTo(other: PackageReference) = compareValuesBy(this, other, { it.identifier })
}
