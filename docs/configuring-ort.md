# Configuration

This article presents a detailed guide to ORT configuration, covering envrionment variables and the configuration files.

## Environment variables

ORT supports several environment variables that influence its behavior:

| Name | Default value | Purpose |
| ---- | ------------- | ------- |
| ORT_DATA_DIR | `~/.ort` | All data, like caches, archives, storages (read & write) |
| ORT_CONFIG_DIR | `$ORT_DATA_DIR/config` | Configuration files, see below (read only) |
| ORT_HTTP_USERNAME | Empty (n/a) | Generic username to use for HTTP(S) downloads |
| ORT_HTTP_PASSWORD | Empty (n/a) | Generic password to use for HTTP(S) downloads |
| http_proxy | Empty (n/a) | Proxy to use for HTTP downloads |
| https_proxy | Empty (n/a) | Proxy to use for HTTPS downloads |

## Configuration files

ORT looks for its configuration files in the directory pointed to by the `ORT_CONFIG_DIR` environment variable. If this
variable is not set, it defaults to the `config` directory below the directory pointed to by the `ORT_DATA_DIR`
environment variable, which in turn defaults to the `.ort` directory below the current user's home directory.

The following provides an overview of the various configuration files that can be used to customize ORT behavior:

### [ORT configuration file](./model/src/main/resources/reference.conf)

The main configuration file for the operation of ORT. This configuration is maintained by an administrator who manages
the ORT instance. In contrast to the configuration files in the following, this file rarely changes once ORT is
operational.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| HOCON | Global | `$ORT_CONFIG_DIR/ort.conf` | Empty |

The [reference configuration file](./model/src/main/resources/reference.conf) gives a good impression about the content
of the main ORT configuration file. It consists of sections related to different sub components of ORT. The meaning
of these sections and the properties they can contain is described together with the corresponding sub components.

While the file is rather static, there are means to override configuration options for a specific run of ORT or to
customize the configuration to a specific environment. The following options are supported, in order of precedence:

* Properties can be defined via environment variables by using the full property path as the variable name.
  For instance, one can override the Postgres schema by setting 
  `ort.scanner.storages.postgresStorage.schema=test_schema`. The variable's name is case sensitive.
  Some programs like Bash do not support dots in variable names. For this case, the dots can be
  replaced by double underscores, i.e., the above example is turned into 
  `ort__scanner__storages__postgresStorage__schema=test_schema`.
* In addition to that, one can override the values of properties on the command line using the `-P` option. The option expects a
  key-value pair. Again, the key must define the full path to the property to be overridden, e.g.
  `-P ort.scanner.storages.postgresStorage.schema=test_schema`. The `-P` option can be repeated on the command
  line to override multiple properties.
* Properties in the configuration file can reference environment variables using the syntax `${VAR}`.
  This is especially useful to reference dynamic or sensitive data. As an example, the credentials for the
  Postgres database used as scan results storage could be defined in the `POSTGRES_USERNAME` and `POSTGRES_PASSWORD`
  environment variables. The configuration file can then reference these values as follows:

  ```hocon
  postgres {
    url = "jdbc:postgresql://your-postgresql-server:5444/your-database"
    username = ${POSTGRES_USERNAME}
    password = ${POSTGRES_PASSWORD}
  }
  ```

### [Copyright garbage file](./docs/config-file-copyright-garbage-yml.md)

A list of copyright statements that are considered garbage, for example statements that were incorrectly classified as
copyrights by the scanner.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| YAML / JSON | Global | `$ORT_CONFIG_DIR/copyright-garbage.yml` | Empty (n/a) |

### [Curations file](./docs/config-file-curations-yml.md)

A file to correct invalid or missing package metadata, and to set the concluded license for packages.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| YAML / JSON | Global | `$ORT_CONFIG_DIR/curations.yml` | Empty (n/a) |

### [Custom license texts dir](./docs/dir-custom-license-texts.md)

A directory that contains license texts which are not provided by ORT.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| Text | Global | `$ORT_CONFIG_DIR/custom-license-texts/` | Empty (n/a) |

### [How to fix text provider script](./docs/how-to-fix-text-provider-kts.md)

A Kotlin script that enables the injection of how-to-fix texts in markdown format for ORT issues into the reports.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| Kotlin script | Global | `$ORT_CONFIG_DIR/how-to-fix-text-provider.kts` | Empty (n/a) |

### [License classifications file](docs/config-file-license-classifications-yml.md)

A file that contains user-defined categorization of licenses.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| YAML / JSON | Global | `$ORT_CONFIG_DIR/license-classifications.yml` | Empty (n/a) |

### [Resolution file](./docs/config-file-resolutions-yml.md)

Configurations to resolve any issues or rule violations by providing a mandatory reason, and an optional comment to
justify the resolution on a global scale.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| YAML / JSON | Global | `$ORT_CONFIG_DIR/resolutions.yml` | Empty (n/a) |

### [Repository configuration file](./docs/config-file-ort-yml.md)

A configuration file, usually stored in the project's repository, for license finding curations, exclusions, and issues
or rule violations resolutions in the context of the repository.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| YAML / JSON | Repository (project) | `[analyzer-input-dir]/.ort.yml` | Empty (n/a) |

### [Package configuration file / directory](./docs/config-file-package-configuration-yml.md)

A single file or a directory with multiple files containing configurations to set provenance-specific path excludes and
license finding curations for dependency packages to address issues found within a scan result. `helper-cli`'s
[`package-config create` command](./helper-cli/src/main/kotlin/commands/packageconfig/CreateCommand.kt)
can be used to populate a directory with template package configuration files.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| YAML / JSON | Package (dependency) | `$ORT_CONFIG_DIR/package-configurations/` | Empty (n/a) |

### [Policy rules file](./docs/file-rules-kts.md)

The file containing any policy rule implementations to be used with the _evaluator_.

| Format | Scope | Default location | Default value |
| ------ | ----- | ---------------- | ------------- |
| Kotlin script (DSL) | Evaluator | `$ORT_CONFIG_DIR/rules.kts` | Empty (n/a) |
