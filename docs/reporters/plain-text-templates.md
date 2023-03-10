# Notice Templates

The [`PlainTextTemplateReporter`](../../reporter/src/main/kotlin/reporters/freemarker/PlainTextTemplateReporter.kt)
enables customization of the generated open source notices with [Apache Freemarker](https://freemarker.apache.org/)
templates and producing any other arbitrary plain text files, such as `.adoc` files.

ORT provides two templates that can be used as a base for creating your custom open source notices:

* [default](../../reporter/src/main/resources/templates/plain-text/NOTICE_DEFAULT.ftl): Prints a summary of all licenses 
  found in the project itself and lists licenses for all dependencies separately.
* [summary](../../reporter/src/main/resources/templates/plain-text/NOTICE_SUMMARY.ftl): Prints a summary of all licenses
  found in the project itself and all dependencies.

See the code comments in the templates for how they work.

## Command Line

To use one or both of the provided templates pass the `template.id`s to the _PlainTextTemplate_ reporter_

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats PlainTextTemplate,StaticHtml,WebApp
  -O PlainTextTemplate=template.id=NOTICE_DEFAULT,NOTICE_SUMMARY
```

To use one or more custom templates pass the `template.path`s to the _PlainTextTemplate_ reporter.
The filename of the template is used as the output filename with the `.ftl` suffix removed. For example, the following
command would produce a `.md` and an `.adoc`:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats PlainTextTemplate,StaticHtml,WebApp
  -O PlainTextTemplate=template.path=[ort-configuration-path]/custom1.md.ftl,[ort-configuration-path]/custom2.adoc.ftl
```

The `template.id` and `template.path` options can be combined to generate multiple notice files.
