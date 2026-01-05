# Reporter CLI Reference

The *ORT Reporter* generates a wide variety of documents in different formats from ORT result files.

## Usage

```
ort report [<options>]
```

## Input options

* `-i`, `--ort-file=<value>` - The ORT result file to use.

## Output options

* `-o`, `--output-dir=<value>` - The output directory to store the generated reports in.
* `-f`, `--report-formats=<value>` - A comma-separated list of report formats to generate, any of [AOSD2.0, AOSD2.1, CtrlXAutomation, CycloneDX,
                                     DocBookTemplate, EvaluatedModel, FossID, FossIdSnippet, HtmlTemplate, ManPageTemplate, Opossum, PdfTemplate,
                                     PlainTextTemplate, SpdxDocument, StaticHTML, TrustSource, WebApp].

  * [Audi Open Source Diagnostics (AOSD)](https://www.aosd.cloud.audi/help)
    * Version 2.0 (`-f AOSD2.0`)
    * Version 2.1 (`-f AOSD2.1`)
  * [AsciiDoc Template](../configuration/reporter-templates.md#custom-reports-using-asciidoc-templates) (`-f AsciiDocTemplate`)
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

## Configuration options

* `--copyright-garbage-file=<value>` - A file containing copyright statements which are marked as garbage. This can make the output inconsistent with the evaluator output but is useful when testing copyright garbage. (default: ~/.ort/config/copyright-garbage.yml)
* `--custom-license-texts-dir=<value>` - A directory which contains custom license texts. It must contain one text file per license with the license ID as the filename. The license texts from this directory will take priority over the license texts provided by the license fact providers configured in the config.yml.
* `--how-to-fix-text-provider-script=<value>` - The path to a Kotlin script which returns an instance of a 'HowToFixTextProvider'. That provider injects how-to-fix texts in Markdown format for ORT issues. (default: ~/.ort/config/reporter.how-to-fix-text-provider.kts)
* `--license-classifications-file=<value>` - A file containing the license classifications. This can make the output inconsistent with the evaluator output but is useful when testing license classifications. (default: ~/.ort/config/license-classifications.yml)
* `--package-configurations-dir=<value>` - A directory that is searched recursively for package configuration files. Each file must only contain a single package configuration. This can make the output inconsistent with the evaluator output but is useful when testing package configurations.
* `--refresh-resolutions` - Use the resolutions from the global and repository configuration instead of the resolved configuration. This can make the output inconsistent with the evaluator output but is useful when testing resolutions.
* `--repository-configuration-file=<value>` - A file containing the repository configuration. If set, overrides the repository configuration contained in the ORT result input file. This can make the output inconsistent with the output of previous commands but is useful when testing changes in the repository configuration.
* `--resolutions-file=<value>` - A file containing issue and rule violation resolutions. (default: ~/.ort/config/resolutions.yml)

## Options

* `-O`, `--report-option=<value>` - Specify a report-format-specific option. The key is the (case-insensitive) name of the report format, and the value is an arbitrary key-value pair. For example: `-O PlainTextTemplate=template.id=NOTICE_SUMMARY`
* `-h`, `--help` - Show this message and exit.

## Related resources

* Code
  * [plugins/commands/reporter/src/main/kotlin/ReportCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/reporter/src/main/kotlin/ReportCommand.kt)
* How-to guides
  * [How to generate SBOMs](../../how-to-guides/how-to-generate-sboms.md)
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
* Reference
  * [Reporter templates](../configuration/reporter-templates.md)
* Tutorials
  * [Visualizing Results](../../tutorials/walkthrough/visualizing-results.md)
  * [Generating SBOMs](../../tutorials/walkthrough/generating-sboms.md)
