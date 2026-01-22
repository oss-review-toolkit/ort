# How to add non-detected or supported packages

When ORT's [Analyzer] doesn't detect your dependencies (due to unsupported package manager, vendored code, or manually managed deps), add you can add them manually so they appear in ORT results.

## Add packages using SPDX project file

Create a `project.spdx.yml` file as shown below in your repository. The [SPDX Analyzer][analyzer-spdx-fallback] automatically picks it up and treats listed packages as if detected by a package manager.

```yaml
spdxVersion: "SPDX-2.2"
dataLicense: "CC0-1.0"
SPDXID: "SPDXRef-DOCUMENT"
name: "my-project"
documentNamespace: "https://github.com/example/my-project"
creationInfo:
  created: "2025-01-20T00:00:00Z"
  creators:
  - "Organization: Example Inc."

packages:
- SPDXID: "SPDXRef-Package-my-project"
  name: "my-project"
  versionInfo: "1.0.0"
  downloadLocation: "https://github.com/example/my-project"
  filesAnalyzed: false
  licenseConcluded: "Apache-2.0"
  licenseDeclared: "Apache-2.0"
  copyrightText: "Copyright 2025 Example Inc."

- SPDXID: "SPDXRef-Package-vendored-lib"
  name: "vendored-lib"
  versionInfo: "2.0.0"
  downloadLocation: "https://example.com/vendored-lib"
  filesAnalyzed: false
  licenseConcluded: "MIT"
  licenseDeclared: "MIT"
  copyrightText: "Copyright 2024 Vendor Inc."

relationships:
- spdxElementId: "SPDXRef-Package-my-project"
  relatedSpdxElement: "SPDXRef-Package-vendored-lib"
  relationshipType: "DEPENDS_ON"
```

Alternatively, you can define a single package using a `package.spdx.yml`.

```yaml
SPDXID: "SPDXRef-DOCUMENT"
spdxVersion: "SPDX-2.2"
creationInfo:
  created: "2021-03-05T13:43:22Z"
  creators:
  - "Organization: Robert Bosch GmbH"
name: "zlib-1.2.11"
dataLicense: "CC0-1.0"
documentNamespace: "http://spdx.org/spdxdocs/spdx-document-zlib"
documentDescribes:
- "SPDXRef-Package-zlib"
packages:
- SPDXID: "SPDXRef-Package-zlib"
  description: "zlib 1.2.11 is a general purpose data compression library."
  copyrightText: "(C) 1995-2017 Jean-loup Gailly and Mark Adler"
  downloadLocation: "http://zlib.net/zlib-1.2.11.tar.gz"
  externalRefs:
  - referenceCategory: "SECURITY"
    referenceLocator: "cpe:/a:compress:zlib:1.2.11:::en-us"
    referenceType: "cpe22Type"
  filesAnalyzed: false
  homepage: "http://zlib.net"
  licenseConcluded: "NOASSERTION"
  licenseDeclared: "Zlib"
  name: "zlib"
  versionInfo: "1.2.11"
  originator: "Person: Mark Adler, Jean-loup Gailly"
```

## Generating an Analyzer result file

Alternatively, if no packages at all are detected you can also create a `package-list.yml` file and convert it to an Analyzer result using the [ORT Helper][orth] `create-analyzer-result-from-package-list` command.

```yaml
project:
  name: "my-project"
  vcs:
    url: "https://github.com/example/my-project.git"
    revision: "abc123"

packages:
- id: "NPM::example-lib:1.0.0"
  vcs:
    url: "https://github.com/example/example-lib.git"
  declared_licenses:
  - "MIT"
  is_excluded: false
  linkage: "DYNAMIC"

- id: "Maven:com.example:other-lib:2.0.0"
  declared_licenses:
  - "Apache-2.0"
  concluded_license: "Apache-2.0"
  description: "A useful library."
  homepage_url: "https://example.com/other-lib"
```

Convert it to an Analyzer result, via:

```
cli-helper/build/install/orth/bin/orth create-analyzer-result-from-package-list \
  -i package-list.yml \
  -o analyzer-result.yml
```

## Related resources

* Examples
  * [examples/package-list.yml](https://github.com/oss-review-toolkit/ort/blob/main/cli-helper/src/funTest/resources/package-list.yml)
  * [package.spdx.yml files within the ORT repository](https://github.com/search?q=repo%3Aoss-review-toolkit%2Fort+package.spdx.yml+language%3AYAML&type=code&l=YAML)
* Reference
  * [Analyzer CLI][analyzer]
  * [Analyzer CLI - SPDX as fallback package manager][analyzer-spdx-fallback]
  * [ORT Helper CLI][orth]

[analyzer]: ../reference/cli/analyzer.md
[analyzer-spdx-fallback]: ../reference/cli/analyzer.md#spdx-as-fallback-package-manager
[orth]: ../reference/cli/orth.md
