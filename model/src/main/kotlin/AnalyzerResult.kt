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
        val packages: SortedSet<Package>,

        /**
         * If dynamic versions were allowed during the dependency resolution. If true it means that the dependency tree
         * might change with another scan if any of the (transitive) dependencies is declared with a version range and
         * a new version of this dependency was released in the meantime. It is always true for package managers that do
         * not support lock files, but do support version ranges.
         */
        val allowDynamicVersions: Boolean
)
