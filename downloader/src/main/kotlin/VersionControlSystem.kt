package com.here.provenanceanalyzer.downloader

import java.io.File

val VERSION_CONTROL_SYSTEMS = listOf(
        Git
)

abstract class VersionControlSystem {

    /**
     * Use this VCS to download the source code from the specified URL.
     */
    abstract fun download(vcsUrl: String, vcsRevision: String?, targetDir: File): Boolean

    /**
     * Check if this VCS can download from the provided URL.
     */
    abstract fun isApplicable(vcsUrl: String): Boolean

}
