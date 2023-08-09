package org.ossreviewtoolkit.plugins.scanners.dos

import io.mockk.*

import java.io.File
import java.time.Instant

import kotlinx.coroutines.test.runTest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.ossreviewtoolkit.clients.dos.DOSRepository
import org.ossreviewtoolkit.clients.dos.DOSService
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.common.withoutSuffix

class DOSTest {
    private val scannerConfig = mockk<ScannerConfiguration>()
    private val config = mockk<DOSConfig>()
    private val repository = mockk<DOSRepository>(relaxed = true)

    private lateinit var dos: DOS

    @BeforeEach
    fun setUp() {
        every { DOSConfig.create(scannerConfig) } returns config
        dos = DOS("DOS", scannerConfig, config)
        dos.repository = repository
    }

    @Test
    fun `test runBackendScan with successful interactions`() = runTest {
        val pkg = Package.EMPTY
        val dosDir = mockk<File>()
        val tmpDir = "/tmp/"
        val thisScanStartTime = Instant.now()
        val issues = mutableListOf<Issue>()

        val zipName = "test.zip"
        val purl = "pkg:pypi/requests@2.25.1"

        every { dosDir.name } returns zipName.withoutSuffix("zip").toString()
        coEvery { repository.getPresignedUrl(zipName) } returns "presigned_url"
        coEvery { repository.uploadFile(any(), any()) } returns true
        coEvery { repository.postScanJob(zipName, purl) } returns DOSService.JobResponseBody("job_id", "message")

        val result = dos.runBackendScan(pkg, dosDir, tmpDir, thisScanStartTime, issues)

        assertNotNull(result)
        coVerifySequence {
            repository.getPresignedUrl(zipName)
            repository.uploadFile(any(), any())
            repository.postScanJob(zipName, purl)
        }
    }
}
