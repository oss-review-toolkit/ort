# How to download sources for projects and dependencies

Use ORT to download and archive containing the sources for your project and all its dependencies. This is useful to:

* Meet license obligations: Provide corresponding source code for open source dependencies when required by licenses (for example, GPL).
* Support security reviews: Scan project and dependency sources for credentials, secrets, or insecure coding patterns.
* Satisfy regulations: Comply with rules such as the EU Cyber Resilience Act (CRA).
* Ensure long-term access and business continuity: Keep an archived copy for business continuity, audits, or long-term access.

## Downloading sources

First, run the [Analyzer] to determine your project's dependencies:

```shell
ort analyze \
  -i /path/to/project \
  -o /path/to/ort-result
```

Then, run the [Downloader] to download all sources:

```shell
ort download \
  --ort-file /path/to/ort-result/analyzer-result.yml \
  --output-dir /path/to/sources
```

This downloads the source code for your project and all its dependencies into directories organized by package manager and package name.

To skip downloading [excluded parts][ort-yml-excludes] of your project, run:

```shell
ort download \
  --ort-file analyzer-result.yml \
  --output-dir /path/to/sources
  --skip-excluded
```

## Creating a single project archive

To download and bundle all sources into one ZIP file, use `--archive-all`:

```shell
ort download \
  --ort-file analyzer-result.yml \
  --archive-all \
  --output-dir /path/to/archive
```

This creates an `archive.zip` file inside `/path/to/archive/` containing all source code.

## Creating individual package archives

To create separate ZIP files for each package, use `--archive`:

```shell
ort download \
  --ort-file /path/to/ort-result/analyzer-result.yml \
  --archive \
  --output-dir /path/to/archive
```

Above command will create ZIP archives in the format `Type-Namespace-Name-Version.zip` (e.g., `Maven-com.example-library-1.0.0.zip`) for each package.

## Related resources

* Reference
  * [Analyzer CLI][analyzer]
  * [Downloader CLI][downloader]

[analyzer]: ../reference/cli/analyzer.md
[downloader]: ../reference/cli/downloader.md
[ort-yml-excludes]: ../reference/configuration/ort-yml.md#excludes
