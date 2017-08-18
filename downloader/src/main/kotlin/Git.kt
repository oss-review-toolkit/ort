package com.here.provenanceanalyzer.downloader

object Git : VersionControlSystem() {

    override fun download(vcsUrl: String, vcsRevision: String?): Boolean {
        return false
    }

    override fun isApplicable(vcsUrl: String) = vcsUrl.endsWith(".git")

}
