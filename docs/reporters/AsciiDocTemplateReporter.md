# AsciiDoc template reporters

The AsciiDoc template reporters create reports using a combination of [Apache Freemarker][1] templates and [AsciiDoc][2]
with [AsciidoctorJ][3] as Java interface and [AsciidoctorJ PDF][4] as PDF file generator for the [PDF Template Reporter](#PdfTemplate).
For each Freemarker template provided using the options described below a separate intermediate file is created that can be
processed by AsciidoctorJ. If no options are provided, the "disclosure_document" template is used, and if security
vulnerability information is available also the "vulnerability_report" template.

## General report options

* `template.id`: A comma-separated list of IDs of templates provided by ORT. Currently, the "disclosure_document" and
                 "vulnerability_report" templates are available.
* `template.path`: A comma-separated list of paths to template files provided by the user.

## Supported formats

### AsciiDoc

Use the _AdocTemplateReporter_ to create AsciiDoc files from a Freemarker template. This template can be passed to the
_report_ command using --report-option (or -O) with the `template.id` option.

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f AdocTemplate
  --report-option AdocTemplate=template.id=[template-id]
```

### PDF

After the intermediate AsciiDoc files are generated, they are processed by AsciidoctorJ or to be more precise by its PDF
implementation AsciidoctorJ PDF. A PDF theme can be handed over to AsciidoctorJ PDF in which properties like fonts or
images displayed in the PDF can be adjusted; see the [Theme Guide][5].
The path to this theme can be set in the options as described below.
Note that only one theme can be set that is used for all given templates. If no theme is given, or the given path to
the theme file does not exist, an in-built theme of AsciidoctorJ PDF is used.

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f PdfTemplate
  --report-option PdfTemplate=template.id=[template-id]
  --report-option PdfTemplate=pdf.theme.file=pdf-theme.yml
```

If you want to add your own custom fonts in the AsciiDoc PDF theme file using a [relative path][6],
you need to add the directory in which the fonts are located as a report-specific option like

    --report-option PdfTemplate=pdf.fonts.dir=path/to/fonts/

where `path/to/fonts` is the relative path to the font directory from the base execution directory.

#### PdfTemplate report options

* `pdf.theme.file`: A path to an AsciiDoc PDF theme file. Only used with the "pdf" backend.
* `pdf.fonts.dir`: A path to a directory containing custom fonts. Only used with the "pdf" backend.

### HTML

Create an HTML report from the Freemarker template using [Asciidoctor's HTML converter][7].

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f HtmlTemplate
  --report-option HtmlTemplate=template.id=[template-id]
```

### XHTML

Create an XHTML report from the Freemarker template using [Asciidoctor's XTML converter][8].

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f XhtmlTemplate
  --report-option XhtmlTemplate=template.id=[template-id]
```

### DocBook

Create a [DocBook][9] report from the Freemarker template using [Asciidoctor's DocBook converter][10].

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f DocBookTemplate
  --report-option DocBookTemplate=template.id=[template-id]
```

### Man page

Create a ManPage report from the Freemarker template using [Asciidoctor's ManPage converter][11].

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f ManPageTemplate
  --report-option DocBookTemplate=template.id=[template-id]
```

[1]: https://freemarker.apache.org
[2]: https://asciidoc.org/
[3]: https://github.com/asciidoctor/asciidoctorj
[4]: https://github.com/asciidoctor/asciidoctorj-pdf
[5]: https://github.com/asciidoctor/asciidoctor-pdf/blob/master/docs/theming-guide.adoc
[6]: https://github.com/asciidoctor/asciidoctor-pdf/blob/master/docs/theming-guide.adoc#configuring-the-font-search-path
[7]: https://docs.asciidoctor.org/asciidoctor/latest/html-backend
[8]: https://docs.asciidoctor.org/asciidoctor/latest/html-backend/#xhtml
[9]: https://docbook.org
[10]: https://docs.asciidoctor.org/asciidoctor/latest/docbook-backend
[11]: https://docs.asciidoctor.org/asciidoctor/latest/manpage-backend
