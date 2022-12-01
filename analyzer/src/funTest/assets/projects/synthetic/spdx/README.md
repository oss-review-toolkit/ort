This directory contains synthetic test projects without a dedicated package manager. Instead, SPDX files are used to
declare the project's metadata and its used packages.

- [inline-packages](inline-packages): This project declares the project's and all its package's metadata within a single
  file.
- [package-references](package-references): The same project as above, but only the project's metadata is declared in
  the main file, which refers to external SPDX files which contain the metadata for the respective packages.
- [subproject-conan](subproject-conan): A test project to verify that definition files from other package managers can
  be referenced and transitive dependencies are resolved correctly.
- [subproject-dependencies](subproject-dependencies): A test project to verify that subprojects are handled correctly.
- [transitive-dependencies](transitive-dependencies): A test project to verify that transitive dependencies are resolved
  correctly.

The [libs](libs) directory contains SPDX packages that can be used by all test projects.
