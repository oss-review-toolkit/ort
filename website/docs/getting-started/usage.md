---
sidebar_position: 3
---

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

Please see [Getting Started](../getting-started/tutorial.md) for an introduction to the individual tools.

## Running on CI

A basic ORT pipeline (using the *analyzer*, *scanner* and *reporter*) can easily be run on [Jenkins CI](https://jenkins.io/) by using the
[Jenkinsfile](https://github.com/oss-review-toolkit/ort/blob/main/integrations/jenkins/Jenkinsfile) in a (declarative) [pipeline](https://jenkins.io/doc/book/pipeline/) job.
Please see the [Jenkinsfile](https://github.com/oss-review-toolkit/ort/blob/main/integrations/jenkins/Jenkinsfile) itself for documentation of the required Jenkins plugins.
The job accepts various parameters that are translated to ORT command line arguments.
Additionally, one can trigger a downstream job which e.g. further processes scan results.
Note that it is the downstream job's responsibility to copy any artifacts it needs from the upstream job.

## Configuration

### Environment variables

ORT supports several environment variables that influence its behavior:

| Name              | Default value          | Purpose                                                  |
|-------------------|------------------------|----------------------------------------------------------|
| ORT_DATA_DIR      | `~/.ort`               | All data, like caches, archives, storages (read & write) |
| ORT_CONFIG_DIR    | `$ORT_DATA_DIR/config` | Configuration files, see below (read only)               |
| ORT_HTTP_USERNAME | Empty (n/a)            | Generic username to use for HTTP(S) downloads            |
| ORT_HTTP_PASSWORD | Empty (n/a)            | Generic password to use for HTTP(S) downloads            |
| http_proxy        | Empty (n/a)            | Proxy to use for HTTP downloads                          |
| https_proxy       | Empty (n/a)            | Proxy to use for HTTPS downloads                         |

### Configuration files

ORT looks for its configuration files in the directory pointed to by the `ORT_CONFIG_DIR` environment variable.
If this variable is not set, it defaults to the `config` directory below the directory pointed to by the `ORT_DATA_DIR` environment variable, which in turn defaults to the `.ort` directory below the current user's home directory.

The following provides an overview of the various configuration files that can be used to customize ORT behavior:

#### [ORT configuration file](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml)

The main configuration file for the operation of ORT.
This configuration is maintained by an administrator who manages the ORT instance.
In contrast to the configuration files in the following, this file rarely changes once ORT is operational.

| Format | Scope  | Default location             |
|--------|--------|------------------------------|
| YAML   | Global | `$ORT_CONFIG_DIR/config.yml` |

The [reference configuration file](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml) gives a good impression about the content of the main ORT configuration file.
It consists of sections related to different subcomponents of ORT.
The meaning of these sections and the properties they can contain is described together with the corresponding subcomponents.

While the file is rather static, there are means to override configuration options for a specific run of ORT or to customize the configuration to a specific environment.
The following options are supported (in order of precedence):

* Properties can be defined via environment variables by using the full property path as the variable name.
  For instance, one can override the Postgres schema by setting `ort.scanner.storages.postgres.connection.schema=test_schema`.
  The variable's name is case-sensitive.
  Some programs like Bash do not support dots in variable names.
  For this case, the dots can be replaced by double underscores, i.e., the above example is turned into `ort__scanner__storages__postgres__connection__schema=test_schema`.
* In addition to that, one can override the values of properties on the command line using the `-P` option.
  The option expects a key-value pair.
  Again, the key must define the full path to the property to be overridden, e.g. `-P ort.scanner.storages.postgres.connection.schema=test_schema`.
  The `-P` option can be repeated on the command line to override multiple properties.
* Properties in the configuration file can reference environment variables using the syntax `${VAR}`.
  This is especially useful to reference dynamic or sensitive data.
  As an example, the credentials for the Postgres database used as scan results storage could be defined in the `POSTGRES_USERNAME` and `POSTGRES_PASSWORD` environment variables.
  The configuration file can then reference these values as follows:

  ```yaml
  postgres:
    connection:
      url: "jdbc:postgresql://your-postgresql-server:5444/your-database"
      username: ${POSTGRES_USERNAME}
      password: ${POSTGRES_PASSWORD}
  ```

To print the active configuration use:

```shell
ort config --show-active
```

#### [Copyright garbage file](../configuration/copyright-garbage.md)

A list of copyright statements that are considered garbage, for example, statements that were incorrectly classified as copyrights by the scanner.

| Format      | Scope  | Default location                        |
|-------------|--------|-----------------------------------------|
| YAML / JSON | Global | `$ORT_CONFIG_DIR/copyright-garbage.yml` |

#### [Curations file](../configuration/package-curations.md)

A file to correct invalid or missing package metadata, and to set the concluded license for packages.

| Format      | Scope  | Default location                |
|-------------|--------|---------------------------------|
| YAML / JSON | Global | `$ORT_CONFIG_DIR/curations.yml` |

#### [Custom license texts dir](../configuration/license-texts.md)

A directory that contains license texts not provided by ORT.

| Format | Scope  | Default location                        |
|--------|--------|-----------------------------------------|
| Text   | Global | `$ORT_CONFIG_DIR/custom-license-texts/` |

#### [How to fix text provider script](../configuration/how-to-fix-text-provider.md)

A Kotlin script that enables the injection of how-to-fix texts in Markdown format for ORT issues into the reports.

| Format        | Scope  | Default location                                        |
|---------------|--------|---------------------------------------------------------|
| Kotlin script | Global | `$ORT_CONFIG_DIR/reporter.how-to-fix-text-provider.kts` |

#### [License classifications file](../configuration/license-classifications.md)

A file that contains user-defined categorization of licenses.

| Format      | Scope  | Default location                              |
|-------------|--------|-----------------------------------------------|
| YAML / JSON | Global | `$ORT_CONFIG_DIR/license-classifications.yml` |

#### [Resolution file](../configuration/resolutions.md)

Configurations to resolve any issues or rule violations by providing a mandatory reason, and an optional comment to justify the resolution on a global scale.

| Format      | Scope  | Default location                  |
|-------------|--------|-----------------------------------|
| YAML / JSON | Global | `$ORT_CONFIG_DIR/resolutions.yml` |

#### [Repository configuration file](../configuration/ort-yml.md)

A configuration file, usually stored in the project's repository, for license finding curations, exclusions, and issues or rule violations resolutions in the context of the repository.

| Format      | Scope                | Default location                |
|-------------|----------------------|---------------------------------|
| YAML / JSON | Repository (project) | `[analyzer-input-dir]/.ort.yml` |

#### [Package configuration file / directory](../configuration/package-configurations.md)

A single file or a directory with multiple files containing configurations to set provenance-specific path excludes and license finding curations for dependency packages to address issues found within a scan result.
`cli-helper`'s [`package-config create` command](https://github.com/oss-review-toolkit/ort/blob/main/cli-helper/src/main/kotlin/commands/packageconfig/CreateCommand.kt) can be used to populate a directory with template package configuration files.

| Format      | Scope                | Default location                          |
|-------------|----------------------|-------------------------------------------|
| YAML / JSON | Package (dependency) | `$ORT_CONFIG_DIR/package-configurations/` |

#### [Policy rules file](../configuration/evaluator-rules.md)

The file containing any policy rule implementations to be used with the *evaluator*.

| Format              | Scope     | Default location                      |
|---------------------|-----------|---------------------------------------|
| Kotlin script (DSL) | Evaluator | `$ORT_CONFIG_DIR/evaluator.rules.kts` |

### Protecting environment variables

To do its analysis, ORT invokes a number of external tools, such as package managers or scanners.
Especially when interacting with package managers to obtain the dependencies of the analyzed project, this can lead to the execution of code in build scripts from potentially unknown sources.
A possible risk in this constellation is that untrusted code could read sensitive information from environment variables used for the ORT configuration, such as database connection strings or service credentials.
This is because the environment variables of a process are by default propagated to the child processes spawned by it.

To reduce this risk, ORT filters out certain environment variables when it runs external tools in child processes.
This filter mechanism can be configured via the following properties in the [ORT configuration file](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml):

| Property | Description |
|----------|-------------|
| deniedProcessEnvironmentVariablesSubstrings | A list of substrings that identify variables containing sensitive information. All variables that contain at least one of these strings (ignoring case) are not propagated to child processes. The default for this property contains strings like "PASS", "PWD", or "TOKEN", which are typically used to reference credentials. |
| allowedProcessEnvironmentVariableNames | This is a list of variable names that are explicitly allowed to be passed to child processes - even if they contain a substring listed in `deniedProcessEnvironmentVariablesSubstrings`. Via this property variables required by external tools, e.g. credentials for repositories needed by package managers, can be passed through. Here, entries must match variables names exactly and case-sensitively. |

This mechanism offers a certain level of security without enforcing an excessive amount of configuration, which would be needed, for instance, to define an explicit allowlist.
With the two configuration properties, even corner cases can be defined:

* To disable filtering of environment variables completely, set the `deniedProcessEnvironmentVariablesSubstrings` property to a single string that is certainly not contained in any environment variable, such as "This is for sure not contained in a variable name."
* To prevent that any environment variable is passed to a child process, substrings can be configured in `deniedProcessEnvironmentVariablesSubstrings` that match all variables, for instance one string for each letter of the alphabet.
