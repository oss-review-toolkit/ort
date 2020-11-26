:title-page:
:sectnums:
:toc: preamble

= Vulnerability Document
:author-name: OSS Review Toolkit
[#assign now = .now]
:revdate: ${now?date?iso_local}
:revnumber: 1.0.0

== Packages:
[#assign advisorResults = ortResult.getAdvisorResultContainers(false)]
[#list advisorResults as advisorResult]
Package: *${advisorResult.id.toCoordinates()}*

[#list advisorResult.results as result]

* Advisor: ${result.advisor.name}

[#list result.vulnerabilities as vulnerability]

** ${vulnerability.url}[${vulnerability.id}], Severity: ${vulnerability.severity}

[/#list]

[/#list]
[/#list]
