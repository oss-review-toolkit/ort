# Ort Project Package Manager

The ORT Project package manager can be used to manually define projects in situations like:

* Package manager is not supported by ORT yet.
* Project is using a custom or in-house package manager.
* Project has no package manager at all.
* Project contains additional packages that are not detected by the main package manager.

## Definition file location, naming and format

### Location

To use the ORT Project Package Manager, just place the definition file(s) in any directory of your project.
If you have multiple projects in a mono-repo, it's possible to place multiple definition files in the project
sub-directories.

### File naming

The ORT Project definition file must be named, or end with `ortproject.yml`, `ortproject.yaml`, or `ortproject.json`.
For example, all of the following names are valid:

* `ortproject.yml`
* `my.ortproject.yaml`
* `custom-name.ortproject.json`

## Definition file format

ORT Project package manager uses an ORT Project definition file to define projects and their dependencies.
Example definition files can be found below:

### Example files

~~~yaml
projectName: "Example ORT project"
description: "Project description"
homepageUrl: "https://project.example.com"
declaredLicenses:
  - "Apache-2.0"
authors:
  - "John Doe"
  - "Foo Bar"
dependencies:
  - purl: "pkg:maven/com.example/full@1.1.0"
    description: "Package with fully elaborated model."
    vcs:
      type: "Mercurial"
      url: "https://example.com/hg/full"
      revision: "master"
      path: "/"
    sourceArtifact:
      url: "https://repo.example.com/m2/full-1.1.0-sources.jar"
      hash: 
        value: "da39a3ee5e6b4b0d3255bfef95601890afd80709"
        algorithm: "SHA-1"
    declaredLicenses:
      - "Apache-2.0"
      - "MIT"
    homepageUrl: "https://project.example.com/full"
    labels:
      label: "value"
      label2: "value2"
    authors:
      - "Doe John"
      - "Bar Foo"
    scopes:
      - "main"
      - "some_scope"
    isModified: false
    isMetadataOnly: false
  - purl: "pkg:maven/com.example/minimal@0.1.0"
  - id: "Maven:com.example:partial:1.0.1"
~~~

Minimal example file:

~~~yaml
dependencies:
  - purl: "pkg:maven/com.example/full@1.1.0"
~~~

### Definition file schema

#### Project schema

The ORT Project definition file uses the following schema:

~~~yaml
projectName: String (optional) Project name.
description: String (optional) Project brief description.
homepageUrl: String (optional) URL to the project homepage.
# (optional) List of declared licenses for the project.
declaredLicenses:
  - String list (optional) List of declared licenses in SPDX format (see remarks below).
# (optional) List of authors of the project.
authors:
  - String Author name.
# (mandatory) List of dependency packages for the project.
dependencies: 
  - Dependency element schema (see below)
~~~

#### Dependency element schema

Single dependency package schema:

~~~yaml

purl: String (mandatory at least one of the id or purl) Package URL in purl format (see remarks below).
id: String (mandatory at least one of the purl or id) Package identifier in the "ORT" format (see remarks below).
description: String (optional) Package brief description.
# (optional) Definition of the package's version control system location.
vcs: 
  type: String (mandatory) VCS type, e.g., "Git", "Subversion", "Mercurial".
  url: String (mandatory) VCS repository URL.
  revision: String (mandatory) VCS revision (branch).
  path: String (optional) VCS path within the repository. Default is empty string.
# (optional) The remote artifact where the source package can be downloaded.
sourceArtifact: 
  url: String (mandatory) URL to the source artifact.
  # (optional) Hash of the source artifact.
  hash: 
    value: String (mandatory) hash value.
    algorithm: String (mandatory) hash algorithm. Check remarks below for supported algorithms.
# (optional) List of declared licenses for the dependency.
declaredLicenses: 
  - String Declared license in SPDX format (see remarks below).
homepageUrl: String (optional) URL to the package homepage.
labels: (optional) User defined labels associated with this package. The labels are not interpreted by the core of ORT
  itself, but can be used in parts of ORT such as plugins, in evaluator rules, or in reporter templates. Labels are key-value
  pairs where both the key and value are strings.
# (optional) List of authors of the dependency.
authors: 
  - String Author name.
# (optional) List of scopes the package belongs to.
scopes: 
  - String Package's scope.
isModified: Boolean (optional) Flag indicating whether the source code of the package has been modified compared to
  the original source code, e.g., in case of a fork of an upstream Open Source project. Default is false.
isMetadataOnly: Boolean (optional) Flag indicating whether the package is just metadata, like e.g. Maven BOM artifacts
  which only define constraints for dependency versions. Default is false.
~~~

### Remarks

* Each dependency package must at least define either a `purl` or an `id`.
* The `purl` field must contain a valid package identifier in [PURL format](https://github.com/package-url/purl-spec).
  Only purls starting with `pkg:` are supported.
  Also, `qualifier` and `subpath` components are not supported.
* The `id` field must contain a valid ORT package identifier in the format:
  `<package-manager>/<namespace>/<name>/<version>`.
* The following hash algorithms are supported in the `sourceArtifact.hash.algorithm` field:
  * `MD5`
  * `SHA-1`
  * `SHA-256`
  * `SHA-384`
  * `SHA-512`
  * `SHA-1-GIT`
* All license names must be in [SPDX format](https://spdx.org/licenses/).
