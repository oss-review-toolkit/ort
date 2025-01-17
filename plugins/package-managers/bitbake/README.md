# About

This is a package manager plugin for the [OSS Review Toolkit][ORT] to analyze [Yocto] projects managed by [BitBake].
It supersedes the combination of the [meta-doubleopen] and [do-convert] projects by relying on upstream [SBOM] generation in [SPDX] format, and converting the generated files to an ORT analyzer result file via ORT's [SPDX document file analyzer].

[ORT]: https://github.com/oss-review-toolkit/ort
[BitBake]: https://docs.yoctoproject.org/bitbake.html
[Yocto]: https://www.yoctoproject.org/
[meta-doubleopen]: https://github.com/doubleopen-project/meta-doubleopen
[do-convert]: https://github.com/doubleopen-project/do-convert
[SBOM]: https://docs.yoctoproject.org/dev/dev-manual/sbom.html
[SPDX]: https://spdx.dev/
[SPDX document file analyzer]: https://oss-review-toolkit.org/ort/docs/tools/analyzer
