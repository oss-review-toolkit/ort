# The Custom License Texts Directory

ORT does provide the license texts for all [SPDX licenses](https://spdx.org/licenses/) and for all license references
from [ScanCode](https://github.com/nexB/scancode-toolkit/tree/develop/src/licensedcode/data/licenses). These license
texts will be used when generating open source notices using the [NoticeTemplateReporter](./notice-templates.md).

If you need a license text that is not provided by ORT you can put it in the custom license texts directory. By default,
it is located at `$ORT_CONFIG_DIR/custom-license-texts`. Alternatively, you can pass a different location to the
`--custom-license-texts-dir` option of the _reporter_:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --custom-license-texts-dir $ORT_CONFIG_DIR/custom-license-texts
  --report-formats NoticeTemplate
```

The filenames in this directory need to the match the identifier of the license. For example, to add the license text
for the license "LicenseRef-custom-license" add it to a file with the same name.
