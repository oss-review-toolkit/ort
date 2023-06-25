# The `reporter.how-to-fix-text-provider.kts` file

The `reporter.how-to-fix-text-provider.kts` file enables the injection of how-to-fix texts in Markdown format for ORT
issues into the reports.

You can use the [example how-to-fix-text-provider](../../examples/example.how-to-fix-text-provider.kts) as the base
script file to create your custom how-to-fix messages in the generated reports.

## Command Line

To use a `*.how-to-fix-text-provider.kts` file, put it to `$ORT_CONFIG_DIR/reporter.how-to-fix-text-provider.kts` or
pass it via the `--how-to-fix-text-provider-script` option to the *reporter*:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats StaticHtml,WebApp
  --how-to-fix-text-provider-script example.how-to-fix-text-provider.kts
```
