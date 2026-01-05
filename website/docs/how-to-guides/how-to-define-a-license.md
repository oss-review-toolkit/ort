# How to define a license

You can add custom licenses to ORT to cover cases such as:

* Defining the license(s) used by your organization.
* Adding a license that a scanner (e.g., [ScanCode]) does not yet recognize.

⚠️ Defining a license because a scanner didn't detect it should be a stop-gap.
Report the license to the scanner's creator so it can be added to the tool's detection.

## Create a license text file

In `$ORT_CONFIG_DIR/custom-license-text/` with a text editor of your choice create a plain text file with as name the [SPDX license id][spdx-license-id] for your license. Say we create a file `LicenseRef-myorg-product-xyz-1.0` with the text below:

```
Copyright (c) 2026 Example Inc.

Permission is hereby granted, subject to the following conditions,
to any person or entity ("Licensee") obtaining a copy of the object code
form of the software ("Software"):

    Grant: Example Inc. grants Licensee a non-exclusive, non-transferable license
    to use the Software for commercial purposes, on up to 10 devices, and to distribute
    it internally within Licensee’s organization.

    Restrictions: Licensee may not modify, reverse engineer, decompile, sublicense,
    publicly distribute, or create derivative works of the Software, nor remove or
    alter any copyright or proprietary notices.

    Fees: Use is licensed for the fee agreed in writing between the parties.
    No implied right to a refund.

    Warranty: THE SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND,
    EXPRESS OR IMPLIED, INCLUDING MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
    OR NON-INFRINGEMENT.

    Liability: TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, IN NO EVENT SHALL
    EXAMPLE INC. BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, OR CONSEQUENTIAL DAMAGES,
    OR FOR LOSS OF PROFITS, DATA, OR BUSINESS, AND EXAMPLE INC.’S TOTAL LIABILITY SHALL
    NOT EXCEED THE AMOUNT PAID FOR THE LICENSE.
```

## Classify the license

Use [license classifications][license-classifications] to categorize the `LicenseRef-org-example-1.0` license.
Edit `$ORT_CONFIG_DIR/license-classifications.yml`, define a new category if no suitable is present then classify your new license by adding it under `categorizations`

```
categories:
- name: "commercial"
- name: "copyleft"
- name: "copyleft-limited"
- name: "include-in-notice-file"
- name: "include-source-code-offer-in-notice-file"
- name: "permissive"
categorizations:
- id: "LicenseRef-myorg-product-xyz-1.0"
  categories:
  - "permissive"
  - "include-in-notice-file"
```

## Pass custom license directory to Reporter

When generating reports, pass the custom license texts directory via the `--custom-license-texts-dir` option to the [Reporter] so license text can be included in outputs (NOTICE files, SPDX, etc.):

```shell
cli/build/install/ort/bin/ort report
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats CycloneDX,SpdxDocument,PlainTextTemplate,WebApp \
  --custom-license-texts-dir $ORT_CONFIG_DIR/custom-license-text/
  -O PlainTextTemplate=template.id=NOTICE_BY_PACKAGE
```

## Related resources

* Code
  * [src/main/kotlin/DirLicenseFactProvider.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/license-fact-providers/dir/src/main/kotlin/DirLicenseFactProvider.kt)
  * [model/src/main/resources/reference.yml][reference-yml]
* Examples
  * [custom-license-texts/ within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/tree/main/custom-license-texts)
  * [license-classifications.yml within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/blob/main/license-classifications.yml)
  * ['licenseFactProviders' in src/main/resources/reference.yml](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml)
* Reference
  * [ORT configuration custom license text directory](../reference/configuration/index.md#custom-license-texts-directory)
  * [License classifications][license-classifications]
  * [ORT Reporter CLI --custom-license-texts-dir option](../reference/cli/reporter.md#options)

[license-classifications]: ../reference/configuration/license-classifications.md
[reference-yml]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml
[reporter]: ../reference/cli/reporter.md
[scancode]: https://github.com/aboutcode-org/scancode-toolkit
[spdx-license-id]: https://spdx.dev/learn/handling-license-info/
