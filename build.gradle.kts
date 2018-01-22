import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.qrtt1"
version = "1.0-SNAPSHOT"

buildscript {
    var kotlin_version: String by extra
    kotlin_version = "1.2.0"

    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin", kotlin_version))
    }

}

apply {
    plugin("java")
    plugin("kotlin")
}

val kotlin_version: String by extra

repositories {
    mavenCentral()
}

dependencies {
    compile("commons-codec:commons-codec:1.11")
    compile("com.google.code.gson:gson:2.8.2")
    compile("org.eclipse.jetty:jetty-server:9.4.8.v20171121")
    compile("commons-io:commons-io:2.6")
    compile(gradleApi())
    compile(kotlinModule("stdlib-jdk8", kotlin_version))
    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

