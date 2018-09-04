# Plugin Example

This is a project to demo how ORT can be extended with plugins.

## Plugin Architecture

ORT uses the Java [ServiceLoader](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html) to load implementations
of extensions points. Extension points can be interfaces or abstract classes. The ServiceLoader will automatically
discover implementations of extensions points if their JAR file is on the classpath and contains a service provider
configurations file for each extension point. For an example see the
[configurations files used in the plugin example](src/main/resources/META-INF/services).

## Example project

To support third-party tools that want to add data to the ORT result that does not fit into the existing properties, the
ORT model provides `data` maps in many classes which can be filled with arbitrary key-value pairs. One use case is to
implement scanners that do not scan the source code for licenses but for other information.  

This feature is used in this example project that uses ORT to summarize HTML tags used in HTML files and all pages
linked from them. For details see the descriptions of the individual components below.

## Extension points

These are the extension points that ORT currently provides.

### Package Manager

To implement a new Package Manager a
[PackageManagerFactory](../../analyzer/src/main/kotlin/PackageManagerFactory.kt) and a corresponding
[PackageManager](../../analyzer/src/main/kotlin/PackageManager.kt) need to be implemented. A service provider
configuration file is only required for the `PackageManagerFactory`.

The [example package manager](src/main/kotlin/HtmlPackageManager.kt) creates a project for each HTML file found in the
input directory. Each link found in a HTML file is added as a dependency to the "links" scope of the project. If the
HTML file contains a `<meta name="date"...>` tag its content is used as revision for the project and all dependencies.

### Version Control System

To add support for another VCS the abstract
[VersionControlSystem](../../downloader/src/main/kotlin/VersionControlSystem.kt) class needs to be extended.

The [example VCS](src/main/kotlin/ArchiveOrgVcs.kt) is not a real VCS, but uses
[the wayback machine](https://archive.org/web/) to search for an archived version of the links found by the package
manager described above. It selects the entry closest to the requested revision.

### Scanner

To integrate a new scanner a [ScannerFactory](../../scanner/src/main/kotlin/ScannerFactory.kt) and a
[Scanner](../../scanner/src/main/kotlin/Scanner.kt) need to be implemented. Note that there is a
[LocalScanner](../../scanner/src/main/kotlin/LocalScanner.kt) class which makes it easier to integrate scanners that run
on the local machine. A service provider configuration file is only required for the `ScannerFactory`.

The [example scanner](src/main/kotlin/HtmlTagScanner.kt) counts the HTML tags found in each of the downloaded HTML
files. The result is stored in the `data` map of the scan summary.

### Reporter

To add a new reporter the abstract [Reporter](../../reporter/src/main/kotlin/Reporter.kt) class needs to be extended.

The [example reporter](src/main/kotlin/HtmlTagScanner.kt) summarizes the tag counts found by the HTML tag scanner and
shows the result on an HTML page. 

## Using plugins

If ORT components are used programmatically, as dependencies in another Java program, plugin components are
automatically loaded if the JAR file is added to the classpath.

For using plugins with the CLI their JAR files have to be added to the lib folder of the distribution. This folder is
located at `cli/build/install/ort/lib` after running `./gradlew cli:installDist` or inside the archives created by the
`distTar` or `distZip` tasks.

### Trying the example

The example project uses a third approach by adding the CLI module as a dependency and configuring the Gradle
application plugin to use the CLI main class. This makes it easy to test plugins manually and to create a custom ORT
distribution that bundles the plugins. To try the example use:

```bash
cd examples/plugin
./gradlew installDist

# Analyze the sample HTML file from the assets folder:
build/install/ort/bin/ort analyze -i assets -o [analyzer-output-folder]

# Scan the result for HTML tags:
build/install/ort/bin/ort scan -a [analyzer-output-folder]/analyzer-result.yml -o [scanner-output-folder] -s HtmlTagScanner

# Create an HTML tag report:
build/install/ort/bin/ort report -i [scanner-output-folder]/scan-result.yml -o [report-output-folder] -f HtmlTag
```
