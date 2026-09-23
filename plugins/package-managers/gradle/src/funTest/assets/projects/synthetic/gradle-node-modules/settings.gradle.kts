rootProject.name = "gradle-node-modules-project"

// Simulate the autolinking mechanism used e.g. by React Native, where native modules that are actually managed by
// another package manager (like Yarn, via a "node_modules" directory) get included as regular Gradle subprojects.
include(":example-native-module")
project(":example-native-module").projectDir = file("node_modules/example-native-module/android")

