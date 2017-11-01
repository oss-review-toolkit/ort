package com.here.ort.downloader

import com.here.ort.downloader.vcs.*

import java.io.File

abstract class VersionControlSystem {
    companion object {
        /**
         * The prioritized list of all available version control systems. This needs to be initialized lazily to ensure
         * the referred objects, which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    Git,
                    GitRepo
            )
        }

        /**
         * Return the list of all applicable VCS for the given [vcsProvider], or null if none are applicable.
         */
        fun fromProvider(vcsProvider: String) = ALL.filter { it.isApplicableProvider(vcsProvider) }

        /**
         * Return the list of all applicable VCS for the given [vcsUrl], or null if none are applicable.
         */
        fun fromUrl(vcsUrl: String) = ALL.filter { it.isApplicableUrl(vcsUrl) }

        /**
         * Return the list of all applicable VCS for the given [vcsDirectory], or null if none are applicable.
         */
        fun fromDirectory(vcsDirectory: File) = ALL.filter { it.isApplicableDirectory(vcsDirectory) }
    }

    /**
     * Use this VCS to download the source code from the specified URL.
     *
     * @return A String identifying the revision that was downloaded.
     *
     * @throws DownloadException In case the download failed.
     */
    abstract fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String

    /**
     * Return true if the provider name matches this VCS. For example for SVN it should return true on "svn",
     * "subversion", or any other spelling that clearly identifies SVN.
     */
    abstract fun isApplicableProvider(vcsProvider: String): Boolean

    /**
     * Return true if this VCS can download from the provided URL. Should only return true when it's almost unambiguous,
     * for example when the URL ends on ".git" for Git or contains "/svn/" for SVN, but not when it contains the string
     * "git" as this could also be part of the host or project names.
     */
    abstract fun isApplicableUrl(vcsUrl: String): Boolean

    /**
     * Return true if the specified local directory is managed by this VCS, false otherwise.
     */
    abstract fun isApplicableDirectory(vcsDirectory: File): Boolean

    /**
     * Return the VCS-specific revision for the given [workingDir].
     */
    abstract fun getRevision(workingDir: File): String
}
