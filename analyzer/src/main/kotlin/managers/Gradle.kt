package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManager
import com.here.ort.model.ScanResult
import com.here.ort.util.OS

import java.io.File

object Gradle : PackageManager(
        "https://gradle.org/",
        "Java",
        listOf("build.gradle")
) {
    val gradle: String
    val wrapper:String

     init {
         if (OS.isWindows) {
             gradle = "gradle.bat"
             wrapper = "gradlew.bat"
         } else {
             gradle = "gradle"
             wrapper = "gradlew"
         }
     }

    override fun command(workingDir: File): String {
        return if (File(workingDir, wrapper).isFile) wrapper else gradle
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, ScanResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
