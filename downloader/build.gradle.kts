val svnkitVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":model"))

    implementation(project(":utils"))

    implementation("org.tmatesoft.svnkit:svnkit:$svnkitVersion")
}
