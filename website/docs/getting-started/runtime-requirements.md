---
sidebar_position: 2
---

# Runtime Requirements

ORT is being continuously used on Linux, Windows and macOS by the [core development team](https://github.com/orgs/oss-review-toolkit/people), so these operating systems are considered to be well-supported.

To run the ORT binaries (also see [Installation from binaries](installation.md#from-binaries)) at least Java 11 is required.
Memory and CPU requirements vary depending on the size and type of project(s) to analyze / scan, but the general recommendation is to configure Java with 8 GiB of memory and to use a CPU with at least 4 cores.

```shell
# This will give the Java Virtual Machine 8GB Memory.
export JAVA_OPTS="$JAVA_OPTS -Xmx8g"
```

If ORT requires external tools to analyze a project, these tools are listed by the `ort requirements` command.
If a package manager is not list listed there, support for it is integrated directly into ORT and does not require any external tools to be installed.
