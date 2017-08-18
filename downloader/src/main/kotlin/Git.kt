package com.here.provenanceanalyzer.downloader

import java.io.File

object Git : VersionControlSystem() {

    override fun download(vcsUrl: String, vcsRevision: String?, targetDir: File): Boolean {
        runGitCommand(targetDir, "init")
        runGitCommand(targetDir, "remote", "add", "origin", vcsUrl)
        val committish = if (vcsRevision.isNullOrEmpty()) "master" else vcsRevision!!
        runGitCommand(targetDir, "fetch", "origin", committish)
        runGitCommand(targetDir, "reset", "--hard", "FETCH_HEAD")
        return true
    }

    override fun isApplicable(vcsUrl: String) = vcsUrl.endsWith(".git")

    private fun runGitCommand(targetDir: File, vararg args: String) {
        val builder = ProcessBuilder("git", *args)
                .directory(targetDir)
        val process = builder.start()
        process.waitFor()
        require(process.exitValue() == 0) {
            "Git command ${args.joinToString(" ")} failed: ${process.errorStream.bufferedReader().use { it.readText() }}"
        }
    }

}
