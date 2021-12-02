[#--
Copyright (C) 2021 Bosch.IO GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

SPDX-License-Identifier: Apache-2.0
License-Filename: LICENSE
--]

[#assign ModelExtensions = statics['org.ossreviewtoolkit.model.utils.ExtensionsKt']]

:title-page:
:sectnums:
:toc: preamble

= Defect Report
:author-name: OSS Review Toolkit
[#assign now = .now]
:revdate: ${now?date?iso_local}
:revnumber: 1.0.0

[#assign advisorResultsWithErrors = helper.advisorResultsWithIssues(AdvisorCapability.DEFECTS, Severity.ERROR)]
[#if advisorResultsWithErrors?has_content]
== Warning
[.alert]
Errors were encountered while retrieving defect information. Therefore, this report may be incomplete and
lack relevant defects. Further details about the issues that occurred can be found in the <<Packages with errors>>
section.

<<<
[/#if]

== Packages
[#assign advisorResults = helper.advisorResultsWithDefects()]
[#list advisorResults as id, results]
=== ${id.name}

Package URL: _${ModelExtensions.toPurl(id)}_

[#list results as result]

*Advisor: ${result.advisor.name}*

[#list result.defects as defect]

* [#if defect.title??]*Title:* "pass:[${defect.title}]"[#else]*Id:* ${defect.id}[/#if]
+
[cols="2,6",frame="none",grid="none"]
|===
|URL:|${defect.url}
|Internal ID:|${defect.id}
[#if defect.severity??]|Severity:|${defect.severity}[/#if]
|Fix availability:|[#if defect.fixReleaseVersion??]Fixed in version ${defect.fixReleaseVersion}[#elseif defect.fixReleaseUrl??]Fixed in release ${defect.fixReleaseUrl}[#else]No known fix available.[/#if]
|===
[/#list]

[#if result.summary.issues?has_content]
.Issues
[cols="1,5",options="header",caption=]
|===
|Severity|Message
[#list result.summary.issues as issue]
|${issue.severity}|${issue.message}
[/#list]
|===

[/#if]

[/#list]
[/#list]

[#if !advisorResults?has_content]
No packages with defects have been found.
[/#if]

[#if advisorResultsWithErrors?has_content]
<<<
== Packages with errors

When retrieving defect information for these packages, the advisor module encountered errors. Therefore, it is possible
that existing defects are missing from the report. This section lists the issues that occurred when requesting defect
information for the single packages.

[#list advisorResultsWithErrors as id, results]
=== ${id.name}

${ModelExtensions.toPurl(id)}

[#list results as result]

[cols="1,5",options="header"]
|===
|Advisor|Message
[#list result.summary.issues as issue]
|${result.advisor.name}|${issue.message}
[/#list]
|===

[/#list]
[/#list]
[/#if]
