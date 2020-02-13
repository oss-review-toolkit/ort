# The `rules.kts` file

The `rules.kts` file  allows you to define custom policy rules that automatically apply to review scan findings.
Rules are written in a Kotlin-based DSL.

For each policy rule violation, you can define 'How to fix' follow-up actions to help users
resolve policy rules violations by themselves.

You can use [examples/rules.kts](examples/rules.kts) as the base script file for your policy rules. Note that this
example depends on the rule sets defined in [examples/licenses.yml](examples/licenses.yml), see the
[licenses.yml docs](config-file-licenses-yml.md).

## Command Line

To use the `rules.kts` file pass it to the `--rules-file` option of the _evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate \
  -i [scanner-output-path]/scan-result.yml
  -o [evaluator-output-path]/evaluator-result.yml \
  --output-formats YAML \
  --license-configuration-file [ort-configuration-path]/licenses.yml \
  --package-curations-file [ort-configuration-path]/curations.yml  \
  --rules-file [ort-configuration-path]/rules.kts
```
