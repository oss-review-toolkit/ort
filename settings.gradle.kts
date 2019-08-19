pluginManagement {
    resolutionStrategy {
        eachPlugin {
            // Work around https://github.com/gradle/gradle/issues/1697.
            if (requested.id.namespace != "org.gradle" && requested.version == null) {
                val versionPropertyName = if (requested.id.id.startsWith("org.jetbrains.kotlin.")) {
                    "kotlinPluginVersion"
                } else {
                    val pluginName = requested.id.name.split('-').joinToString("") { it.capitalize() }.decapitalize()
                    "${pluginName}PluginVersion"
                }

                logger.info("Checking for plugin version property '$versionPropertyName'.")

                gradle.rootProject.properties[versionPropertyName]?.let { version ->
                    logger.info("Setting '${requested.id.id}' plugin version to $version.")
                    useVersion(version.toString())
                } ?: logger.warn("No version specified for plugin '${requested.id.id}' and property " +
                        "'$versionPropertyName' does not exist.")
            }
        }
    }
}

rootProject.name = "oss-review-toolkit"

include("analyzer")
include("clearly-defined")
include("cli")
include("downloader")
include("evaluator")
include("helper-cli")
include("model")
include("reporter")
include("reporter-web-app")
include("scanner")
include("spdx-utils")
include("test-utils")
include("utils")
