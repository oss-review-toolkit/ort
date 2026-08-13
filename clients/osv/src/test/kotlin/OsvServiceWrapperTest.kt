/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.clients.osv

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import java.io.IOException

private class FakeOsvService(
    private val responseFor: (List<VulnerabilitiesForPackageRequest>) -> Result<VulnerabilitiesForPackageBatchResponse>
) : OsvService {
    val callSizes = mutableListOf<Int>()

    override suspend fun getVulnerabilityIdsForPackages(
        request: VulnerabilitiesForPackageBatchRequest
    ): VulnerabilitiesForPackageBatchResponse {
        callSizes += request.queries.size
        return responseFor(request.queries).fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    override suspend fun getVulnerabilityForId(id: String): Vulnerability = error("Not used in this test.")
}

private fun emptyBatchResponse(size: Int) =
    VulnerabilitiesForPackageBatchResponse(results = List(size) { VulnerabilitiesForPackageBatchResponse.IdList() })

private fun makeRequests(n: Int): List<VulnerabilitiesForPackageRequest> =
    (0 until n).map { VulnerabilitiesForPackageRequest(commit = "request-$it") }

// noinspection unused
class OsvServiceWrapperTest : StringSpec({
    "getVulnerabilityIdsForPackages isolates the bad request when OSV reports a failing index" {
        val failingCommit = "request-2"
        val requests = makeRequests(5)

        // The bad request is identified by its commit. The fake fails any call that includes it, and succeeds
        // otherwise. Call order from the wrapper for 5 requests with one bad request:
        //   1. size=5, contains bad -> fail with "error in query at index 2"
        //   2. size=2, indices [0,1], no bad -> succeed
        //   3. size=2, indices [3,4], no bad -> succeed
        //   4. size=4, indices [0,1,3,4], no bad -> succeed (the final retry)
        val failingIndex = requests.indexOfFirst { it.commit == failingCommit }
        val fake = FakeOsvService { chunk ->
            if (chunk.any { it.commit == failingCommit }) {
                Result.failure(
                    IOException(
                        "Error code 3: error in query at index $failingIndex: rpc error: desc = bad"
                    )
                )
            } else {
                Result.success(emptyBatchResponse(chunk.size))
            }
        }

        val wrapper = OsvServiceWrapper(service = fake)
        val results = wrapper.getVulnerabilityIdsForPackages(requests)

        results shouldHaveSize 5
        results.indices.filter { results[it] is OsvServiceWrapper.RequestResult.Failure } shouldContainExactly
            listOf(failingIndex)
        results.forEachIndexed { index, result ->
            if (index != failingIndex) {
                result.shouldBeInstanceOf<OsvServiceWrapper.RequestResult.Success>()
            } else {
                // The failure message should include the original OSV error so users can diagnose the issue.
                (result as OsvServiceWrapper.RequestResult.Failure).message shouldContain
                    "error in query at index $failingIndex"
            }
        }

        // The first call must be the full batch; the last call must be the retry of the 4 good requests.
        fake.callSizes.first() shouldBe 5
        fake.callSizes.last() shouldBe 4
    }

    "getVulnerabilityIdsForPackages throws IOException when the error has no parseable index" {
        val requests = makeRequests(5)

        // The error has no parseable index. The wrapper should throw IOException so the caller can report it as a
        // single provider-level issue, without duplicating it per-package.
        val fake = FakeOsvService { _ ->
            Result.failure(IOException("Error code 5: internal server error"))
        }

        val wrapper = OsvServiceWrapper(service = fake)
        val exception = shouldThrow<IOException> {
            wrapper.getVulnerabilityIdsForPackages(requests)
        }

        exception.message shouldBe "Error code 5: internal server error"

        // The wrapper should have made exactly one call and given up.
        fake.callSizes shouldContainExactly listOf(5)
    }

    "getVulnerabilityIdsForPackages isolates multiple bad requests" {
        val badCommits = setOf("request-1", "request-3")
        val requests = makeRequests(5)

        // The fake fails any call that includes at least one bad request, returning the first bad index it finds.
        // The wrapper should eventually identify all bad requests and report them individually.
        val fake = FakeOsvService { chunk ->
            val firstBad = chunk.indexOfFirst { it.commit in badCommits }
            if (firstBad >= 0) {
                Result.failure(IOException("Error code 3: error in query at index $firstBad: rpc error: desc = bad"))
            } else {
                Result.success(emptyBatchResponse(chunk.size))
            }
        }

        val wrapper = OsvServiceWrapper(service = fake)
        val results = wrapper.getVulnerabilityIdsForPackages(requests)

        results shouldHaveSize 5
        results.indices.filter { results[it] is OsvServiceWrapper.RequestResult.Failure } shouldContainExactly
            listOf(1, 3)
    }
})
