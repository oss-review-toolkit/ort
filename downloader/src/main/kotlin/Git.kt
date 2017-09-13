package com.here.provenanceanalyzer.downloader

import ch.frankel.slf4k.*

import com.here.provenanceanalyzer.util.log
import com.here.provenanceanalyzer.util.ProcessCapture

import java.io.File
import java.io.IOException

object Git : VersionControlSystem() {

    /**
     * Clones the Git repository using the native Git command.
     *
     * @param vcsPath If this parameter is not null or empty, the working tree is deleted and the path is selectively
     *                checked out using 'git checkout HEAD -- vcsPath'.
     */
    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, targetDir: File): Boolean {
        runGitCommand(targetDir, "init")
        runGitCommand(targetDir, "remote", "add", "origin", vcsUrl)

        if (vcsPath != null && vcsPath.isNotEmpty()) {
            log.info { "Configuring Git to do sparse checkout of path '$vcsPath'." }
            runGitCommand(targetDir, "config", "core.sparseCheckout", "true")
            val gitInfoDir = File(targetDir, ".git/info")
            if (gitInfoDir.mkdir()) {
                File(targetDir, ".git/info/sparse-checkout").writeText(vcsPath)
            } else {
                throw IOException("Could not create directory '${gitInfoDir.absolutePath}'.")
            }
        }

        val committish = if (vcsRevision != null && vcsRevision.isNotEmpty()) vcsRevision else "master"

        try {
            runGitCommand(targetDir, "fetch", "origin", committish)
            runGitCommand(targetDir, "reset", "--hard", "FETCH_HEAD")
        } catch (e: IllegalArgumentException) {
            log.warn { "Could not fetch '$committish': ${e.message}" }
            runGitCommand(targetDir, "fetch", "origin")
            runGitCommand(targetDir, "checkout", committish)
        }

        return true
    }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("git", true)

    override fun isApplicableUrl(vcsUrl: String) = vcsUrl.endsWith(".git")

    private fun runGitCommand(targetDir: File, vararg args: String) {
        val process = ProcessCapture(targetDir, "git", *args)
        require(process.exitValue() == 0) {
            "'${process.commandLine}' failed with exit code ${process.exitValue()}:\n${process.stderr()}"
        }
    }

}
