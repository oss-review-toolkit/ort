# How to generate SBOMs

ORT can generate Software Bill of Materials (SBOMs) in [CycloneDX][cyclonedx-reporter] and [SPDX][spdx-reporter] formats via the [Reporter].

## Prerequisites

Before you begin, make sure you have the following:

1. ORT is correctly [installed on your system][installation].
2. Save [npm-mime-types-2.1.26-scan-result.json][npm-mime-types-2.1.26-scan-result-json] on your local system.

## Generating CycloneDX and SPDX SBOMs

Generate both formats at once via:

```
ort report \
  -i npm-mime-types-2.1.26-scan-result.json \
  -o . \
  -f CycloneDX,SpdxDocument
```

## Generating a CycloneDX SBOM with a specific version

Generate a CycloneDX 1.5 SBOM instead of the default 1.6:

```
ort report \
  -i npm-mime-types-2.1.26-scan-result.json \
  -o . \
  -f CycloneDX \
  -O CycloneDX=schema.version=1.5
```

## Generating a CycloneDX SBOM in multiple formats

Generate both JSON and XML output:

```
ort report \
  -i npm-mime-types-2.1.26-scan-result.json \
  -o . \
  -f CycloneDX \
  -O CycloneDX=output.file.formats=json,xml
```

## Generating an SPDX SBOM with a specific version

Generate an SPDX 2.2 SBOM instead of the default 2.3:

```
ort report \
  -i npm-mime-types-2.1.26-scan-result.json \
  -o . \
  -f SpdxDocument \
  -O SpdxDocument=spdx.version=SPDX_VERSION_2_2
```

## Generating an SPDX SBOM with custom metadata

Customize the SPDX document with creator information and output formats:

```
ort report \
  -i npm-mime-types-2.1.26-scan-result.json \
  -o . \
  -f SpdxDocument \
  -O SpdxDocument=data.license="LicenseRef-proprietary-example-inc" \
  -O SpdxDocument=creation.info.comment="A mime types SBOM generated using ORT." \
  -O SpdxDocument=creation.info.person="John Doe <john.doe@example.com>" \
  -O SpdxDocument=creation.info.organization="Example Inc." \
  -O SpdxDocument=document.name="Mime Types 2.1.26" \
  -O SpdxDocument=document.comment="SBOM generated to learn ORT." \
  -O SpdxDocument=file.information.enabled=true \
  -O SpdxDocument=output.file.formats=json,yaml \
  -O SpdxDocument=spdx.version=SPDX_VERSION_2_2
```

## Related resources

* Reference
  * [CycloneDX Reporter][cyclonedx-reporter]
  * [Reporter CLI][reporter]
  * [SPDX Document Reporter][spdx-reporter]

[installation]: ../getting-started/installation.md
[npm-mime-types-2.1.26-scan-result-json]: https://raw.githubusercontent.com/oss-review-toolkit/orthw-shell/refs/heads/main/examples/npm-mime-types-2.1.26-scan-result.json
[reporter]: ../reference/cli/reporter.md
[cyclonedx-reporter]: ../reference/plugins/reporters/CycloneDX%20SBOM.md
[spdx-reporter]: ../reference/plugins/reporters/SPDX.md
