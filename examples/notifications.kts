/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

val issues: Map<Identifier, Set<OrtIssue>> = ortResult.collectIssues()

if (issues.isNotEmpty()) {
    mailClient.sendMail(
        subject = "Issues found",
        message = "Number of issues found: ${issues.size}",
        htmlEmail = true,
        charset = Charsets.UTF_8,
        receivers = arrayOf("example1@oss-review-toolkit.org", "example2@oss-review-toolkit.org")
    )
}
