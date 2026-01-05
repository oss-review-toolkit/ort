# Generating SBOMs

This is part of the [ORT walkthrough tutorial](index.md). Make sure you've completed the [running policy checks](running-policy-checks.md) step before continuing.

The [Reporter] generates various output formats from ORT results. Throughout this tutorial, we've used the WebApp format for interactive exploration. Now you'll generate SBOMs (Software Bill of Materials) in industry-standard formats like CycloneDX and SPDX that you can share with customers, use for compliance documentation, or feed into other security tools.

## Generating SBOMs

```shell
docker run --rm \
  -v "$(pwd)/todo_list_rust":/workspace \
  -v "$(pwd)/ort-config":/home/ort/.ort/config \
  -v "$(pwd)/ort-output":/ort-output \
  ghcr.io/oss-review-toolkit/ort:76.0.0 \
  report \
    --ort-file /ort-output/evaluation-result.yml \
    --output-dir /ort-output \
    --report-formats CycloneDx,SpdxDocument,WebApp \
    -O CycloneDX=output.file.formats=json,xml \
    -O SpdxDocument=outputFileFormats=JSON,YAML
```

New options:

| Option             | Description                                          |
| ------------------ | ---------------------------------------------------- |
| `--report-formats` | Which report format(s) to generate                   |
| `-O`               | Format-specific options (e.g., output file formats)  |

You should see output like this:

```
Looking for ORT configuration in the following file:
        /home/ort/.ort/config/config.yml (does not exist)

Generating 'CycloneDX' report(s) in thread 'DefaultDispatcher-worker-3'...
Generating 'SpdxDocument' report(s) in thread 'DefaultDispatcher-worker-4'...
Generating 'WebApp' report(s) in thread 'DefaultDispatcher-worker-2'...
Successfully created 'CycloneDX' report at '/ort-output/bom.cyclonedx.json'.
Successfully created 'CycloneDX' report at '/ort-output/bom.cyclonedx.xml'.
Generating 'CycloneDX' report(s) took 184.287917ms.
Successfully created 'SpdxDocument' report at '/ort-output/bom.spdx.json'.
Successfully created 'SpdxDocument' report at '/ort-output/bom.spdx.yml'.
Generating 'SpdxDocument' report(s) took 288.578625ms.
Successfully created 'WebApp' report at '/ort-output/scan-report-web-app.html'.
Generating 'WebApp' report(s) took 313.968916ms.
Created 3 of 3 report(s) in 318.107458ms.
```

This generates:

* **CycloneDX**: `bom.cyclonedx.json` and `bom.cyclonedx.xml` - A widely-used SBOM format, particularly in security contexts
* **SPDX**: `bom.spdx.json` and `bom.spdx.yml` - The ISO/IEC standard for SBOMs
* **WebApp**: `scan-report-web-app.html` - The interactive report we've been using

## What's next

Congratulations! You've completed the ORT walkthrough. You now know how to:

* Analyze dependencies with the Analyzer
* Visualize results with the WebApp report
* Scan for licenses with the Scanner
* Check for vulnerabilities with the Advisor
* Apply policy rules with the Evaluator
* Generate SBOMs with the Reporter

To use ORT on your own projects, check out the *Getting Started* section for [installation](../../getting-started/installation.md) and [CI integration](../../getting-started/ci-integrations.md) options.

## Related resources

* How-to guides
  * [How to generate SBOMs - whether CycloneDX or SPDX](../../how-to-guides/how-to-generate-sboms.md)
* Reference
  * [Reporter CLI][reporter]
  * [Reporter templates - to generate custom notices and reports](../../reference/configuration/reporter-templates.md)

[reporter]: ../../reference/cli/reporter.md
