# AntennaAttributionDocumentReporter

The AntennaAttributionDocumentReporter generates an attribution document in PDF format by leveraging the [Eclipse 
Antenna][1] project. The attribution document can be customized by providing a template bundle JAR, or a directory with 
template PDF files. See [this guide][2] to learn how to create a template bundle JAR file.

In addition to using a template bundle JAR, there is an alternative way to customize the attribution document. For this, 
a directory with template PDF files has to be provided. The PDF file names have to match the following:
 * `cover.pdf`: Template PDF file for the cover page.
 * `copyright.pdf`: Template PDF file for the project copyright page.
 * `content.pdf`: Template PDF file for the packages of the project.
 * `back.pdf`: Template PDF file for the back page.

## Report options

* `template.path`: This option has to point to a template bundle JAR file, or a directory with the template PDF files.
* `template.id`: Specifies the unique ID of the template inside the provided template bundle JAR. Only required if 
`template.path` points to a template bundle JAR file.

## Command Line

To use the _AntennaAttributionDocument_ report format with a template bundle JAR file, pass it as the template.path via 
the --report-option (or -O) option to the _report_ command:

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f AntennaAttributionDocument
  --report-option AntennaAttributionDocument=template.id=[template-id]
  --report-option AntennaAttributionDocument=template.path=[template-bundle-jar-dir]/custom-template.jar
```

To use the _AntennaAttributionDocument_ report format with template PDF files, pass the directory the PDF files are
located in as the template.path via the --report-option (or -O) option to the _report_ command:

```bash
cli/build/install/ort/bin/ort report
  -i [scanner-output-dir]/scanner-result.yml
  -o [reporter-output-dir]
  -f AntennaAttributionDocument
  --report-option AntennaAttributionDocument=template.path=[directory-with-pdf-files-dir]
```

[1]: https://github.com/eclipse/antenna
[2]: https://github.com/eclipse/antenna/blob/master/antenna-documentation/src/site/markdown/template-bundle-development.md
