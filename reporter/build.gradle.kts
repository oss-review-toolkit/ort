val apachePoiVersion: String by project
val apachePoiSchemasVersion: String by project
val hamcrestCoreVersion: String by project
val kotlinxHtmlVersion: String by project
val simpleExcelVersion: String by project
val xalanVersion: String by project

plugins {
    // Apply core plugins.
    id("java-library")
}

val generatedResourcesDir = file("$buildDir/generated-resources/main")
val copyWebAppTemplate by tasks.registering(Copy::class) {
    dependsOn(":reporter-web-app:yarnBuild")

    from(project(":reporter-web-app").file("build")) {
        include("scan-report-template.html")
    }

    into(generatedResourcesDir)
}

sourceSets.named("main") {
    output.dir(mapOf("builtBy" to copyWebAppTemplate), generatedResourcesDir)
}

repositories {
    maven("http://www.robotooling.com/maven/")
}

dependencies {
    api(project(":model"))

    implementation(project(":utils"))

    implementation("org.apache.poi:ooxml-schemas:$apachePoiSchemasVersion")
    implementation("org.apache.poi:poi-ooxml:$apachePoiVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinxHtmlVersion")

    // This is required to not depend on the version of Apache Xalan bundled with the JDK. Otherwise the formatting of
    // the HTML generated in StaticHtmlReporter is slightly different with different Java versions.
    implementation("xalan:xalan:$xalanVersion")

    funTestImplementation("bad.robot:simple-excel:$simpleExcelVersion")
    funTestImplementation("org.hamcrest:hamcrest-core:$hamcrestCoreVersion")
}
