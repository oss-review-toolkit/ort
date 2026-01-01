# Ort Project Package Manager

The ORT Project package manager can be used to manually define projects in situations like:

* Package manager is not supported by ORT yet.
* Project is using a custom or in-house package manager.
* Project has no package manager at all.
* Project contains additional packages that are not detected by the main package manager.

## Definition file location, naming and format

### Location

To use the ORT project Package Manager, just place the definition file in the root directory of your project.
If you have multiple projects in a mono-repo, you can place multiple definition files in the respective project
sub-directories.

### File naming

The ORT Project definition file must be named, or end with `ort.project.yml`, `ort.project.yaml`, or `ort.project.json`.
For example, all of the following names are valid:

* `ort.project.yml`
* `my.ort.project.yaml`
* `custom-name.ort.project.json`

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
      url: "https://git.example.com/full/"
      revision: "master"
      path: "/"
    sourceArtifact:
      url: "https://repo.example.com/m2/full-1.1.0-sources.jar"
      hash: "da39a3ee5e6b4b0d3255bfef95601890afd80709"
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
  - id: "Maven/com.example/partial/1.0.1"
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
declaredLicenses:
  - String list (optional) List of declared licenses in SPDX format (see remarks below).
authors:
  - String list (optional) List of authors.
dependencies: (mandatory) List of dependency packages (described in the next section).
~~~

#### Dependency element schema

Single dependency package schema:

~~~yaml

purl: String (mandatory/optional) Package URL in purl format (see remarks below).
id: String (mandatory/optional) Package identifier in the "ORT" format (see remarks below).
description: String (optional) Package brief description.
# (optional) Definition of the package's version control system location.
vcs:
  type: String (optional) VCS type, e.g., "Git", "Subversion", "Mercurial".
  url: String (optional) VCS repository URL.
  revision: String (optional) VCS revision (branch).
  path: String (optional) VCS path within the repository.
# (optional) The remote artifact where the source package can be downloaded.
sourceArtifact:
  url: String (optional) URL to the source artifact.
  hash: String (optional) Hash of the source artifact.
declaredLicenses:
  - String list (optional) List of declared licenses in SPDX format (see remarks below).
homepageUrl: String (optional) URL to the package homepage.
labels: (optional) User defined labels associated with this package. The labels are not interpreted by the core of ORT
  itself, but can be used in parts of ORT such as plugins, in evaluator rules, or in reporter templates.
authors:
  - String list (optional) List of authors.
scopes:
  - String list (optional) List of scopes the package belongs to.
isModified: Boolean (optional) Flag indicating whether the source code of the package has been modified compared to
  the original source code, e.g., in case of a fork of an upstream Open Source project. Default is false.
isMetadataOnly: Boolean (optional) Flag indicating whether the package is just metadata, like e.g. Maven BOM artifacts
  which only define constraints for dependency versions.. Default is false.
~~~

### Remarks

* Each dependency package must at least define either a `purl` or an `id`.
* The `purl` field must contain a valid package identifier in [PURL format](https://github.com/package-url/purl-spec).
  Only purls starting with `pkg:` are supported.
  Also, `qualifier` and `subpath` components are not supported.
* The `id` field must contain a valid ORT package identifier in the format:
  `<package-manager>/<namespace>/<name>/<version>`.
* All license names must be in [SPDX format](https://spdx.org/licenses/).
