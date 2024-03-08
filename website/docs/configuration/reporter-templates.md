# Reporter Templates

## AsciiDoc templates

The AsciiDoc template reporters create reports using a combination of [Apache Freemarker][1] templates and [AsciiDoc][2] with [AsciidoctorJ][3] as Java interface and [AsciidoctorJ PDF][4] as PDF file generator for the [PDF Template Reporter](#pdf).
For each Freemarker template provided using the options described below a separate intermediate file is created that can be processed by AsciidoctorJ.
If no options are provided, the "disclosure_document" template is used, and if security vulnerability information is available also the "vulnerability_report" template.

### General report options

* `template.id`:
  A comma-separated list of IDs for templates built into ORT.
  Currently, the following IDs are supported:
  * "[disclosure_document](../../../plugins/reporters/asciidoc/src/main/resources/templates/asciidoc/disclosure_document.ftl)"
  * "[vulnerability_report](../../../plugins/reporters/asciidoc/src/main/resources/templates/asciidoc/vulnerability_report.ftl)"
  * "[defect_report](../../../plugins/reporters/asciidoc/src/main/resources/templates/asciidoc/defect_report.ftl)"
* `template.path`:
  A comma-separated list of paths to template files provided by the user.

### Supported formats

#### PDF

After the intermediate AsciiDoc files are generated, they are processed by AsciidoctorJ or to be more precise by its PDF implementation AsciidoctorJ PDF.
A PDF theme can be handed over to AsciidoctorJ PDF in which properties like fonts or images displayed in the PDF can be adjusted; see the [Theme Guide][5].
The path to this theme can be set in the options as described below.
Note that only one theme can be set that is used for all given templates.
If no theme is given, or the given path to the theme file does not exist, an in-built theme of AsciidoctorJ PDF is used.

```shell
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f PdfTemplate
  --report-option PdfTemplate=template.id=[template-id]
  --report-option PdfTemplate=pdf.theme.file=pdf-theme.yml
```

If you want to add your own custom fonts in the AsciiDoc PDF theme file using a [relative path][6], you need to add the directory in which the fonts are located as a report-specific option like

```
--report-option PdfTemplate=pdf.fonts.dir=path/to/fonts/
```

where `path/to/fonts` is the relative path to the font directory from the base execution directory.

##### PdfTemplate report options

* `pdf.theme.file`:
  A path to an AsciiDoc PDF theme file.
  Only used with the "pdf" backend.
* `pdf.fonts.dir`:
  A path to a directory containing custom fonts.
  Only used with the "pdf" backend.

#### HTML

Create an HTML report from the Freemarker template using [Asciidoctor's HTML converter][7].

```shell
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f HtmlTemplate
  --report-option HtmlTemplate=template.id=[template-id]
```

#### DocBook

Create a [DocBook][8] report from the Freemarker template using [Asciidoctor's DocBook converter][9].

```shell
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f DocBookTemplate
  --report-option DocBookTemplate=template.id=[template-id]
```

#### Man page

Create a ManPage report from the Freemarker template using [Asciidoctor's ManPage converter][10].

```shell
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f ManPageTemplate
  --report-option DocBookTemplate=template.id=[template-id]
```

### Example

```mdx-code-block
import CodeBlock from '@theme/CodeBlock';
import Example from '!!raw-loader!@site/../examples/asciidoctor-pdf-theme.yml'

<CodeBlock language="yml" title="asciidoctor-pdf-theme.yml">{Example}</CodeBlock>
```

## Plain Text Templates

The [`PlainTextTemplateReporter`](../../../plugins/reporters/freemarker/src/main/kotlin/PlainTextTemplateReporter.kt) enables customization of the generated open source notices with [Apache Freemarker](https://freemarker.apache.org/) templates and producing any other arbitrary plain text files, such as `.adoc` files.

ORT provides two templates that can be used as a base for creating your custom open source notices:

* [default](../../../plugins/reporters/freemarker/src/main/resources/templates/plain-text/NOTICE_DEFAULT.ftl):
  Prints a summary of all licenses found in the project itself and lists licenses for all dependencies separately.
* [summary](../../../plugins/reporters/freemarker/src/main/resources/templates/plain-text/NOTICE_SUMMARY.ftl):
  Prints a summary of all licenses found in the project itself and all dependencies.

See the code comments in the templates for how they work.

### Command Line

To use one or both of the provided templates pass the `template.id`s to the *PlainTextTemplate* reporter:

```shell
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats PlainTextTemplate,StaticHtml,WebApp
  -O PlainTextTemplate=template.id=NOTICE_DEFAULT,NOTICE_SUMMARY
```

To use one or more custom templates pass the `template.path`s to the *PlainTextTemplate* reporter.
The filename of the template is used as the output filename with the `.ftl` suffix removed.
For example, the following command would produce a `.md` and an `.adoc`:

```shell
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats PlainTextTemplate,StaticHtml,WebApp
  -O PlainTextTemplate=template.path=[ort-configuration-path]/custom1.md.ftl,[ort-configuration-path]/custom2.adoc.ftl
```

The `template.id` and `template.path` options can be combined to generate multiple notice files.

[1]: https://freemarker.apache.org
[2]: https://asciidoc.org/
[3]: https://github.com/asciidoctor/asciidoctorj
[4]: https://github.com/asciidoctor/asciidoctorj-pdf
[5]: https://docs.asciidoctor.org/pdf-converter/latest/theme/
[6]: https://docs.asciidoctor.org/pdf-converter/latest/theme/font-support/
[7]: https://docs.asciidoctor.org/asciidoctor/latest/html-backend
[8]: https://docbook.org
[9]: https://docs.asciidoctor.org/asciidoctor/latest/docbook-backend
[10]: https://docs.asciidoctor.org/asciidoctor/latest/manpage-backend
