package com.here.ort.model

import java.util.SortedSet

/**
 * A class that bundles all information generated during an analysis.
 */
data class AnalyzerResult(
        /**
         * The project that was analyzed. The tree of dependencies is implicitly contained in the scopes in the form
         * of package references.
         */
        val project: Project,

        /**
         * The set of identified packages used by the project.
         */
        val packages: SortedSet<Package>
)
