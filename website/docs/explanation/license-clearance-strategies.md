# License Compliance Clearance Strategies

In an ideal world, every package used by a project has its applicable license(s) clearly identified so you can easily determine your obligations. In reality, projects often have hundreds or thousands of dependencies and running the [Scanner] typically returns many different open-source licenses. ORT provides powerful mechanisms - see e.g. [how to exclude dirs, files or scopes][how-to-exclude-dirs-files-or-scopes] - to reduce compliance effort, but even after applying those you may still need to validate numerous license findings. Since you rarely have the resources to clear every finding, your only option is to prioritize which licenses to investigate.

The ORT project

* How-to guides
  * [How to exclude dirs, files or scopes][how-to-exclude-dirs-files-or-scopes]
* Reference
  * [Package configurations][package-configurations]
  * [Repository configuration (.ort.yml)][ort-yml]
  * [Scanner CLI][scanner]

[how-to-exclude-dirs-files-or-scopes]: ../how-to-guides/how-to-exclude-dirs-files-or-scopes.md
[ort-yml]: ../reference/configuration/ort-yml.md
[package-configurations]: ../reference/configuration/package-configurations.md#file-format
[scanner]: ../reference/cli/scanner.md
