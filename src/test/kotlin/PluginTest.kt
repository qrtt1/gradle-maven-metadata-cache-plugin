package org.qrtt1.gradle

import junit.framework.Assert.assertNotNull
import org.gradle.testkit.runner.GradleRunner
import org.gradle.wrapper.GradleUserHomeLookup
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.*


class GoogleTest : GradlePluginTest() {

    @Test
    override fun testProxyWithRepository() {
        val result = testRepository("google()",
                "implementation 'com.android.support:appcompat-v7:26.1.0'")
        assertNotNull(parseResolvingResult(result!!).filter {
            it.file == "com/android/support/appcompat-v7/26.1.0/appcompat-v7-26.1.0.aar"
        }.singleOrNull())
    }

}


class JCenterTest : GradlePluginTest() {

    @Test
    override fun testProxyWithRepository() {
        val result = testRepository("jcenter()",
                "compile group: 'com.google.code.gson', name: 'gson', version: '2.8.2'")
        assertNotNull(parseResolvingResult(result!!).filter {
            it.file == "com/google/code/gson/gson/2.8.2/gson-2.8.2.jar"
        }.singleOrNull())
    }

}

class MavenCentralTest : GradlePluginTest() {

    @Test
    override fun testProxyWithRepository() {
        val result = testRepository("mavenCentral()",
                "compile group: 'com.google.code.gson', name: 'gson', version: '2.8.1'")

        assertNotNull(parseResolvingResult(result!!).filter {
            it.file == "com/google/code/gson/gson/2.8.1/gson-2.8.1.jar"
        }.singleOrNull())
    }

}

class JitpackTest : GradlePluginTest() {

    @Test
    override fun testProxyWithRepository() {
        val result = testRepository("""
            maven { url 'https://jitpack.io' }
            mavenCentral()
        """.trimIndent(),
                "compile 'com.github.qrtt1:gradle-maven-metadata-cache-plugin:v0.1-alpha.2'")

        assertNotNull(parseResolvingResult(result!!).filter {
            it.status == 200 &&
                    it.file == "com/github/qrtt1/gradle-maven-metadata-cache-plugin/v0.1-alpha.2/gradle-maven-metadata-cache-plugin-v0.1-alpha.2.jar"
        }.singleOrNull())
    }

}


data class Resolved(val file: String, val status: Int, val url: String)

fun parseResolvingResult(input: String): List<Resolved> {
    return "resolve file\\[([^\\[]+)\\]\\(status: (\\d+)\\) from \\[([^\\[]+)\\]".toRegex().findAll(input)
            .map {
                Resolved(it.groups[1]!!.value,
                        Integer.parseInt(it.groups[2]!!.value),
                        it.groups[3]!!.value
                )

            }.toList()
}

abstract class GradlePluginTest {

    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    private var buildFile: File? = null

    @Before
    @Throws(IOException::class)
    fun setup() {
        PluginInstaller.install()
        buildFile = testProjectDir.newFile("build.gradle")
    }

    abstract fun testProxyWithRepository()

    fun testRepository(repositoryDefition: String, dependencyNotation: String): String? {
        val property = GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY
        var randomGradleHome = File(testProjectDir.root, UUID.randomUUID().toString())
        randomGradleHome.mkdirs()

        val script = """
            buildscript {
                // change default gradle-home to force proxy always downloading new files
                System.setProperty("$property", "${randomGradleHome.absolutePath}")
                repositories {
                    maven {
                        url = "${PluginInstaller.developMaven}"
                    }
                    mavenCentral()
                }
                dependencies {
                    classpath group: 'org.qrtt1', name: 'maven.cache', version: '0.1'
                }
            }

            apply plugin: 'java'
            apply plugin: org.qrtt1.gradle.MavenCacheRuleSource

            repositories {
                $repositoryDefition
            }

            dependencies {
                 $dependencyNotation
            }

            task sayMyName {
                doLast {
                    // force resolving libraries
                    configurations.findAll().each { c ->
                        try {
                            c.findAll()
                        } catch (Exception ignored) { }
                    }

                    repositories.findAll() {
                        println "repo: " + it.name + " => " + it.url
                    }
                }

            }
            """.trimIndent()

        buildFile!!.writeText(script)
        println(script)

        val result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("--info", "sayMyName")
                .build()

        println(testProjectDir.root)
        testProjectDir.root.walkTopDown().asSequence().forEach {
            println(it)
        }

        println(result.output)
        return result.output
    }

}