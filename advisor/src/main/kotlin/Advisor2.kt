/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.advisor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.utils.ort.Environment
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class Advisor2(val providers: List<AdviceProvider>, val packages: Set<Package>, val config: AdvisorConfiguration) {
    private val updates = MutableSharedFlow<AdvisorUpdate>()

    fun getUpdates() = updates.asSharedFlow()

    private val state = MutableStateFlow(
        AdvisorState(
            providerProgress = providers.map { it.providerName }.associateWith {
                ProviderProgress(
                    totalPackages = packages.size,
                    completedPackages = 0
                )
            },
            finished = false
        )
    )

    fun getState() = state.asStateFlow()

    private val results = mutableMapOf<String, MutableMap<Identifier, AdvisorResult>>()
    private val mutex = Mutex()

    private lateinit var startTime: Instant
    private lateinit var endTime: Instant

    suspend fun execute() {
        startTime = Clock.System.now()

        coroutineScope {
            providers.map { provider ->
                async {
                    withContext(PluginContext(name = provider.providerName)) {
                        logger.info { "Starting advice provider '${provider.providerName}'." }

                        provider.execute(packages)
                            .onCompletion {
                                it?.printStackTrace()
                                logger.info { "Finished advice provider '${provider.providerName}'." }
                            }
                            .collect { update ->
                                logger.info("Received result for package '{package}'.", update.pkg.toCoordinates())

                                updates.emit(update)

                                mutex.withLock {
                                    val providerResults = results.getOrPut(update.provider) { mutableMapOf() }
                                    providerResults[update.pkg] = update.result
                                    updateStatus(update.provider, packages.size, providerResults.size)
                                }
                            }
                    }
                }
            }.awaitAll()

            endTime = Clock.System.now()
        }
    }

    private fun updateStatus(provider: String, totalPackages: Int, completedPackages: Int) {
        val providerProgress = ProviderProgress(totalPackages, completedPackages)
        val newProviderProgress = state.value.providerProgress + (provider to providerProgress)
        val newState = state.value.copy(
            providerProgress = newProviderProgress,
            finished = newProviderProgress.values.all { it.isFinished() }
        )

        state.value = newState
    }

    fun getRun(): AdvisorRun {
        return AdvisorRun(
            startTime = startTime.toJavaInstant(),
            endTime = endTime.toJavaInstant(),
            environment = Environment(),
            config = config,
            results = packages.map { it.id }.associateWith { id ->
                results.mapNotNull { (_, resultsById) -> resultsById[id] }
            }
        )
    }
}

data class AdvisorState(
    val providerProgress: Map<String, ProviderProgress>,
    val finished: Boolean
)

data class ProviderProgress(
    val totalPackages: Int,
    val completedPackages: Int
) {
    fun isFinished() = totalPackages == completedPackages
}

data class AdvisorUpdate(
    val provider: String,
    val pkg: Identifier,
    val result: AdvisorResult
)

data class OrtContext(
    val config: OrtConfiguration,
    val environment: Environment
) : AbstractCoroutineContextElement(OrtContext) {
    companion object Key : CoroutineContext.Key<OrtContext>

    override val key: CoroutineContext.Key<*> get() = Key
}

data class PluginContext(
    val name: String
) : AbstractCoroutineContextElement(PluginContext) {
    companion object Key : CoroutineContext.Key<PluginContext>

    override val key: CoroutineContext.Key<*> get() = Key
}
