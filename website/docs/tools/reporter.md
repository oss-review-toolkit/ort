---
sidebar_position: 6
---

# Reporter

The *reporter* generates a wide variety of documents in different formats from ORT result files.
Currently, the following formats are supported (reporter names are case-insensitive):

* [Audi Open Source Diagnostics 2](https://www.aosd.cloud.audi/help) (`-f AOSD2`)
* [AsciiDoc Template](../configuration/reporter-templates.md#asciidoc-templates) (`-f AsciiDocTemplate`)
  * Customizable with [Apache Freemarker](https://freemarker.apache.org/) templates and [AsciiDoc](https://asciidoc.org/)
  * PDF style customizable with Asciidoctor [PDF themes](https://docs.asciidoctor.org/pdf-converter/latest/theme/)
  * Supports multiple AsciiDoc backends:
    * PDF (`-f PdfTemplate`)
    * HTML (`-f HtmlTemplate`)
    * DocBook (`-f DocBookTemplate`)
    * Man page (`-f ManPageTemplate`)
* [ctrlX AUTOMATION](https://apps.boschrexroth.com/microsites/ctrlx-automation/) platform [FOSS information](https://github.com/boschrexroth/json-schema/tree/master/ctrlx-automation/ctrlx-os/apps/fossinfo) (`-f CtrlXAutomation`)
* [CycloneDX](https://cyclonedx.org/) BOM (`-f CycloneDx`)
* [FossID](https://fossid.com/) report download
  * HTML, SPDX, and Excel reports (`-f FossId`)
  * Snippet report (`-f FossIdSnippet`)
* [GitLabLicenseModel](https://docs.gitlab.com/ee/ci/pipelines/job_artifacts.html#artifactsreportslicense_scanning-ultimate) (`-f GitLabLicenseModel`)
  * There is a [tutorial video](https://youtu.be/dNmH_kYJ34g) by @xlgmokha
* [NOTICE](https://infra.apache.org/licensing-howto.html) file in two variants
  * List license texts and copyrights by package (`-f PlainTextTemplate`)
  * Summarize all license texts and copyrights (`-f PlainTextTemplate -O PlainTextTemplate=template.id=NOTICE_SUMMARY`)
  * Customizable with [Apache Freemarker](https://freemarker.apache.org/) templates
* [OpossumUI](https://github.com/opossum-tool/opossumUI) input (`-f Opossum`)
* [SPDX Document](https://spdx.dev/specifications/), version 2.2 (`-f SpdxDocument`)
* Static HTML (`-f StaticHtml`)
* [TrustSource](https://www.trustsource.io/) JSON file (`-f TrustSource`)
  * Use this as an alternative to [ts-scan](https://github.com/TrustSource/ts-scan) for support of more build systems.
* Web App (`-f WebApp`)
  * Also see the [EvaluatedModelReporter](https://github.com/oss-review-toolkit/ort/blob/main/plugins/reporters/evaluated-model/src/main/kotlin/EvaluatedModelReporter.kt) (`-f EvaluatedModel`) which is the JSON / YAML format used by the Web App report that is also suitable for custom post-processing.
