# The `copyright-garbage.yml` file

The `copyright-garbage.yml` file allows to define which Copyright statements are to be considered as garbage, like any
invalid findings from a scanner. This can be done by literal strings or regular expression patterns. The _evaluator_ and
_reporter_ take the file as optional input. See the [copyright-garbage.yml example](../examples/copyright-garbage.yml)
as a base to get started.

## Command Line

Either create a file at the default location at `$ORT_CONFIG_DIR/copyright-garbage.yml`, or pass a custom file via the
`--copyright-garbage-file` option of the _evaluator_ or _reporter_. For example:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats PlainTextTemplate,StaticHtml,WebApp
  --copyright-garbage-file $ORT_CONFIG_DIR/copyright-garbage.yml
```
