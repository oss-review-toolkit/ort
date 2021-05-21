# AsciiDocTemplateReporter

The AsciiDocTemplateReporter creates PDF files using a combination of [Apache Freemarker][1] templates and [AsciiDoc][2]
with [AsciidoctorJ][3] as Java interface and [AsciidoctorJ PDF][4] as PDF file generator.
For each Freemarker template provided using the options described below a separate intermediate file is created that can be
processed by AsciidoctorJ. If no options are provided, the "disclosure_document" template is used, and if security
vulnerability information is available also the "vulnerability_report" template.

After the intermediate files are generated, they are processed by AsciidoctorJ or to be more precise by its PDF
implementation AsciidoctorJ PDF. A PDF theme can be handed over to AsciidoctorJ PDF in which properties like fonts or
images displayed in the PDF can be adjusted; see the [Theme Guide][5].
The path to this theme can be set in the options as described below.
Note that only one theme can be set that is used for all given templates. If no theme is given or the given path to
the theme file does not exist, an in-built theme of AsciidoctorJ PDF is used.

## Report options

* `template.id`: A comma-separated list of IDs of templates provided by ORT. Currently, the "disclosure_document" and
                 "vulnerability_report" templates are available.
* `template.path`: A comma-separated list of paths to template files provided by the user.
* `backend`: The name of the AsciiDoc backend to use, like "html". Defaults to "pdf". As a special case, the "adoc"
             fake backend is used to indicate that no backend should be used but the AsciiDoc files should be kept.
* `pdf.theme.file`: A path to an AsciiDoc PDF theme file. Only used with the "pdf" backend.
* `pdf.fonts.dir`: A path to a directory containing custom fonts. Only used with the "pdf" backend.

## Command Line

To use the _AsciiDocTemplateReporter_ report format with a Freemarker template and an AsciiDoc PDF theme, pass it as
the template.id and pdf-them.path, respectively, via the --report-option (or -O) option to the _report_ command:

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f AsciiDoc
  --report-option AsciiDocTemplate=template.id=[template-id]
  --report-option AsciiDocTemplate=pdf.theme.file=pdf-theme.yml
```

If you want to add your own custom fonts in the AsciiDoc PDF theme file using a [relative path][6],
you need to add the directory in which the fonts are located as a report-specific option like

    --report-option AsciiDocTemplate=pdf.fonts.dir=path/to/fonts/

where `path/to/fonts` is the relative path to the font directory from the base execution directory.

[1]: https://freemarker.apache.org
[2]: https://asciidoc.org/
[3]: https://github.com/asciidoctor/asciidoctorj
[4]: https://github.com/asciidoctor/asciidoctorj-pdf
[5]: https://github.com/asciidoctor/asciidoctor-pdf/blob/master/docs/theming-guide.adoc
[6]: https://github.com/asciidoctor/asciidoctor-pdf/blob/master/docs/theming-guide.adoc#configuring-the-font-search-path
