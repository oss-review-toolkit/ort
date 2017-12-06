package vcs

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.util.ProcessCapture
import com.here.ort.util.getCommandVersion
import com.here.ort.util.log

import java.io.File
import java.io.IOException

data class SubversionLogEntry(
        @JacksonXmlProperty(isAttribute = true)
        val revision: String,
        @JacksonXmlProperty
        val msg: String,
        @JacksonXmlProperty
        val date: String,
        @JacksonXmlProperty
        val author: String) {
}

object Subversion : VersionControlSystem() {
    override fun getVersion(): String {
        val subversionVersionRegex = Regex("svn, version (?<version>[\\d.]+) \\(.*\\)")

        return getCommandVersion("svn") {
            subversionVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingDirectory(vcsDirectory: File) =
            object : WorkingDirectory(vcsDirectory) {

                val infoCommandResult = ProcessCapture("svn", "info", workingDir.absolutePath)

                override fun isValid() = infoCommandResult.exitValue() == 0;

                override fun getRemoteUrl() = infoCommandResult.stdout().lineSequence()
                        .first { it.startsWith("URL:") }.removePrefix("URL:").trim()

                override fun getRevision() = getLineValue("Revision: ")

                override fun getRootPath(path: File) = getLineValue("Working Copy Root Path:")

                override fun getPathToRoot(path: File): String
                        = getLineValue("Path:").substringAfter(File.separatorChar)

                private fun getLineValue(
                        linePrefix: String) = infoCommandResult.requireSuccess().stdout().lineSequence()
                        .first { it.startsWith(linePrefix) }.removePrefix(linePrefix).trim()
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.toLowerCase() in listOf("subversion", "svn")

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("svn", "ls", vcsUrl).exitValue() == 0

    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String,
                          targetDir: File): String {

        runSVNCommand(targetDir, "co", vcsUrl, "--depth", "empty", ".")

        val revision = if (vcsRevision != null && vcsRevision.isNotBlank()) {
            vcsRevision
        } else {
            if (version.isNotBlank()) {
                try {
                    log.info { "Trying to determine revision for version: $version" }
                    val tagsList = runSVNCommand(targetDir, "list", "$vcsUrl/tags").stdout().trim().lineSequence()
                    val tagName = tagsList.firstOrNull {
                        it.trimEnd('/').endsWith(version)
                                || it.trimEnd('/').endsWith(version.replace('.', '_'))
                    }

                    val xml = runSVNCommand(targetDir,
                                             "log",
                                             "$vcsUrl/tags/$tagName",
                                             "--xml").stdout().trim()
                    val xmlMapper = ObjectMapper(XmlFactory()).registerKotlinModule()
                    val logEntries: List<SubversionLogEntry> = xmlMapper.readValue(xml, xmlMapper.typeFactory
                            .constructCollectionType(List::class.java, SubversionLogEntry::class.java))
                    logEntries.firstOrNull()?.revision ?: ""

                } catch (e: IOException) {
                    if (Main.stacktrace) {
                        e.printStackTrace()
                    }

                    log.warn { "Could not determine revision for version: $version. Falling back to fetch everything" }
                    ""
                }

            } else {
                ""
            }
        }

        if (vcsPath != null && vcsPath.isNotBlank()) {
            targetDir.resolve(vcsPath).mkdirs()
        }

        if (revision.isNotBlank()) {
            runSVNCommand(targetDir, "up", "-r", revision, "--set-depth", "infinity", vcsPath?.apply { } ?: "")
        } else {
            runSVNCommand(targetDir, "up", "--set-depth", "infinity", vcsPath?.apply { } ?: "")
        }

        val workDir = Subversion.getWorkingDirectory(targetDir)
        return workDir.getRevision()
    }

    private fun runSVNCommand(workingDir: File, vararg args: String) = ProcessCapture(workingDir, "svn", *args).requireSuccess()
}
