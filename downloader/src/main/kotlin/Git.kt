package com.here.provenanceanalyzer.downloader

import ch.frankel.slf4k.*

import com.here.provenanceanalyzer.util.log

import java.io.File

object Git : VersionControlSystem() {

    override fun download(vcsUrl: String, vcsRevision: String?, targetDir: File): Boolean {
        runGitCommand(targetDir, "init")
        runGitCommand(targetDir, "remote", "add", "origin", vcsUrl)

        @Suppress("UnsafeCallOnNullableType")
        val committish = if (vcsRevision.isNullOrEmpty()) "master" else vcsRevision!!

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
        val builder = ProcessBuilder("git", *args)
                .directory(targetDir)
        val process = builder.start()
        process.waitFor()
        require(process.exitValue() == 0) {
            "Git command ${args.joinToString(" ")} failed: " +
                    process.errorStream.bufferedReader().use { it.readText() }
        }
    }

}
