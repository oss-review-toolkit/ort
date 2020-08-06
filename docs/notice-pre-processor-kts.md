# The `notice-pre-processor.kts` file

The `notice-pre-processor.kts` file enables customization of the generated open source notices
such as headers, footers and the conditional inclusion of a source code offer.

You can use the [notice-pre-processor.kts example](./examples/notice-pre-processor.kts) as the base script file to
create your custom open source notices.

## Command Line

To use the `notice-pre-processor.kts` file pass it to the `--pre-processing-script` option of the _reporter_:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats NoticeByPackage,StaticHtml,WebApp
  --pre-processing-script [ort-configuration-path]/notice-pre-processor.kts
```
