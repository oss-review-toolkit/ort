# ORT Plugin API

The ORT Plugin API defines the interfaces that ORT plugin extension points must use and that ORT plugins must implement.

## Plugin Extension Points

Plugin extension points must define the base class for the plugin and an interface for a factory that creates instances of the plugin.
For example, the extension point for advisor plugins is defined in the `AdviceProvider` interface and the `AdviceProviderFactory` interface.

### Plugin Base Class

The plugin base class defines the functions and properties that plugins must implement.
The base class can be either an interface or an abstract class and must extend the `Plugin` interface.
If it is an abstract class, it must not take any constructor arguments, as this would make it impossible to define a generic factory function for all plugins.

For example, the `AdviceProvider` interface defines one function and one property that all advisor plugins must implement:

```kotlin
interface AdviceProvider : Plugin {
    val details: AdvisorDetails
    suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult>
}
```

In addition, the `Plugin` interface defines a `descriptor` property that contains metadata about the plugin:

```kotlin
interface Plugin {
    val descriptor: PluginDescriptor
}
```

### Plugin Factory Interface

The plugin factory interface is a markup interface that extends the `PluginFactory` interface and defines the plugin base class as a type parameter.

For example, the `AdviceProviderFactory` interface defines that the base class for advisor plugins is `AdviceProvider`:

```kotlin
interface AdviceProviderFactory : PluginFactory<AdviceProvider>
```

The `create` function defined by the `PluginFactory` interface takes has a `PluginConfig` parameter that contains the configuration for the plugin.
The `PluginFactory` provides a generic `getAll<T>()` function that returns all available plugin factories of the given type.
It uses the service loader mechanism to find the available plugin factories.

## Plugin Implementations

Plugin implementations consist of a class that implements the plugin base class, a factory class that implements the factory interface, and a service loader configuration file.
If the plugin has configuration options, it must implement an additional data class as a holder for the configuration.

To reduce the amount of boilerplate code, ORT provides a compiler plugin that can generate the factory class and the service loader file.
The compiler plugin uses the [Kotlin Symbol Processing (KSP) API](https://kotlinlang.org/docs/ksp-overview.html).
With this, the plugin implementation only needs to implement the plugin class and the configuration data class.

### Plugin Class

To be able to use the compiler plugin, the plugin class must follow certain conventions:

* It must be annotated with the `@OrtPlugin` annotation which takes some metadata and the factory interface as arguments.
* It must have a single constructor that takes one or two arguments:
  The first one must override the `descriptor` property of the `Plugin` interface.
  Optionally, the second one must be called `config` and must be of the type of the configuration data class.

For example, an advisor plugin implementation could look like this:

```kotlin
@OrtPlugin(
    name = "Example Advisor",
    description = "An example advisor plugin.",
    factory = AdviceProviderFactory::class
)
class ExampleAdvisor(override val descriptor: PluginDescriptor, val config: ExampleConfiguration) : AdviceProvider {
    ...
}
```

### Plugin Configuration Class

The configuration class must be a data class with a single constructor that takes all configuration options as arguments.
To be able to use the compiler plugin, the configuration class must follow certain conventions:

* All constructor arguments must be `val`s.
* Constructor arguments must have one of the following types: `Boolean`, `Int`, `Long`, `Secret`, `String`, `List<String>`.
* Constructor arguments must not have a default value.
  Instead, the default value can be set by adding the `@OrtPluginOption` annotation to the property.
  This is required for code generation because KSP does not provide any details about default values of constructor arguments.
  Also, to be able to handle default values in the compiler plugin, they must be compile time constants which also applies to annotation arguments.
* Constructor arguments can be nullable if they are optional.
* If a constructor argument is not nullable and has no default value, the argument is required and the generated factory will throw an exception if it cannot be found in the `PluginConfig`.
* The compiler plugin will use the KDoc of a constructor argument as the description of the option when generating the `PluginDescriptor`.

The generated factory class will take option values from the `PluginConfig.options` map and use them to create an instance of the configuration class.
The only exception are `Secret` properties which are taken from the `PluginConfig.secrets` map.

For example, an advisor plugin configuration could look like this:

```kotlin
data class ExampleConfiguration(
    /** The REST API server URL. */
    @OrtPluginOption(defaultValue = "https://example.com")
    val serverUrl: String,
    
    /** The timeout in seconds for REST API requests. */
    val timeout: Int,
    
    /** The API token to use for authentication. */
    val token: Secret?
)
```

Here, the `serverUrl` property has a default value, the `timeout` property is required, and the `token` property is optional.

### Gradle Configuration

A Gradle module that contains an ORT plugin implementation must apply the `com.google.devtools.ksp` Gradle plugin and add dependencies to the ORT compiler plugin and the API of the implemented extension point to the KSP configuration:

```kotlin
plugins {
    id("com.google.devtools.ksp:[version]")
}

dependencies {
    ksp("org.ossreviewtoolkit:advisor:[version]")
    ksp("org.ossreviewtoolkit:compiler:[version]")
}
```

In the ORT codebase, the `ort-plugin-conventions` should be applied so that only a dependency on the extension point API is required:

```kotlin
plugins {
    id("ort-plugin-conventions")
}

dependencies {
    ksp(projects.advisor)
}
```
