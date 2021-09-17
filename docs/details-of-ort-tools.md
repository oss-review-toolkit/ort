# Details of ORT Tools

<a name="analyzer"></a>

[![Analyzer](../logos/analyzer.png)](./analyzer/src/main/kotlin)

The _analyzer_ is a Software Composition Analysis (SCA) tool that determines the dependencies of software projects
inside the specified input directory (`-i`). It does so by querying the detected package managers; **no modifications**
to your existing project source code, like applying build system plugins, are necessary for that to work. The tree of
transitive dependencies per project is written out as part of an
[OrtResult](https://github.com/oss-review-toolkit/ort/blob/master/model/src/main/kotlin/OrtResult.kt) in YAML (or
JSON, see `-f`) format to a file named `analyzer-result.yml` in the specified output directory (`-o`). The output file
exactly documents the status quo of all package-related metadata. It can be further processed or manually edited before
passing it to one of the other tools.

Currently, the following package managers (grouped by the programming language they are most commonly used with) are
supported:

* C / C++
  * [Conan](https://conan.io/) (limitations:
  [receipe vs. source repository](https://github.com/oss-review-toolkit/ort/issues/2037))
  * Also see: [SPDX documents](#analyzer-for-spdx-documents)
* Dart / Flutter
  * [Pub](https://pub.dev/)
* Go
  * [dep](https://golang.github.io/dep/)
  * [Glide](https://github.com/Masterminds/glide)
  * [Godep](https://github.com/tools/godep)
  * [GoMod](https://github.com/golang/go/wiki/Modules) (limitations:
  [no `replace` directive](https://github.com/oss-review-toolkit/ort/issues/4445))
* Haskell
  * [Stack](http://haskellstack.org/)
* Java
  * [Gradle](https://gradle.org/)
  * [Maven](http://maven.apache.org/) (limitations:
  [default profile only](https://github.com/oss-review-toolkit/ort/issues/1774))
* JavaScript / Node.js
  * [Bower](http://bower.io/)
  * [NPM](https://www.npmjs.com/) (limitations:
  [no scope-specific registries](https://github.com/oss-review-toolkit/ort/issues/3741),
  [no peer dependencies](https://github.com/oss-review-toolkit/ort/issues/95))
  * [Yarn](https://yarnpkg.com/)
* .NET
  * [DotNet](https://docs.microsoft.com/en-us/dotnet/core/tools/) (limitations:
  [no floating versions / ranges](https://github.com/oss-review-toolkit/ort/pull/1303#issue-253860146),
  [no target framework](https://github.com/oss-review-toolkit/ort/issues/4083))
  * [NuGet](https://www.nuget.org/) (limitations:
  [no floating versions / ranges](https://github.com/oss-review-toolkit/ort/pull/1303#issue-253860146),
  [no target framework](https://github.com/oss-review-toolkit/ort/issues/4083))
* Objective-C / Swift
  * [Carthage](https://github.com/Carthage/Carthage) (limitation:
  [no `cartfile.private`](https://github.com/oss-review-toolkit/ort/issues/3774))
  * [CocoaPods](https://github.com/CocoaPods/CocoaPods) (limitations:
  [no custom source repositories](https://github.com/oss-review-toolkit/ort/issues/4188))
* PHP
  * [Composer](https://getcomposer.org/)
* Python
  * [PIP](https://pip.pypa.io/) (limitations:
  [Python 2.7 or 3.6 only](https://github.com/oss-review-toolkit/ort/issues/3671))
  * [Pipenv](https://pipenv.readthedocs.io/) (limitations:
  [Python 2.7 or 3.6 only](https://github.com/oss-review-toolkit/ort/issues/3671))
* Ruby
  * [Bundler](http://bundler.io/) (limitations:
  [restricted to the version available on the host](https://github.com/oss-review-toolkit/ort/issues/1308))
* Rust
  * [Cargo](https://doc.rust-lang.org/cargo/)
* Scala
  * [SBT](http://www.scala-sbt.org/)

<a name="analyzer-for-spdx-documents"></a>

If another package manager that is not part of the list above is used (or no package manager at all), the generic
fallback to [SPDX documents](https://spdx.dev/specifications/) can be leveraged to describe
[projects](./analyzer/src/funTest/assets/projects/synthetic/spdx/project/project.spdx.yml) or
[packages](./analyzer/src/funTest/assets/projects/synthetic/spdx/package/libs/curl/package.spdx.yml).

<a name="downloader">&nbsp;</a>

[![Downloader](../logos/downloader.png)](./downloader/src/main/kotlin)

Taking an ORT result file with an _analyzer_ result as the input (`-i`), the _downloader_ retrieves the source code of
all contained packages to the specified output directory (`-o`). The _downloader_ takes care of things like normalizing
URLs and using the [appropriate VCS tool](./downloader/src/main/kotlin/vcs) to checkout source code from version
control.

Currently, the following Version Control Systems (VCS) are supported:

* [CVS](https://en.wikipedia.org/wiki/Concurrent_Versions_System)
* [Git](https://git-scm.com/)
* [Git-Repo](https://source.android.com/setup/develop/repo)
* [Mercurial](https://www.mercurial-scm.org/)
* [Subversion](https://subversion.apache.org/)

<a name="scanner">&nbsp;</a>

[![Scanner](../logos/scanner.png)](./scanner/src/main/kotlin)

This tool wraps underlying license / copyright scanners with a common API so all supported scanners can be used in the
same way to easily run them and compare their results. If passed an ORT result file with an analyzer result (`-i`), the
_scanner_ will automatically download the sources of the dependencies via the _downloader_ and scan them afterwards.

We recommend to use ORT with one of the following scanners as their integration has been thoroughly tested (in
alphabetical order):

* [FossID](https://fossid.com/)
* [ScanCode](https://github.com/nexB/scancode-toolkit)

Additionally, the following reference implementations exist (in alphabetical order):

* [Askalono](https://github.com/amzn/askalono)
* [lc](https://github.com/boyter/lc)
* [Licensee](https://github.com/benbalter/licensee)

For a comparison of some of these, see this
[Bachelor Thesis](https://osr.cs.fau.de/2019/08/07/final-thesis-a-comparison-study-of-open-source-license-crawler/).

## Storage Backends

In order to avoid downloading and/or re-scanning any previously scanned sources or to reuse scan results generated via other
services, the _scanner_ can be configured to use so-called _storage backends_. Before processing a package, it checks
whether compatible scan results are already available in one of the storages declared; if this is the case, they
are fetched and reused. Otherwise, the package's source code is downloaded and scanned. Afterwards, the new scan
results can be put into a storage for later reuse.

It is possible to configure multiple storages to read scan results from or to write scan results to. For reading,
the declaration order in the configuration is important, as the scanner queries the storages in this order and uses
the first matching result. This allows a fine-grained control over the sources, from which existing scan results are
loaded. For instance, you can specify that the scanner checks first whether results for a specific package are
available in a local storage on the file system. If this is not the case, it can look up the package in a Postgres
database. If this does not yield any results either, a service like [ClearlyDefined](https://clearlydefined.io) can be
queried. Only if all of these steps fail, the scanner has to actually process the package.

When storing a newly generated scan result the scanner invokes all the storages declared as writers. The storage
operation is considered successful if all writer storages could successfully persist the scan result.

The configuration of storage backends is located in the [ORT configuration file](#ort-configuration-file). (For the
general structure of this file and the set of options available refer to the
[reference configuration](./model/src/main/resources/reference.conf).) The file has a section named _storages_ that lists
all the storage backends and assigns them a name. Each storage backend is of a specific type and needs to be configured
with type-specific properties. The different types of storage backends supported by ORT are described below.

After the declaration of the storage backends, the configuration file has to specify which ones of them the
scanner should use for looking up existing scan results or to store new results. This is done in two list properties
named _storageReaders_ and _storageWriters_. The lists reference the names of the storage backends declared in the
_storages_ section. The scanner invokes the storage backends in the order they appear in the lists; so for readers,
this defines a priority for look-up operations. Each storage backend can act as a reader; however, some types do not
support updates and thus cannot serve as writers. If a storage backend is referenced both as reader and writer, the
scanner creates only a single instance of this storage class.

The following subsections describe the different storage backend implementations supported by ORT. Note that the name of
a storage entry (like `fileBasedStorage`) can be freely chosen. That name is then used to refer to the storage from the
`storageReaders` and `storageWriters` sections.

### Local File Storage

By default, the _scanner_ stores scan results on the local file system in the current user's home directory (i.e.
`~/.ort/scanner/scan-results`) for later reuse. Settings like the storage directory and the compression flag can be
customized in the ORT configuration file (`-c`) with a respective storage configuration:

```hocon
ort {
  scanner {
    storages {
      fileBasedStorage {
        backend {
          localFileStorage {
            directory = "/tmp/ort/scan-results"
            compression = false
          }
        }
      }
    }

    storageReaders: [
      "fileBasedStorage"
    ]

    storageWriters: [
      "fileBasedStorage"
    ]
  }
}
```

### HTTP Storage

Any HTTP file server can be used to store scan results. Custom headers can be configured to provide authentication
credentials. For example, to use Artifactory to store scan results, use the following configuration:

```hocon
ort {
  scanner {
    storages {
      artifactoryStorage {
        backend {
          httpFileStorage {
            url = "https://artifactory.domain.com/artifactory/repository/scan-results"
            headers {
              X-JFrog-Art-Api = "api-token"
            }
          }
        }
      }
    }

    storageReaders: [
      "artifactoryStorage"
    ]

    storageWriters: [
      "artifactoryStorage"
    ]
  }
}
```

### PostgreSQL Storage

To use PostgreSQL for storing scan results you need at least version 9.4, create a database with the `client_encoding`
set to `UTF8`, and a configuration like the following:

```hocon
ort {
  scanner {
    storages {
      postgresStorage {
        url = "jdbc:postgresql://example.com:5444/database"
        schema = "public"
        username = "username"
        password = "password"
        sslmode = "verify-full"
      }
    }

    storageReaders: [
      "postgresStorage"
    ]

    storageWriters: [
      "postgresStorage"
    ]
  }
}
```

The database needs to exist. If the schema is set to something else than the default of `public`, it needs to exist and be accessible by the configured username.

The _scanner_ will itself create a table called `scan_results` and
store the data in a [jsonb](https://www.postgresql.org/docs/current/datatype-json.html) column.

If you do not want to use SSL set the `sslmode` to `disable`, other possible values are explained in the
[documentation](https://jdbc.postgresql.org/documentation/head/ssl-client.html). For other supported configuration
options see [ScanStorageConfiguration.kt](./model/src/main/kotlin/config/ScanStorageConfiguration.kt).

### ClearlyDefined Storage

[ClearlyDefined](https://clearlydefined.io) is a service offering curated metadata for Open Source components. This
includes scan results that can be used by ORT's _scanner_ tool (if they have been generated by a compatible scanner
version with a suitable configuration). This storage backend queries the ClearlyDefined service for scan results of the
packages to be processed. It is read-only; so it will not upload any new scan results to ClearlyDefined. In the
configuration the URL of the ClearlyDefined service needs to be set:

```hocon
ort {
  scanner {
    storages {
      clearlyDefined {
        serverUrl = "https://api.clearlydefined.io"
      }
    }

    storageReaders: [
      "clearlyDefined"
    ]
  }
}
```

<a name="advisor">&nbsp;</a>

[![Advisor](../logos/advisor.png)](./advisor/src/main/kotlin)

The _advisor_ retrieves security advisories from configured services. It requires the analyzer result as an input. For
all the packages identified by the analyzer, it queries the services configured for known security vulnerabilities. The
vulnerabilities returned by these services are then stored in the output result file together with additional
information like the source of the data and a severity (if available).

Multiple providers for security advisories are available. The providers require specific configuration in the
[ORT configuration file](./model/src/main/resources/reference.conf), which needs to be placed in the _advisor_
section. When executing the advisor the providers to enable are selected with the `--advisors` option (or its short
alias `-a`); here a comma-separated list with provider IDs is expected. The following sections describe the providers
supported by the advisor:

## NexusIQ

A security data provider that queries [Nexus IQ Server](https://help.sonatype.com/iqserver). In the configuration,
the URL where Nexus IQ Server is running and the credentials to authenticate need to be provided:

```hocon
ort {
  advisor {
    nexusIq {
      serverUrl = "https://nexusiq.ossreviewtoolkit.org"
      username = myUser
      password = myPassword
    }
  }
}
```

To enable this provider, pass `-a NexusIQ` on the command line.

## VulnerableCode

This provider obtains information about security vulnerabilities from a
[VulnerableCode](https://github.com/nexB/vulnerablecode) instance. The configuration is limited to the server URL, as
authentication is not required:

```hocon
ort {
  advisor {
    vulnerableCode {
      serverUrl = "http://localhost:8000"
    }
  }
}
```

To enable this provider, pass `-a VulnerableCode` on the command line.

<a name="evaluator">&nbsp;</a>

[![Evaluator](../logos/evaluator.png)](./evaluator/src/main/kotlin)

The _evaluator_ is used to perform custom license policy checks on scan results. The rules to check against are
implemented as scripts (currently Kotlin scripts, with a dedicated DSL, but support for other scripting can be added as
well. See [rules.kts](./examples/rules.kts) for an example file.

<a name="reporter">&nbsp;</a>

[![Reporter](../logos/reporter.png)](./reporter/src/main/kotlin)

The _reporter_ generates a wide variety of documents in different formats from ORT result files. Currently, the
following formats are supported (reporter names are case-insensitive):

* [AsciiDoc Template](./reporters/AsciiDocTemplateReporter.md) (`-f AsciiDocTemplate`)
  * Content customizable with [Apache Freemarker](https://freemarker.apache.org/) templates and [AsciiDoc](https://asciidoc.org/)
  * Supports all AsciiDoc backends
  * PDF style customizable with Asciidoctor [PDF themes](https://github.com/asciidoctor/asciidoctor-pdf/blob/master/docs/theming-guide.adoc)
* [CycloneDX](https://cyclonedx.org/) BOM (`-f CycloneDx`)
* [Excel](https://products.office.com/excel) sheet (`-f Excel`)
* [GitLabLicenseModel](https://docs.gitlab.com/ee/ci/pipelines/job_artifacts.html#artifactsreportslicense_scanning-ultimate) (`-f GitLabLicenseModel`)
  * A nice tutorial video has been [published](https://youtu.be/dNmH_kYJ34g) by GitLab engineer @mokhan.
* [NOTICE](http://www.apache.org/dev/licensing-howto.html) file in two variants
  * List license texts and copyrights by package (`-f NoticeTemplate`)
  * Summarize all license texts and copyrights (`-f NoticeTemplate -O NoticeTemplate=template.id=summary`)
  * Customizable with [Apache Freemarker](https://freemarker.apache.org/) templates
* [SPDX Document](https://spdx.dev/specifications/), version 2.2 (`-f SpdxDocument`)
* Static HTML (`-f StaticHtml`)
* Web App (`-f WebApp`)

