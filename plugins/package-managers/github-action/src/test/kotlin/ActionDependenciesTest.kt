/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.githubaction

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

class ActionDependenciesTest : WordSpec({
    "of()" should {
        "remove local actions from the transitive dependencies" {
            val nested = action("owner/nested@v1", dependencies = listOf(local("./nested-local")))
            val composite = action(
                "owner/composite@v1",
                dependencies = listOf(local("./local"), nested, action("owner/remote@v1"))
            )

            val result = ActionDependencies.of(listOf(composite, action("owner/other@v1")))

            result.actions should containExactly(
                action(
                    "owner/composite@v1",
                    dependencies = listOf(action("owner/nested@v1"), action("owner/remote@v1"))
                ),
                action("owner/other@v1")
            )

            result.localActions should containExactlyInAnyOrder(
                "owner/composite@v1" to "./local",
                "owner/nested@v1" to "./nested-local"
            )
        }

        "report a local action only once if it is used multiple times" {
            val composite = action("owner/composite@v1", dependencies = listOf(local("./local")))

            val result = ActionDependencies.of(listOf(composite, composite))

            result.localActions should containExactly("owner/composite@v1" to "./local")
        }
    }
})

private fun action(uses: String, dependencies: List<ABom.Action> = emptyList()) =
    ABom.Action(
        uses = uses,
        owner = uses.substringBefore("/"),
        repo = uses.substringAfter("/").substringBefore("@"),
        ref = uses.substringAfter("@"),
        type = ABom.ActionType.STANDARD,
        pinned = false,
        dependencies = dependencies
    )

private fun local(uses: String) = ABom.Action(uses = uses, path = uses, type = ABom.ActionType.LOCAL, pinned = false)
