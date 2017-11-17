package com.here.ort.analyzer

import java.io.File

import io.kotlintest.matchers.shouldBe

import com.here.ort.analyzer.managers.Gradle
import com.here.ort.model.Package
import com.here.ort.downloader.Main as DownloaderMain
import com.here.ort.analyzer.Main as AnalyzerMain


class RobolectricTest : BaseIntegrationSpec() {

    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.robolectric",
            name = "robolectric",
            version = "3.3.2",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            downloadUrl = "",
            vcsPath = "",
            vcsProvider = "Git",
            vcsUrl = "git@github.com:robolectric/robolectric.git",
            hashAlgorithm = "",
            hash = "",
            //TODO: add a revision to have stable analyzer results
            vcsRevision = "")

    init {

    }
}
