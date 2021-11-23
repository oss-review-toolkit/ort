This the root of a synthetic project called "xyz" without a dedicated package manager. Instead, SPDX files are used to
declare the project's metadata and its used packages.

Two alternative (but equivalent) ways are demonstrated to declare the metadata:

- The `project-xyz-with-inline-packages.spdx.yml` file declares the project's and all its package's metadata within that
  single file only.

- The `project-xyz-with-package-references.spdx.yml` file only declares the project's metadata directly, but refers to
  external SPDX files which contain the metadata for the respective packages.
