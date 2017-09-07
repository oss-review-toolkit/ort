package com.here.provenanceanalyzer.downloader

import ch.frankel.slf4k.*

import com.here.provenanceanalyzer.util.ProcessCapture
import com.here.provenanceanalyzer.util.log

import java.io.File

object GitRepo : VersionControlSystem() {

    /**
     * Clones the Git repositories defined in the manifest file using the Git Repo tool.
     *
     * @param vcsPath The path to the repo manifest file in the repository. Defaults to "manifest.xml" if not provided.
     */
    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, targetDir: File): Boolean {
        @Suppress("UnsafeCallOnNullableType")
        val revision = if (vcsRevision.isNullOrEmpty()) "master" else vcsRevision!!
        @Suppress("UnsafeCallOnNullableType")
        val manifestPath = if (vcsPath.isNullOrEmpty()) "manifest.xml" else vcsPath!!

        log.debug { "Initialize git-repo from $vcsUrl with branch $revision and manifest $manifestPath." }
        runRepoCommand(targetDir, "init", "--depth", "1", "-b", revision, "-u", vcsUrl, "-m", manifestPath)

        log.debug { "Start git-repo sync." }
        runRepoCommand(targetDir, "sync", "-c")

        return true
    }

    override fun isApplicableProvider(vcsProvider: String) =
            vcsProvider.toLowerCase() in listOf("gitrepo", "git-repo", "repo")

    override fun isApplicableUrl(vcsUrl: String) = false

    private fun runRepoCommand(targetDir: File, vararg args: String) {
        val process = ProcessCapture(targetDir, "repo", *args)
        require(process.exitValue() == 0) {
            "'${process.commandLine}' failed with exit code ${process.exitValue()}:\n${process.stderr()}"
        }
    }

}
