
# Maven Metadata Cache Plugin

A gradle plugin act as a maven proxy that can cache the metadata for artifacts. By the way, it is my first Kotlin pet project.

# How to use it

### For regular usage

Our plugin is published to jitpack.io, you can install it in that way:

```groovy
buildscript {
    repositories {
        maven { url 'https://jitpack.io' }
        mavenCentral()
    }
    dependencies {
        classpath 'com.github.qrtt1:gradle-maven-metadata-cache-plugin:v0.1-alpha.3'
    }
}
```

### For latest build

Another way to get the built plugin is publishing the plugin at local maven repo:

```
./gradlew uploadArchives
```

Finally, you got a maven repo:

```
qty:maven.cache qrtt1$ tree build/maven-repo/
build/maven-repo/
└── org
    └── qrtt1
        └── maven.cache
            ├── 0.1
            │   ├── maven.cache-0.1.jar
            │   ├── maven.cache-0.1.jar.md5
            │   ├── maven.cache-0.1.jar.sha1
            │   ├── maven.cache-0.1.pom
            │   ├── maven.cache-0.1.pom.md5
            │   └── maven.cache-0.1.pom.sha1
            ├── maven-metadata.xml
            ├── maven-metadata.xml.md5
            └── maven-metadata.xml.sha1
```

And configure it in the `buildscript`:

```groovy
buildscript {
    repositories {
        maven {
            url = '/path/to/local/maven-repo'
        }
        mavenCentral()
    }
    dependencies {
        classpath group: 'org.qrtt1', name: 'maven.cache', version: '0.1'
    }
}

apply plugin: org.qrtt1.gradle.MavenCacheRuleSource
```

### For development

If someone wants to verify it quickly can clone this repository into `buildSrc` directory. Apply plugin:

```groovy
apply plugin: org.qrtt1.gradle.MavenCacheRuleSource
```


# How does it work ?

1. during gradle task graph creation, this pluing will scan all `repositories` and replace any `http[s]` protocol to a local proxy
2. when gradle building and trying access the maven repository, our proxy will answer any metadata including the sha1 of a file or HEAD request

## Where do caches locate ?

It puts at the `caches` directory with GRADLE\_HOME. Take my computer as an instance, it goes into

```
/Users/qrtt1/.gradle/caches/org.qrtt1.maven-metadata-cache
```

```
qty:org.qrtt1.maven-metadata-cache qrtt1$ ls -a | head
.
..
.0575e685c1a0d4026bf658462fdd313fe400d3750587a2141d3b59252aaaed8c.json
.05cb4c93c203ee50a4a774d733b6d29aa45a1dc42d9277f5f845a049435842c5.json
.09092d50943f42874fb26ef6bc0e2fba5905d045111152212ba155874ef8ba65.json
```

Each cache keeps the URI and URL with its sha1 and original server response headers:

```json
{
  "target": "/com/android/support/test/espresso/espresso-idling-resource/3.0.1/espresso-idling-resource-3.0.1.pom",
  "url": "https://dl.google.com/dl/android/maven2/com/android/support/test/espresso/espresso-idling-resource/3.0.1/espresso-idling-resource-3.0.1.pom",
  "sha1": "c437e84474d3683dcf3801293b4b9cc1ec43fbd9",
  "headers": {
    "Alt-Svc": "hq=\":443\"; ma=2592000; quic=51303431; quic=51303339; quic=51303338; quic=51303337; quic=51303335,quic=\":443\"; ma=2592000; v=\"41,39,38,37,35\"",
    "Server": "downloads",
    "X-Content-Type-Options": "nosniff",
    "Last-Modified": "Mon, 28 Aug 2017 19:43:33 GMT",
    "Date": "Mon, 22 Jan 2018 14:55:11 GMT",
    "X-Frame-Options": "SAMEORIGIN",
    "Accept-Ranges": "bytes",
    "Etag": "\"169901\"",
    "Cache-Control": "public,max-age=86400",
    "X-Xss-Protection": "1; mode=block",
    "Content-Length": "1092",
    "Content-Type": "application/octet-stream"
  }
}
```

# Known Issues

Known issues means something we know it but won't fix it (it can't be fixed).

## Plugin has no chance to resolve by proxy when the project has been resolved

Consider this situation:

```groovy
configurations.findAll() {
  println it
}

apply plugin: org.qrtt1.gradle.MavenCacheRuleSource
```

The script try to resolve dependencis forcely, and it might happen before our plugin applied.
That causes our plugin never be asked dependencies from Gradle's depdendency-manager.

