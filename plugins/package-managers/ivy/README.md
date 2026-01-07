# Apache Ivy Package Manager Plugin

This plugin provides support for analyzing projects that use [Apache Ivy](https://ant.apache.org/ivy/) for dependency management.

## Overview

Apache Ivy is a dependency manager focused on flexibility and simplicity.
It is typically used with Apache Ant, but can also be used standalone or with other build tools.

## Supported Features

### Core Features (Always Available)

* Parsing `ivy.xml` module descriptors
* Extracting project metadata (organization, module name, revision)
* Extracting direct dependencies from ivy.xml
* Mapping Ivy configurations to ORT scopes (compile, runtime, test, etc.)
* Extracting license information from module descriptors
* Support for multiple configurations in a single ivy.xml

### Advanced Features (Requires Ivy CLI)

* **Full transitive dependency resolution** using Ivy's resolve functionality
* **Dynamic version resolution** (e.g., `latest.integration`, `1.0.+`)
* **Conflict resolution** and version eviction handling
* **Complete dependency tree** with all transitive dependencies
* **Artifact location tracking** from Ivy resolvers

## Configuration Files

The plugin recognizes the following definition files:

* `ivy.xml` - Ivy module descriptor file

## Configuration

### Basic Usage (Direct Dependencies Only)

By default, the plugin only parses `ivy.xml` and extracts direct dependencies.
No external tools are required.

```yaml
# .ort.yml
analyzer:
  enabled_package_managers:
    - Ivy
```

**Note:** The default is `resolveTransitive: false` for compatibility and performance reasons.

### Advanced Usage (Full Transitive Resolution)

To enable full transitive dependency resolution, you need:

1. **Install Apache Ivy** and ensure it's available in your PATH
2. **Enable transitive resolution** in your ORT configuration:

```yaml
# .ort.yml
analyzer:
  package_managers:
    Ivy:
      options:
        resolveTransitive: "true"
```

**Installing Ivy:**

```bash
# Using package manager
brew install ivy  # macOS
apt-get install ivy  # Debian/Ubuntu

# Or download from Apache
wget https://downloads.apache.org/ant/ivy/2.5.2/apache-ivy-2.5.2-bin.tar.gz
tar -xzf apache-ivy-2.5.2-bin.tar.gz
export PATH=$PATH:/path/to/ivy/bin
```

## Ivy Module Structure

A typical `ivy.xml` file contains:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="2.0">
    <info organisation="com.example"
          module="my-project"
          revision="1.0.0"
          status="release">
        <license name="Apache-2.0" url="https://www.apache.org/licenses/LICENSE-2.0"/>
        <description>My project description</description>
    </info>

    <configurations>
        <conf name="default" visibility="public"/>
        <conf name="compile" visibility="public"/>
        <conf name="runtime" visibility="public"/>
        <conf name="test" visibility="private"/>
    </configurations>

    <dependencies>
        <dependency org="commons-lang" name="commons-lang" rev="2.6" conf="compile->default"/>
        <dependency org="junit" name="junit" rev="4.13.2" conf="test->default"/>
        <!-- Dynamic versions work with transitive resolution -->
        <dependency org="org.slf4j" name="slf4j-api" rev="latest.release" conf="runtime->default"/>
    </dependencies>
</ivy-module>
```

## How It Works

### Mode 1: Direct Dependencies (Default)

When `resolveTransitive: false` or Ivy CLI is not available:

1. Parses `ivy.xml` using XML parsing
2. Extracts direct dependencies as declared
3. Groups dependencies by configuration (scope)
4. **Limitation:** Dynamic versions remain unresolved, transitive dependencies are not included

**Use case:** Quick analysis, CI/CD where Ivy is not installed, basic dependency overview

### Mode 2: Transitive Resolution (Opt-in)

When `resolveTransitive: true` and Ivy CLI is available:

1. Parses `ivy.xml` to understand project structure
2. For each configuration, runs `ivy -ivy ivy.xml -confs <config> -xml report.xml`
3. Parses the generated resolve report XML
4. Builds complete dependency tree with all transitive dependencies
5. Resolves dynamic versions to concrete versions
6. Handles version conflicts and evictions

**Use case:** Complete SBOM generation, security scanning, license compliance

## Limitations and Known Issues

### Current Limitations

1. **Repository Configuration:**
    * `ivysettings.xml` files are not processed by ORT
    * Uses Ivy's default resolver configuration
    * Custom repositories must be configured in Ivy's global settings

2. **Module Metadata:**
    * Package metadata (licenses, descriptions, URLs) from POM files in repositories is not extracted
    * Only information from ivy.xml is captured
    * Recommendation: Use additional ORT features for metadata enrichment

3. **Build Files:**
    * Does not parse or execute `build.xml` (Ant build files)
    * Only analyzes dependency declarations in `ivy.xml`

4. **Performance:**
    * Transitive resolution requires running Ivy CLI for each configuration
    * Can be slow for projects with many configurations or large dependency trees
    * Ivy downloads artifacts during resolution (uses local cache)

### Workarounds

* **For missing metadata:** Use ORT's package curation features to enrich package information
* **For custom repositories:** Configure `ivysettings.xml` in Ivy's global configuration directory
* **For performance:** Use caching strategies, limit configurations, or run analysis on dedicated build machines

## Comparison: Direct vs Transitive Resolution

| Feature | Direct Mode | Transitive Mode |
| ------- | ----------- | --------------- |
| External Dependencies | None | Requires Ivy CLI |
| Dependency Depth | Direct only | Full tree |
| Dynamic Versions | Not resolved | Resolved to concrete versions |
| Version Conflicts | Not detected | Handled by Ivy |
| Analysis Speed | Fast | Slower (depends on dependencies) |
| Accuracy | Basic | Complete |
| Use Case | Quick scan | Production SBOM |

## Example Output

### Direct Dependencies Mode

```yaml
project:
  id: "Ivy::com.example:my-project:1.0.0"
  scopes:
  - name: "compile"
    dependencies:
    - id: "Maven::commons-lang:commons-lang:2.6"
      dependencies: []  # No transitive deps
```

### Transitive Resolution Mode

```yaml
project:
  id: "Ivy::com.example:my-project:1.0.0"
  scopes:
  - name: "compile"
    dependencies:
    - id: "Maven::commons-lang:commons-lang:2.6"
      dependencies:
        - id: "Maven::commons-logging:commons-logging:1.1.1"  # Transitive!
          dependencies: []
```

## Example Projects

See the `src/funTest/assets/projects/synthetic/` directory for example projects that can be analyzed with this plugin.

## Requirements

### Basic Mode (Default)

* No external tools required
* Works with any JVM (Java 11+)

### Transitive Mode (Optional)

* Apache Ivy 2.4.0 or later installed and in PATH
* Internet connection or configured Ivy cache for dependency resolution
* `ivysettings.xml` configured if using private repositories

### Container/Docker Mode

* Apache Ivy 2.5.2 is **pre-installed** in ORT container images (`ort:latest`)
* No manual installation required when using containers
* See the "Docker/Podman Support" section below for detailed container usage instructions

## Testing

Run the functional tests with:

```bash
# Test basic parsing
./gradlew :plugins:package-managers:ivy:funTest

# Test with transitive resolution (requires Ivy installed)
./gradlew :plugins:package-managers:ivy:funTest -DresolveTransitive=true
```

## Troubleshooting

### "Ivy CLI not found in PATH"

* Install Apache Ivy and ensure the `ivy` command is available
* Check with: `ivy -version`

### "Ivy resolve failed for configuration"

* Check that your `ivy.xml` is valid
* Ensure repositories are accessible
* Review Ivy's cache in `~/.ivy2/cache`
* Enable debug logging: `export IVY_OPTS="-verbose"`

### "Dynamic version not resolved"

* This is expected in direct mode
* Enable `resolveTransitive: true` for dynamic version resolution

### Slow resolution

* Use Ivy's cache (enabled by default)
* Reduce number of configurations if possible
* Consider using Maven Central mirror for faster downloads

## Docker/Podman Support

Apache Ivy is included in the ORT container image.
When using containers, Ivy is already installed and configured.

### Quick Start with Container

```bash
# Using Docker
docker run --rm -v $(pwd):/project ghcr.io/oss-review-toolkit/ort:latest \
    analyze -i /project -o /project/ort-results

# Using Podman
podman run --rm -v $(pwd):/project:z ghcr.io/oss-review-toolkit/ort:latest \
    analyze -i /project -o /project/ort-results
```

For detailed container usage including:

* Persistent Ivy cache configuration
* Custom `ivysettings.xml` mounting
* Network proxy configuration
* Multi-architecture support

Please refer to the ORT documentation for complete container setup and configuration details.

## Contributing

When contributing improvements to this plugin:

1. Ensure backward compatibility with direct parsing mode
2. Add appropriate tests for both modes
3. Document any new configuration options
4. Update this README with new features or limitations
