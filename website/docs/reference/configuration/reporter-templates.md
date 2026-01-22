# Reporter Templates

Reporter templates allow you use ORT results in a format you define.

## Custom reports using AsciiDoc templates

The AsciiDoc template reporters generate reports using a combination of [Apache Freemarker][Freemarker] templates and [AsciiDoc][AsciiDoc], with [AsciidoctorJ][AsciidoctorJ] serving as the Java interface and [AsciidoctorJ PDF][AsciidoctorJ-PDF] functioning as the PDF file generator for the [PDF Template Reporter](#generate-a-pdf). For each provided Freemarker template using the options outlined below, a separate intermediate file is created that can be processed by AsciidoctorJ.

### Reporter options

* `template.id`:
  A comma-separated list of IDs for templates built into ORT.
  Currently, the following IDs are supported:
  * "[disclosure_document](../../../../plugins/reporters/asciidoc/src/main/resources/templates/asciidoc/disclosure_document.ftl)" (default)
  * "[vulnerability_report](../../../../plugins/reporters/asciidoc/src/main/resources/templates/asciidoc/vulnerability_report.ftl)"
  * "[defect_report](../../../../plugins/reporters/asciidoc/src/main/resources/templates/asciidoc/defect_report.ftl)"
* `template.path`:
  A comma-separated list of paths to template files provided by the user.

If no options are provided, the "disclosure_document" template is used by default. Additionally, if security vulnerability information is available, the "vulnerability_report" template is also generated.

### Generate a PDF

After the intermediate AsciiDoc files are generated, they are processed by AsciidoctorJ, specifically through its PDF implementation, AsciidoctorJ PDF. You can provide a PDF theme to AsciidoctorJ PDF, which allows you to customize properties such as fonts and images displayed in the PDF. For more details, refer to the [Asciidoctor theming guide][Asciidoctor-PDF-theming].

The path to the theme can be set in the options described below. Note that only one theme can be specified for use with all provided templates. If no theme is specified, or if the provided path to the theme file does not exist, an in-built theme from AsciidoctorJ PDF will be used.

#### Command line

```shell
cli/build/install/ort/bin/ort report \
  -i <scanner-output-dir>/scanner-result.yml \
  -o <reporter-output-dir> \
  -f PdfTemplate \
  --report-option PdfTemplate=template.id=<template-id> \
  --report-option PdfTemplate=pdf.theme.file=pdf-theme.yml
```

Alternatively, you can also use the ORT docker image

```shell
docker run ghcr.io/oss-review-toolkit/ort report \
  -i <scanner-output-dir>/scanner-result.yml \
  -o <reporter-output-dir> \
  -f PdfTemplate \
  --report-option PdfTemplate=template.id=<template-id> \
  --report-option PdfTemplate=pdf.theme.file=pdf-theme.yml
```

If you want to add your own custom fonts in the AsciiDoc PDF theme file using a [relative path][Asciidoctor-fonts], you must specify the directory containing the fonts as a report-specific option as shown below

```
--report-option PdfTemplate=pdf.fonts.dir=path/to/fonts/
```

where `path/to/fonts` is the relative path to the font directory from the base execution directory.

#### Reporter PDF template options

* `pdf.theme.file`:
  A path to an AsciiDoc PDF theme file.
  Only used with the "pdf" backend.
* `pdf.fonts.dir`:
  A path to a directory containing custom fonts.
  Only used with the "pdf" backend.

### Generate a HTML report

You can generate an HTML report from the Freemarker template using [Asciidoctor's HTML converter][Asciidoctor-html].

```shell
cli/build/install/ort/bin/ort report \
  -i <scanner-output-dir>/scanner-result.yml \
  -o <reporter-output-dir> \
  -f HtmlTemplate \
  --report-option HtmlTemplate=template.id=<template-id>
```

### Generate a DocBook

You can generate a [DocBook][DocBook] report from the Freemarker template using [Asciidoctor's DocBook converter][Asciidoctor-docbook].

```shell
cli/build/install/ort/bin/ort report \
  -i <scanner-output-dir>/scanner-result.yml \
  -o <reporter-output-dir> \
  -f DocBookTemplate \
  --report-option DocBookTemplate=template.id=[template-id]
```

### Generate a Man page

You can generate a ManPage report from the Freemarker template using [Asciidoctor's ManPage converter][Asciidoctor-manpage].

```shell
cli/build/install/ort/bin/ort report \
  -i <scanner-output-dir>/scanner-result.yml \
  -o <reporter-output-dir> \
  -f ManPageTemplate \
  --report-option DocBookTemplate=template.id=<template-id>
```

## Generate plain text attribution notices

The [`PlainTextTemplateReporter`][PlainTextTemplateReporter] enables customization of the generated attribution notices with [Apache Freemarker][Freemarker] templates and producing any other arbitrary plain text files, such as `.adoc` files.

ORT provides two templates that can be used as a base for creating your custom attribution / open source notices:

* [default][NOTICE_DEFAULT-ftl]:
  Prints a summary of all licenses found in the project itself and lists licenses for all dependencies separately.
* [summary][NOTICE_SUMMARY-ftl]:
  Prints a summary of all licenses found in the project itself and all dependencies.

See the code comments within the templates for detailed explanations of how they work.

### Command line

To use one or both of the provided templates pass the `template.id`s to the *PlainTextTemplate* reporter:

```shell
cli/build/install/ort/bin/ort report \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats CycloneDX,PlainTextTemplate,SpdxDocument,WebApp \
  -O PlainTextTemplate=template.id=NOTICE_DEFAULT,NOTICE_SUMMARY
```

Alternatively, you can also use the ORT docker image

```shell
docker run ghcr.io/oss-review-toolkit/ort report \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats CycloneDX,PlainTextTemplate,SpdxDocument,WebApp \
  -O PlainTextTemplate=template.id=NOTICE_DEFAULT,NOTICE_SUMMARY
```

To use one or more custom templates, pass the `template.path` parameters to the *PlainTextTemplate* reporter. The filename of each template is used as the output filename, with the `.ftl` suffix removed. For example, the following command would produce both a `.md` and an `.adoc` file.

```shell
cli/build/install/ort/bin/ort report \
  -i <evaluator-output-path>/evaluation-result.yml \
  -o <reporter-output-path> \
  --report-formats PlainTextTemplate,StaticHtml,WebApp
  -O PlainTextTemplate=template.path=<ort-configuration-path>/custom1.md.ftl,<ort-configuration-path>/custom2.adoc.ftl
```

The `template.id` and `template.path` options can be combined to generate multiple notices files.

## Related resources

* Code
  * [plugins/reporters/asciidoc/src/main/kotlin](https://github.com/oss-review-toolkit/ort/tree/main/plugins/reporters/asciidoc/src/main/kotlin)
  * [plugins/reporters/freemarker/src/main/kotlin/FreemarkerTemplateProcessor.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/reporters/freemarker/src/main/kotlin/FreemarkerTemplateProcessor.kt)
  * [plugins/reporters/freemarker/src/main/kotlin/PlainTextTemplateReporter.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/reporters/freemarker/src/main/kotlin/PlainTextTemplateReporter.kt)
* Examples
  * [examples/asciidoctor-pdf-theme.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/asciidoctor-pdf-theme.yml)
  * [asciidoctor-pdf-theme.yml within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/blob/main/asciidoctor-pdf-theme.yml)
* Reference
  * [Reporter CLI --report-formats option](../cli/reporter.md#configuration-options)

[AsciiDoc]: https://asciidoc.org
[Asciidoctor-docbook]: https://docs.asciidoctor.org/asciidoctor/latest/docbook-backend
[Asciidoctor-fonts]: https://docs.asciidoctor.org/pdf-converter/latest/theme/font-support/
[Asciidoctor-html]: https://docs.asciidoctor.org/asciidoctor/latest/html-backend
[Asciidoctor-manpage]: https://docs.asciidoctor.org/asciidoctor/latest/manpage-backend
[Asciidoctor-PDF-theming]: https://docs.asciidoctor.org/pdf-converter/latest/theme/
[AsciidoctorJ]: https://github.com/asciidoctor/asciidoctorj
[AsciidoctorJ-PDF]: https://github.com/asciidoctor/asciidoctorj-pdf
[DocBook]: https://docbook.org
[Freemarker]: https://freemarker.apache.org
[NOTICE_DEFAULT-ftl]: https://github.com/oss-review-toolkit/ort/blob/main/plugins/reporters/freemarker/src/main/resources/templates/plain-text/NOTICE_DEFAULT.ftl
[NOTICE_SUMMARY-ftl]: https://github.com/oss-review-toolkit/ort/blob/main/plugins/reporters/freemarker/src/main/resources/templates/plain-text/NOTICE_SUMMARY.ftl
[PlainTextTemplateReporter]: https://github.com/oss-review-toolkit/ort/blob/main/plugins/reporters/freemarker/src/main/kotlin/PlainTextTemplateReporter.kt
