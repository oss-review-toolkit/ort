# How to define package sources

When ORT reports VCS or artifact download errors (for example in the WebApp) after running the [Scanner][scanner], those errors usually mean the [Downloader][downloader] couldn't fetch a package's sources or artifacts. Causes include missing or incorrect VCS metadata, artifact registries not or no longer available, or closed-source packages with no published sources.

To fix these issues, you can define package sources using [package curations][package-curations].

## Defining the source artifact for a released package

To specify the source artifact URL for a package, create a [package curation][package-curations]:

```yaml
- id: "Maven:com.example:library:1.0.0"
  curations:
    comment: "Source artifact not in Maven Central, using GitHub release."
    source_artifact:
      url: "https://github.com/example/library/archive/refs/tags/v1.0.0.tar.gz"
      hash:
        value: "abc123def456"
        algorithm: "SHA-256"
```

## Defining the code repository for a released package

To specify the VCS (source repository) for a package:

```yaml
- id: "NPM::example-lib:2.0.0"
  curations:
    comment: "VCS info missing from package metadata."
    vcs:
      type: "Git"
      url: "https://github.com/example/example-lib.git"
      revision: "v2.0.0"
```

## Defining no sources available for a package

For closed-source or proprietary packages where no sources are available, set `source_code_origins` to an empty list:

```yaml
- id: "Maven:com.vendor:proprietary-sdk:3.0.0"
  curations:
    comment: "Proprietary SDK with no published sources."
    source_code_origins: []
```

## Related resources

* Reference
  * [Package curations][package-curations]
  * [Downloader CLI][downloader]

[downloader]: ../reference/cli/downloader.md
[scanner]: ../reference/cli/scanner.md
[package-curations]: ../reference/configuration/package-curations.md
