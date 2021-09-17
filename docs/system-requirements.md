# System requirements

ORT is being continuously used on Linux, Windows and macOS by the
[core development team](https://github.com/orgs/oss-review-toolkit/people), so these operating systems are
considered to be well supported.

To run the ORT binaries (also see [Installation from binaries](./installation-and-basic-usage#from-binaries)) at least Java 11 is required. Memory and
CPU requirements vary depending on the size and type of project(s) to analyze / scan, but the general recommendation is
to configure Java with 8 GiB of memory (`-Xmx=8g`) and to use a CPU with at least 4 cores.

If ORT requires external tools in order to analyze a project, these tools are listed by the `ort requirements` command.
If a package manager is not list listed there, support for it is integrated directly into ORT and does not require any
external tools to be installed.
