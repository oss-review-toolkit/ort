package com.here.ort.downloader.vcs

import ch.frankel.slf4k.*

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.util.log
import com.here.ort.util.ProcessCapture
import com.here.ort.util.safeMkdirs

import java.io.File
import java.io.IOException

abstract class GitBase : VersionControlSystem() {

    override fun getRevision(workingDir: File): String {
        return runGitCommand(workingDir, "rev-parse", "HEAD").stdout().trim()
    }

    protected fun runGitCommand(workingDir: File, vararg args: String): ProcessCapture {
        return ProcessCapture(workingDir, "git", *args).requireSuccess()
    }

}

object Git : GitBase() {

    /**
     * Clones the Git repository using the native Git command.
     *
     * @param vcsPath If this parameter is not null or empty, the working tree is deleted and the path is selectively
     *                checked out using 'git checkout HEAD -- vcsPath'.
     *
     * @throws DownloadException In case the download failed.
     */
    @Suppress("ComplexMethod")
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

            // Do safe network bandwidth, first try to only fetch exactly the committish we want.
            try {
                runGitCommand(targetDir, "fetch", "origin", committish)
                runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                return getRevision(targetDir)
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn {
                    "Could not fetch only '$committish': ${e.message}\n" +
                            "Falling back to fetching everything."
                }
            }

            // Fall back to fetching everything.
            log.info { "Fetching origin and trying to checkout '$committish'." }
            runGitCommand(targetDir, "fetch", "origin")

            try {
                runGitCommand(targetDir, "checkout", committish)
                return getRevision(targetDir)
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn { "Could not checkout '$committish': ${e.message}" }
            }

            // If checking out the provided committish did not work and we have a version, try finding a tag
            // belonging to the version to checkout.
            if (version.isNotBlank()) {
                log.info { "Trying to guess tag for version '$version'." }

                val tag = runGitCommand(targetDir, "ls-remote", "--tags", "origin")
                        .stdout()
                        .lineSequence()
                        .map { it.split("\t").last() }
                        .find { it.endsWith(version) || it.endsWith(version.replace('.', '_')) }

                if (tag != null) {
                    log.info { "Using '$tag'." }
                    runGitCommand(targetDir, "fetch", "origin", tag)
                    runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                    return getRevision(targetDir)
                }

                log.warn { "No matching tag found for version '$version'." }
            }

            throw IOException("Unable to determine a committish to checkout.")
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not clone $vcsUrl: ${e.message}" }
            throw DownloadException("Could not clone $vcsUrl.", e)
        }
    }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("git", true)

    override fun isApplicableUrl(vcsUrl: String) = vcsUrl.endsWith(".git")

}
