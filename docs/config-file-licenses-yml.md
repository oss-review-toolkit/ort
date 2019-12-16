# The `licenses.yml` file

The `licenses.yml` file holds a user-defined the categorization of licenses.

You can use [examples/licenses.yml](examples/licenses.yml) as the base configuration file for your scans.

### When to Use

Set *include\_in_notice_file* if the licenses does not require attribution.
Similarly setting include_source_code_offer_in_notice_file to `true` will ensure
a written source code offers is included at the bottom in the notices.

## Command Line

To use the `licenses.yml` file pass it to the `--license-configuration-file` option of the _evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-path]/scan-result.yml
  -o [evaluator-output-path]/evaluator-result.yml
  --output-formats YAML
  --license-configuration-file [ort-configuration-path]/licenses.yml
  --package-curations-file [ort-configuration-path]/curations.yml
  --rules-file [ort-configuration-path]/rules.kts
```