package com.here.provenanceanalyzer.model

import java.util.SortedSet

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
         * The set of identified packages used by the project.
         */
        val packages: SortedSet<Package>
)
