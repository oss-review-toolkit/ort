package com.here.provenanceanalyzer.downloader

import java.io.File

object Git : VersionControlSystem() {

    override fun download(vcsUrl: String, vcsRevision: String?, targetDir: File): Boolean {
        return false
    }

    override fun isApplicable(vcsUrl: String) = vcsUrl.endsWith(".git")

}
