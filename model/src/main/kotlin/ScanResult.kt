package com.here.provenanceanalyzer.model

/**
 * A class that bundles all information generated during a scan.
 */
data class ScanResult(
        /**
         * The project that was scanned. The tree of dependencies is implicitly contained in the scopes in the form
         * of package references.
         */
        val project: Project,

        /**
         * The list of identified packages used by the project.
         */
        val packages: List<Package>
)
