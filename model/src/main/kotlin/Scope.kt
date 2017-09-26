package com.here.provenanceanalyzer.model

/**
 * The scope class puts package dependencies into context.
 */
data class Scope(
        /**
         * The respective package manager's native name for the scope, e.g. "compile", " provided" etc. for Maven, or
         * "dependencies", "devDependencies" etc. for NPM.
         */
        val name: String,

        /**
         * A flag to indicate whether this scope is delivered along with the product, i.e. distributed to external
         * parties.
         */
        val delivered: Boolean,

        /**
         * The list of references to packages in this scope. Note that only the first-order packages in this list
         * actually belong to the scope of [name]. Transitive dependency packages usually belong to the scope that
         * describes the packages required to compile the product. As an example, if this was the Maven "test" scope,
         * all first-order items in [dependencies] would be packages required for testing the product. But transitive
         * dependencies would not be test dependencies of the test dependencies, but compile dependencies of test
         * dependencies.
         */
        val dependencies: List<PackageReference>
) {
    fun contains(pkg: Package): Boolean {
        return contains(pkg.identifier)
    }

    fun contains(packageIdentifier: String): Boolean {
        return dependencies.find { pkgRef ->
            // Strip the package manager part from the packageIdentifier because it is not part of the PackageReference.
            pkgRef.identifier == packageIdentifier.substringAfter(":") || pkgRef.dependsOn(packageIdentifier)
        } != null
    }
}
