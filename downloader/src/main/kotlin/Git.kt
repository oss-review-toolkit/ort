package com.here.provenanceanalyzer.downloader

import ch.frankel.slf4k.*

import com.here.provenanceanalyzer.util.log
import com.here.provenanceanalyzer.util.ProcessCapture
import com.here.provenanceanalyzer.util.safeMkdirs

import java.io.File
import java.io.IOException

object Git : VersionControlSystem() {

    /**
     * Clones the Git repository using the native Git command.
     *
     * @param vcsPath If this parameter is not null or empty, the working tree is deleted and the path is selectively
     *                checked out using 'git checkout HEAD -- vcsPath'.
     *
     * @throws DownloadException In case the download failed.
     */
    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, targetDir: File) {
        try {
            // Do not use "git clone" to have more control over what is being fetched.
            runGitCommand(targetDir, "init")
            runGitCommand(targetDir, "remote", "add", "origin", vcsUrl)

            if (vcsPath != null && vcsPath.isNotEmpty()) {
                log.info { "Configuring Git to do sparse checkout of path '$vcsPath'." }
                runGitCommand(targetDir, "config", "core.sparseCheckout", "true")
                val gitInfoDir = File(targetDir, ".git/info").apply { safeMkdirs() }
                File(gitInfoDir, "sparse-checkout").writeText(vcsPath)
            }

            val committish = if (vcsRevision != null && vcsRevision.isNotEmpty()) vcsRevision else "HEAD"

            try {
                runGitCommand(targetDir, "fetch", "origin", committish)
                runGitCommand(targetDir, "reset", "--hard", "FETCH_HEAD")
            } catch (e: IOException) {
                log.warn { "Could not fetch '$committish': ${e.message}" }
                runGitCommand(targetDir, "fetch", "origin")
                runGitCommand(targetDir, "checkout", committish)
            }
        } catch (e: IOException) {
            throw DownloadException("Could not clone $vcsUrl.", e)
        }
    }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("git", true)

    override fun isApplicableUrl(vcsUrl: String) = vcsUrl.endsWith(".git")

    private fun runGitCommand(targetDir: File, vararg args: String) {
        ProcessCapture(targetDir, "git", *args).requireSuccess()
    }

}
