# The Notice templates

The [`NoticeTemplateProcessor`](../reporter/src/main/kotlin/reporters/freemarker/NoticeTemplateReporter.kt) enables customization
of the generated open source notices with [Apache Freemarker](https://freemarker.apache.org/) templates.

ORT provides two templates that can be used as a base for creating your custom open source notices:

* [default](../reporter/src/main/resources/templates/notice/default.ftl): Prints a summary of all licenses found in the
  project itself and lists licenses for all dependencies separately.
* [summary](../reporter/src/main/resources/templates/notice/summary.ftl): Prints a summary of all licenses found in the
  project itself and all dependencies.

See the code comments in the templates for how they work.

## Command Line

To use one or both of the provided templates pass the `template.id`s to the _NoticeTemplate_ reporter_

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats NoticeTemplate,StaticHtml,WebApp
  -O NoticeTemplate=template.id=default,summary
```

To use one or more custom templates pass the `template.path`s to the _NoticeTemplate_ reporter:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats NoticeTemplate,StaticHtml,WebApp
  -O NoticeTemplate=template.path=[ort-configuration-path]/custom1.ftl,[ort-configuration-path]/custom2.ftl
```

The `template.id` and `template.path` options can be combined to generate multiple notice files.
