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
    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String {
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
                runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                return getRevision(targetDir)
            } catch (e: IOException) {
                log.warn {
                    "Could not fetch only '$committish': ${e.message}\n" +
                            "Falling back to fetching everything."
                }
            }

            log.info { "Fetching origin and trying to checkout '$committish'." }

            runGitCommand(targetDir, "fetch", "origin")

            try {
                runGitCommand(targetDir, "checkout", committish)
                return getRevision(targetDir)
            } catch (e: IOException) {
                log.warn { "Could not checkout '$committish': ${e.message}" }
            }

            if (version.isNotBlank()) {
                log.info { "Trying to guess tag for version '$version'." }

                val tag = runGitCommand(targetDir, "ls-remote", "--tags", "origin")
                        .stdout()
                        .lineSequence()
                        .map { it.split("\t").last() }
                        .find { it.endsWith(version.toString()) }

                if (tag != null) {
                    log.info { "Using '$tag'." }
                    runGitCommand(targetDir, "fetch", "origin", tag)
                    runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                    return getRevision(targetDir)
                }

                log.warn { "No matching tag found for version '$version'." }
            }

            log.info { "Checking out remote HEAD." }

            val head = runGitCommand(targetDir, "ls-remote", "origin", "HEAD").stdout().split("\t").first()
            log.info { "Remote HEAD points to $head." }

            runGitCommand(targetDir, "checkout", head)
            return getRevision(targetDir)
        } catch (e: IOException) {
            log.error { "Could not clone $vcsUrl: ${e.message}" }
            throw DownloadException("Could not clone $vcsUrl.", e)
        }
    }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("git", true)

    override fun isApplicableUrl(vcsUrl: String) = vcsUrl.endsWith(".git")

    private fun getRevision(targetDir: File): String {
        return runGitCommand(targetDir, "rev-parse", "HEAD").stdout().trim()
    }

    private fun runGitCommand(targetDir: File, vararg args: String): ProcessCapture {
        return ProcessCapture(targetDir, "git", *args).requireSuccess()
    }

}
