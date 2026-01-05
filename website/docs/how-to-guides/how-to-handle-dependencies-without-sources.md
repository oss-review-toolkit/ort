# How to handle dependencies without sources

Some packages have no sources — e.g., metadata-only packages (Maven BOMs), proprietary SDKs, or packages whose source repository moved or whose source artifacts were never published to the registry. This causes the [Scanner] to fail (it needs sources for copyright and file scans). Use curations to point ORT to available sources; if that’s not possible, mark the issue as resolved with a resolution so it doesn't block your ORT workflow.

## Metadata-only packages (no source code files to scan)

Metadata-only (binary) packages do not contain source code and only provide metadata, for example:

* Maven BOM (Bill of Materials) files
* Platform or version-constraint packages
* Virtual packages that only pull in other dependencies

Mark these packages using a [package curation][package-curations] with `is_metadata_only: true` to tell ORT to skip downloading and scanning.

```yaml
- id: "Maven:org.example:example-bom:1.0.0"
  curations:
    comment: "BOM file that only defines dependency versions, no actual code."
    is_metadata_only: true
```

## Closed-source or unavailable-source packages

For (binary) packages with no published sources, add a [package curation][package-curations] with `source_code_origins: []` to tell ORT knows there are no sources to fetch; this prevents ORT from attempting to download or scan these packages.

```yaml
- id: "Maven:com.example.vendor.lib:api-client:1.2.3"
  curations:
    comment: "Close source package from Example Inc. for which no sources are made available."
    source_code_origin: []
```

## Marking unavailable-source errors as resolved

If you cannot fix an unavailable-source error by defining the package's source repository or artifact (see [How to define package sources][how-to-define-package-sources]) then use an [issue resolution][resolutions] to mark these errors as resolved using an [issue resolution][resolutions].

⚠️ The error will still appear in the results but will be marked as resolved, allowing your ORT workflow to proceed.

```yaml
issues:
- message: "Maven:com.vendor:proprietary-sdk:3.0.0.*"
  reason: "CANT_FIX_ISSUE"
  comment: "Proprietary SDK with no published sources."
```

## Related resources

* How-to guides
  * [How to define package sources][how-to-define-package-sources]
* Reference
  * [Package curations][package-curations]
  * [Resolutions][resolutions]
  * [Scanner CLI][scanner]

[how-to-define-package-sources]: how-to-define-package-sources.md
[package-curations]: ../reference/configuration/package-curations.md
[resolutions]: ../reference/configuration/resolutions.md
[scanner]: ../reference/cli/scanner.md
