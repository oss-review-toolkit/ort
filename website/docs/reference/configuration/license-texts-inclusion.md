# License Texts Inclusion

ORT supports two ways to get license texts: [License Fact Providers](#license-fact-providers) and [License File Archives](#license-file-archives).

Several ORT reporters can include license texts in their output. For instance, the [PlainTextTemplateReporter](reporter-templates.md#generate-plain-text-attribution-notices) can incorporate license texts into the generated attribution / open source notices.

## License fact providers

ORT includes multiple license fact provider plugins that can retrieve license texts from various sources.
These plugins can be configured in the [config.yml][reference-yml] file within the `licenseFactProviders`.

For example, to use the [SPDX License Fact Provider](../plugins/license-fact-providers/SPDX%20License%20Fact%20Provider.md), you can add the following configuration.

```yaml
ort:
  licenseFactProviders:
    spdx: {}
```

License fact providers are queried in the order they are defined in the configuration file.
By default, ORT uses the following license fact providers:

* [SPDX License Fact Provider](../plugins/license-fact-providers/SPDX%20License%20Fact%20Provider.md): Provides bundled license texts for all [SPDX licenses](https://spdx.org/licenses/).
* [ScanCode License Fact Provider](../plugins/license-fact-providers/ScanCode%20License%20Fact%20Provider.md): Provides license texts from a local ScanCode installation.
* [Default Directory License Fact Provider](../plugins/license-fact-providers/Default%20Directory%20License%20Fact%20Provider.md): Provides license texts from files the `$ORT_CONFIG_DIR/custom-license-texts` directory.

The latter can be used to add license texts for licenses that are not provided by the other license fact providers.
The files must be named according to the license identifier; see for instance `LicenseRef-ort-SAP-Developer-License-Agreement-3.1`. in below linked [custom-license-texts example](#related-resources).

## License file archives

In some situations using the unmodified license texts from the license fact providers is not sufficient, and it is necessary to include the license texts exactly as they are provided by the package maintainers.
For this use case, ORT supports archiving license files from the sources of the scanned packages.

### Configuration

By default, the ORT scanner create license file archives in the local directory `~/.ort/scanner/archive`.
Alternatively, ORT can be configured to store archives on a remote HTTP server, in an S3 bucket, or in a PostgreSQL database.
For example, to configure ORT to store license file archives on a remote HTTP server, you can add the following configuration to the `config.yml:

```yaml
ort:
  scanner:
    archive:
      fileStorage:
        httpFileStorage:
          url: 'https://example.org/archive'
          query: '?username=user&password=secret'
```

See the [`reference.yml`][reference-yml] for more examples of how to configure file storages or the PostgreSQL storage.

#### License File Patterns

To decide which files to archive, ORT has a [predefined set of license file patterns][LicenseFilePatterns].
To overwrite these patterns, you can add the `licenseFilePatterns` section to the `config.yml` file:

```yaml
ort:
  licenseFilePatterns:
    - 'LICENSE*'
    - 'COPYING*'
```

### Usage

Currently, only the template reporters can make use of the archived license files.
Other reporters, like the CylconeDX or SPDX reporters, only support license fact providers.

See the [NOTICE_DEFAULT.ftl][NOTICE_DEFAULT-ftl] example for how to include archived license files in a template report.
This example uses the following algorithm when including license texts for dependencies:

* List the content of all archive license files for the package.
* Check if any licenses were detected in the source code which were not detected in any of the archived license files, and add the raw license texts from a license fact provider for those licenses.

## Related resources

* Code
  * [model/src/main/kotlin/config/LicenseFilePatterns.kt][LicenseFilePatterns]
  * [model/src/main/kotlin/config/S3FileStorageConfiguration.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/S3FileStorageConfiguration.kt)
  * [plugins/license-fact-providers/api/src/main/kotlin/LicenseFactProvider.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/license-fact-providers/api/src/main/kotlin/LicenseFactProvider.kt)
  * [utils/ort/src/main/kotlin/storage](https://github.com/oss-review-toolkit/ort/tree/main/utils/ort/src/main/kotlin/storage)
* Examples
  * [custom-license-texts directory within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/tree/main/custom-license-texts)
* How-to guides
  * [How to define a license](../../how-to-guides/how-to-define-a-license.md)
* Reference
  * [Reporter CLI](../cli/reporter.md#configuration-options)

[LicenseFilePatterns]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFilePatterns.kt
[NOTICE_DEFAULT-ftl]: https://github.com/oss-review-toolkit/ort/blob/main/plugins/reporters/freemarker/src/main/resources/templates/plain-text/NOTICE_DEFAULT.ftl
[reference-yml]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml
