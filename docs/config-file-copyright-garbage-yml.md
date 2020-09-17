# The `copyright-garbage.yml` file

The `copyright-garbage.yml` file allows for removal of incorrect copyright holder detections from the _reporter_ output.

You can use the [copyright-garbage.yml example](../examples/copyright-garbage.yml) as the base file for your scans.

### When to Use

No scanner is perfect and many detect code statements or binary code as copyright holders, `copyright-garbage.yml`
provides a way to clean up such errors across all scans.

## Command Line

To use the `copyright-garbage.yml` file put it to `$ORT_CONFIG_DIR/copyright-garbage.yml` or pass it to the
`--copyright-garbage-file` option of the _reporter_:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats NoticeTemplate,StaticHtml,WebApp
  --copyright-garbage-file $ORT_CONFIG_DIR/copyright-garbage.yml
```
