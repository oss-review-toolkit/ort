// These interfaces must match the interfaces defined in src/resources/init.gradle because they are used to deserialize
// the model created there. As it is not possible to declare a package in init.gradle also no package is declared
// here.

interface DependencyTreeModel {
    val name: String
    val configurations: List<Configuration>
    val errors: List<String>
}

interface Configuration {
    val name: String
    val dependencies: List<Dependency>
}

interface Dependency {
    val groupId: String
    val artifactId: String
    val version: String
    val dependencies: List<Dependency>
    val error: String?
}
