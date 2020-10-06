/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.fossid.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class Project {
    var id: Int? = null

    var created: String? = null
    var updated: String? = null
    var creator: String? = null

    @JsonProperty("project_code")
    var projectCode: String? = null
    @JsonProperty("project_name")
    var projectName: String? = null

    @JsonProperty("limit_date")
    var limitDate: String? = null

    @JsonProperty("product_code")
    var productCode: String? = null
    @JsonProperty("product_name")
    var productName: String? = null

    var description: String? = null
    var comment: String? = null

    @JsonProperty("is_archived")
    var isArchived: Int? = null
    @JsonProperty("jira_project_key")
    var jiraProjectKey: String? = null
    @JsonProperty("creation_date")
    var creationDate: String? = null
    @JsonProperty("date_limit_date")
    var dateLimitDate: String? = null
}
