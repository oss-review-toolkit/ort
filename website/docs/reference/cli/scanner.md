# Scanner CLI Reference

The *ORT Scanner* wraps underlying license / copyright scanners with a common API so all supported scanners can be used in the same way to easily run them and compare their results.
If passed an ORT result file with an [Analyzer] result (`-i`), the *Scanner* will automatically download the sources of the dependencies via the [Downloader] and scan them afterward.

We recommend using ORT with any of the following scanners, as their integrations have been extensively tested (listed in alphabetical order):

* [FossID](https://fossid.com/) (snippet scanner, commercial)
* [ScanCode](https://github.com/aboutcode-org/scancode-toolkit)

Additionally, the following reference implementations are available (also in alphabetical order):

* [Askalono](https://github.com/jpeddicord/askalono)
* [Licensee](https://github.com/licensee/licensee)
* [SCANOSS](https://www.scanoss.com/) (snippet scanner)

For a comparison of some of these options, check out this [Bachelor Thesis](https://osr.cs.fau.de/2019/08/07/final-thesis-a-comparison-study-of-open-source-license-crawler/).

## Usage

```
ort scan [<options>]
```

## Output Options

* `-o`, `--output-dir=<value>` - The directory to write the ORT result file with scan results to.
* `-f`, `--output-formats=(JSON|YAML)` - The list of output formats to be used for the ORT result file(s). (default: YAML)

## Configuration Options

* `--resolutions-file=<value>` - A file containing issue and rule violation resolutions. (default: ~/tsteenbe/.ort/config/resolutions.yml)

## Options

* `-i`, `--ort-file=<value>` - An ORT result file with an analyzer result to use. Source code is downloaded automatically if needed.
* `-l`, `--label=<value>` - Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple times. For example: `--label distribution=external`.
* `-s`, `--scanners=<value>` - A comma-separated list of scanners to use. Possible values are: [Askalono, DOS, FossId, Licensee, ScanCode, SCANOSS] (default: ScanCode)
* `--project-scanners=<value>` - A comma-separated list of scanners to use for scanning the source code of projects. By default, projects and packages are scanned with the same scanners as specified by `--scanners`. Possible values are: [Askalono, DOS, FossId, Licensee, ScanCode, SCANOSS]
* `--package-types=(PACKAGE|PROJECT)` - A comma-separated list of the package types from the ORT file's analyzer result to limit scans to. (default: [PACKAGE, PROJECT])
* `--skip-excluded` - Do not scan excluded projects or packages. Works only with the `--ort-file` parameter. (deprecated)
* `-h`, `--help` - Show this message and exit.

## Storage backends configurations

To not download or scan any previously scanned sources again, or to reuse scan results generated via other services, the *ORT Scanner* can be configured to use so-called storage backends.
Before processing a package, it checks whether compatible scan results are already available in one of the storages declared.
If this is the case, they are fetched and reused.
Otherwise, the package's source code is downloaded and scanned.
Afterward, the new scan results can be put into a storage for later reuse.

This reuse of scan results can actually happen on a per-repository (`type: "PROVENANCE_BASED"`) or per-package (`type: "PACKAGE_BASED"`) basis.
For all storages based on `FileBasedStorage` or `PostgresStorage`, the scanner wrapper groups packages by their provenance before scanning.
This ensures that a certain revision of a VCS repository is only scanned once, and the results are shared for all packages that are provided by this repository.
In the case of repositories that provide a lot of packages, this can bring a significant performance improvement.

It is possible to configure multiple storages to read scan results from or to write scan results to.
For reading, the declaration order in the configuration is important, as the scanner queries the storages in this order and uses the first matching result.
This allows a fine-grained control over the sources from which existing scan results are loaded.
For instance, you can specify that the scanner checks first whether results for a specific package are available in a local storage on the file system.
If this is not the case, it can look up the package in a Postgres database.
If this does not yield any results either, a service like [ClearlyDefined](https://clearlydefined.io) can be queried.
Only if all of these steps fail, the scanner has to actually process the package.

When storing a newly generated scan result, the scanner invokes all the storages declared as writers.
The storage operation is considered successful if all writer storages could successfully persist the scan result.

The configuration of storage backends is located in the [ORT configuration file](../configuration/index.md#ort-configuration-file).
(For the general structure of this file and the set of options available refer to the [reference configuration][reference-yml].)
The file has a section named *storages* that lists all the storage backends and assigns them a name.
Each storage backend is of a specific type and needs to be configured with type-specific properties.
The different types of storage backends supported by ORT are described below.

After the declaration of the storage backends, the configuration file has to specify which ones of them the scanner should use for looking up existing scan results or to store new results.
This is done in two list properties named *storageReaders* and *storageWriters*.
The lists reference the names of the storage backends declared in the *storages* section.
The scanner invokes the storage backends in the order they appear in the lists; so for readers, this defines a priority for look-up operations.
Each storage backend can act as a reader; however, some types do not support updates and thus cannot serve as writers.
If a storage backend is referenced both as reader and writer, the scanner creates only a single instance of this storage class.

The following subsections describe the different storage backend implementations supported by ORT.
Note that the name of a storage entry (like `fileBasedStorage`) can be freely chosen.
That name is then used to refer to the storage from the `storageReaders` and `storageWriters` sections.

### Local file storage

By default, the *Scanner* stores scan results on the local file system in the current user's home directory (i.e. `~/.ort/scanner/scan-results`) for later reuse.
Settings like the storage directory and the compression flag can be customized in the ORT configuration file (`-c`) with a respective storage configuration:

```yaml
ort:
  scanner:
    storages:
      fileBasedStorage:
        backend:
          localFileStorage:
            directory: "/tmp/ort/scan-results"
            compression: false

    storageReaders: ["fileBasedStorage"]
    storageWriters: ["fileBasedStorage"]
```

### HTTP storage

Any HTTP file server can be used to store scan results.
Custom headers can be configured to provide authentication credentials.
For example, to use Artifactory to store scan results, use the following configuration:

```yaml
ort:
  scanner:
    storages:
      artifactoryStorage:
        backend:
          httpFileStorage:
            url: "https://artifactory.domain.com/artifactory/repository/scan-results"
            headers:
              X-JFrog-Art-Api: "api-token"

    storageReaders: ["artifactoryStorage"]
    storageWriters: ["artifactoryStorage"]
```

### PostgreSQL storage

To use PostgreSQL for storing scan results you need at least version 9.4, create a database with the `client_encoding` set to `UTF8`, and a configuration like the following:

```yaml
ort:
  scanner:
    storages:
      postgresStorage:
        connection:
          url: "jdbc:postgresql://example.com:5444/database"
          schema: "public"
          username: "username"
          password: "password"
          sslmode: "verify-full"

    storageReaders: ["postgresStorage"]
    storageWriters: ["postgresStorage"]
```

The database needs to exist.
If the schema is set to something else than the default of `public`, it needs to exist and be accessible by the configured username.

The *Scanner* will itself create a table called `scan_results` and store the data in a [jsonb](https://www.postgresql.org/docs/current/datatype-json.html) column.

If you do not want to use SSL set the `sslmode` to `disable`, other possible values are explained in the [documentation](https://jdbc.postgresql.org/documentation/ssl/#configuring-the-client).
For other supported configuration options see [ScanStorageConfiguration.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/ScanStorageConfiguration.kt).

### ClearlyDefined storage

[ClearlyDefined](https://clearlydefined.io) is a service offering curated metadata for Open Source components.
This includes scan results that can be used by ORT's *Scanner* tool (if they have been generated by a compatible scanner version with a suitable configuration).
This storage backend queries the ClearlyDefined service for scan results of the packages to be processed.
It is read-only; so it will not upload any new scan results to ClearlyDefined.
In the configuration the URL of the ClearlyDefined service needs to be set:

```yaml
ort:
  scanner:
    storages:
      clearlyDefined:
        serverUrl: "https://api.clearlydefined.io"

    storageReaders: ["clearlyDefined"]
```

## Related resources

* Code
  * [plugins/commands/scanner/src/main/kotlin/ScanCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/scanner/src/main/kotlin/ScanCommand.kt)
  * [model/src/main/kotlin/config/ScanStorageConfiguration.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/ScanStorageConfiguration.kt)
* How-to guides
  * [How to correct licenses](../../how-to-guides/how-to-correct-licenses.md)
  * [How to correct copyrights](../../how-to-guides/how-to-correct-copyrights.md)
  * [How to make snippet choices](../../how-to-guides/how-to-make-snippet-choices.md)
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
* Tutorials
  * [Scanning for copyrights and licenses](../../tutorials/walkthrough/scanning-for-copyrights-and-licenses.md)

[analyzer]: analyzer.md
[downloader]: downloader.md
[reference-yml]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml
