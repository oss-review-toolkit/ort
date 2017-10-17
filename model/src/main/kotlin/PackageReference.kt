package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import java.util.SortedSet

/**
 * A human-readable reference to a software [Package]. Each package reference itself refers to other package
 * references that are dependencies of the package.
 */
@JsonIgnoreProperties("identifier")
data class PackageReference(
        /**
         * The namespace of the package, for example the group id in Maven or the scope in NPM.
         */
        val namespace: String,

        /**
         * The name of the package.
         */
        val name: String,

        /**
         * The version of the package.
         */
        val version: String,

        /**
         * The list of references to packages this package depends on. Note that this list depends on the scope in
         * which this package reference is used.
         */
        val dependencies: SortedSet<PackageReference>,

        /**
         * A list of errors that occured handling this [PackageReference].
         */
        val errors: List<String> = emptyList()
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
