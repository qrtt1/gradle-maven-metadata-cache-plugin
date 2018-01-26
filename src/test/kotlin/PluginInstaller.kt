package org.qrtt1.gradle

import org.apache.commons.logging.LogFactory
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean


object PluginInstaller {

    private val logger = LogFactory.getLog(PluginInstaller::class.java)

    private val installed = AtomicBoolean(false)
    var developMaven = ""

    @Synchronized
    fun install() {
        if (installed.get()) {
            return
        }

        try {
            val dir = File(PluginInstaller::class.java.getResource("/").toURI())
            val buildScript = findBuildScript(dir, 0)

            val result = GradleRunner.create().withProjectDir(buildScript.parentFile)
                    .withArguments("uploadArchives").build()

            if (result.task(":uploadArchives").outcome != SUCCESS) {
                throw RuntimeException("failed to install plugin to custom-maven")
            }

            val maven = File(buildScript.parent, "build/maven-repo")
            if (!maven.exists()) {
                throw RuntimeException("failed to locate custom-maven: " + maven.absolutePath)
            }
            
            developMaven = maven.absolutePath
            installed.set(true)
        } catch (e: Exception) {
            throw RuntimeException("Cannot install plugin !!!", e)
        }
    }

    fun findBuildScript(dir: File, depth: Int): File {
        val buildScript = File(dir, "build.gradle.kts")
        if (buildScript.exists()) {
            return buildScript
        }

        if (depth > 3) {
            throw RuntimeException("no build.gradle found at " + dir)
        }

        return findBuildScript(dir.parentFile, depth + 1)
    }

}