# How to address tool issues

When ORT reports errors during analysis or scanning, you can either fix the underlying problem or resolve the issue if it cannot be fixed.

## Fixing download errors

If an error message contains "DownloadException: Download failed", create a [package curation][package-curations] with the correct source artifact or VCS information:

```yaml
- id: "Maven:com.example:library:1.0.0"
  curations:
    comment: "Source repository moved to new location."
    vcs:
      type: "Git"
      url: "https://github.com/example/library.git"
      revision: "v1.0.0"
```

## Fixing revision errors

If an error message contains "Unable to determine a revision to checkout", ORT found the repository but cannot determine which revision corresponds to the package version. Create a [package curation][package-curations] with the exact revision:

```yaml
- id: "Maven:com.example:library:1.0.0"
  curations:
    comment: "No tag for this version, manually found the correct revision."
    vcs:
      revision: "abc123def456"
```

## Fixing license mapping errors

If an error message contains "could not be mapped to a valid license or parsed as an SPDX expression", create a [package curation][package-curations] with a declared license mapping:

```yaml
- id: "Maven:com.example:library:1.0.0"
  curations:
    comment: "Map non-standard license string to SPDX identifier."
    declared_license_mapping:
      "Apache 2": "Apache-2.0"
```

## Fixing access errors (404)

If the URL no longer exists (e.g., repository moved or deleted), create a [package curation][package-curations] with the correct URL:

```yaml
- id: "Maven:com.example:library:1.0.0"
  curations:
    comment: "Repository moved to new location."
    vcs:
      url: "https://github.com/new-org/library.git"
```

If the URL requires authentication, configure credentials using a `.netrc` file:

```
machine github.com login <username> password <token>
```

ORT reads credentials from `~/.netrc` when accessing private repositories.

If your package manager needs credentials passed via environment variables, add them to [`allowedProcessEnvironmentVariableNames`][config-env-vars] in `config.yml` to ensure they are passed to child processes.

## Resolving unfixable issues

When an issue cannot be fixed (e.g., scanner timeout, unavailable sources), add an [issue resolution][resolutions-issues] to your `.ort.yml` file:

```yaml
resolutions:
  issues:
  - message: "Timeout after .* seconds"
    reason: "SCANNER_ISSUE"
    comment: "Scanner timeout on large file, manually verified no license issues."
```

## Providing guidance in reports

You can provide custom "how to fix" text for issues to help users fix issues themselves using a [HowToFixTextProvider][how-to-fix-text-provider]. See the [example script](https://github.com/oss-review-toolkit/ort/blob/main/examples/example.how-to-fix-text-provider.kts) for details.

## Related resources

* Reference
  * [Package curations][package-curations]
  * [Resolutions][resolutions]
  * [How to fix text provider][how-to-fix-text-provider]

[config-env-vars]: ../reference/configuration/index.md#protecting-environment-variables
[how-to-fix-text-provider]: ../reference/configuration/how-to-fix-text-provider.md
[package-curations]: ../reference/configuration/package-curations.md
[resolutions]: ../reference/configuration/resolutions.md
[resolutions-issues]: ../reference/configuration/resolutions.md#file-format
