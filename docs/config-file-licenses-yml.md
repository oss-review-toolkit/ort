# The `licenses.yml` file

The `licenses.yml` file holds a user-defined categorization of licenses.

You can use the [licenses.yml example](./examples/licenses.yml) as the base configuration file for your scans.

### When to Use

Set *include\_in_notice_file* if the license does not require attribution. Similarly setting
*include_source_code_offer_in_notice_file* to `true` will ensure a written source code offer is included in the notices.
Licenses can be assigned to license sets like "permissive" or "public domain" which can be used by the
[rules](file-rules-kts.md) to determine how to handle the license.

## Command Line

To use the `licenses.yml` file pass it to the `--license-configuration-file` option of the _evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-path]/scan-result.yml
  -o [evaluator-output-path]
  --output-formats YAML
  --license-configuration-file [ort-configuration-path]/licenses.yml
  --package-curations-file [ort-configuration-path]/curations.yml
  --rules-file [ort-configuration-path]/rules.kts
```
