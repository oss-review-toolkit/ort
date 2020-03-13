/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.reporter

import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.RuleViolation
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.OrtConfiguration
import com.here.ort.model.licenses.LicenseConfiguration
import com.here.ort.model.utils.PackageConfigurationProvider
import com.here.ort.model.utils.SimplePackageConfigurationProvider
import com.here.ort.reporter.reporters.AbstractNoticeReporter

/**
 * A bundle of input to be used by [Reporter] implementations.
 */
data class ReporterInput(
    /**
     * The [OrtResult] to generate a report for.
     */
    val ortResult: OrtResult,

    /**
     * The [OrtConfiguration], can be used by the reporter to adapt the output based on how ORT is configured.
     */
    val ortConfig: OrtConfiguration = OrtConfiguration(),

    /**
     * A [PackageConfigurationProvider], can be used to obtain [PackageConfiguration]s for packages.
     */
    val packageConfigurationProvider: PackageConfigurationProvider = SimplePackageConfigurationProvider(),

    /**
     * A [ResolutionProvider], can be used to check which [OrtIssue]s and [RuleViolation]s are resolved.
     */
    val resolutionProvider: ResolutionProvider = DefaultResolutionProvider(),

    /**
     * A [LicenseTextProvider], can be used to integrate licenses texts into reports.
     */
    val licenseTextProvider: LicenseTextProvider = DefaultLicenseTextProvider(),

    /**
     * A [CopyrightGarbage] container, can be used to clean up copyrights used in reports.
     */
    val copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),

    /**
     * A [LicenseConfiguration], can be used to handle licenses based on the user's configuration, for example to
     * determine which licenses to include in a notice file.
     */
    val licenseConfiguration: LicenseConfiguration = LicenseConfiguration(),

    /**
     * A [notice pre-processor][AbstractNoticeReporter.PreProcessor], used by implementations of
     * [AbstractNoticeReporter] to pre-process the default [data][AbstractNoticeReporter.NoticeReportData].
     */
    val preProcessingScript: String? = null
)
