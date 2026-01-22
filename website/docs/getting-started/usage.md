# Usage

## Running the Tools

First, make sure that the locale of your system is set to `en_US.UTF-8` as using other locales might lead to issues with parsing the output of some external tools.

Then, let ORT check whether all required external tools are available by running

```shell
ort requirements
```

and install any missing tools or add compatible versions as indicated.

Finally, ORT tools like the *analyzer* can be run like

```shell
ort --info analyze -f JSON -i /project -o /project/ort/analyzer
```

Just the like top-level `ort` command, the subcommands for all tools provide a `--help` option for detailed usage help.
Use it like `ort analyze --help`.

## Runtime Requirements

ORT is being continuously used on Linux, Windows and macOS by the [core development team](https://github.com/orgs/oss-review-toolkit/people), so these operating systems are considered to be well-supported.

To run the ORT binaries (also see [Installation from binaries](installation.md#from-binaries)) at least Java 21 is required.
Memory and CPU requirements vary depending on the size and type of project(s) to analyze / scan, but the general recommendation is to configure Java with 8 GiB of memory and to use a CPU with at least 4 cores.

```shell
# This will give the Java Virtual Machine 8GB Memory.
export JAVA_OPTS="$JAVA_OPTS -Xmx8g"
```

If ORT requires external tools to analyze a project, these tools are listed by the `ort requirements` command.
If a package manager is not listed there, support for it is integrated directly into ORT and does not require any external tools to be installed.
