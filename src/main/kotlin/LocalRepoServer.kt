package org.qrtt1.gradle


import com.google.gson.Gson
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.gradle.wrapper.GradleUserHomeLookup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

data class RepositoryInformation(val name: String, val url: String)

fun asCacheName(target: String): String {
    return DigestUtils.sha256Hex(target)
}

data class Entry(val target: String,
                 val url: String,
                 val sha1: String,
                 val found: Boolean,
                 val headers: Map<String, String>)

object Cache {

    private val gson = Gson()
    private val base = File(GradleUserHomeLookup.gradleUserHome(), "caches/org.qrtt1.maven-metadata-cache")

    init {
        base.mkdirs()
    }

    private fun toFile(filename: String): File {
        return File(base, filename)
    }

    fun getUrl(target: String): String? {
        val url = load(target)?.url
        if (url != null || url != "") {
            return url
        }
        return null
    }

    fun getSha1(target: String): String? {
        logger.info("getSha1 from $target")
        if (!target.endsWith(".sha1")) {
            return null
        }
        return load(target.substringBeforeLast(".sha1"))?.sha1
    }

    private fun sha1(url: String): String {
        var sha1Url = URL(url + ".sha1")
        val httpConnection = (sha1Url.openConnection() as HttpURLConnection)
        httpConnection.instanceFollowRedirects = true
        httpConnection.connectTimeout = MavenProxy.httpTimeOut
        httpConnection.readTimeout = MavenProxy.httpTimeOut

        MavenProxy.configureRealm(httpConnection)
        if (httpConnection.responseCode != 200) {
            throw IllegalStateException("Cannot get the sha1 from $sha1Url")
        }
        val output = ByteArrayOutputStream()
        httpConnection.inputStream.use {
            IOUtils.copy(it, output)
        }
        return String(output.toByteArray())
    }

    fun add(target: String, connection: HttpURLConnection) {
        if (connection.responseCode != 200) {
            return
        }

        val headers = mutableMapOf<String, String>()
        connection.headerFields.forEach {
            val header = it.key
            val value = it.value.first()
            if (header != null) {
                headers[header] = value
            }
        }
        val entry = Entry(target,
                connection.url.toString(),
                sha1(connection.url.toString()),
                true,
                headers)
        val file = toFile(".${asCacheName(target)}.json")
        file.parentFile.mkdirs()
        FileWriter(file).use {
            it.write(gson.toJson(entry))
        }
        logger.info("create cache at $file")
    }

    fun markLost(target: String) {
        val file = toFile(".${asCacheName(target)}.json")
        if (file.exists()) {
            return
        }

        val entry = Entry(target, "", "", false, mapOf())
        file.parentFile.mkdirs()
        FileWriter(file).use {
            it.write(gson.toJson(entry))
        }
    }

    private fun load(target: String): Entry? {
        val file = toFile(".${asCacheName(target)}.json")
        if (!file.exists()) {
            return null
        }
        logger.info("load cache from $file")
        val content = FileUtils.readFileToString(file, "utf-8")
        return gson.fromJson<Entry>(content, Entry::class.java)
    }

    fun get(target: String): Entry? {
        return load(target)
    }

    fun getHeader(target: String): Map<String, String>? {
        return load(target)?.headers
    }


}


class GlobalHandler(val repos: Set<RepositoryInformation>) : AbstractHandler() {

    override fun handle(originTarget: String?, baseRequest: Request?, request: HttpServletRequest?,
                        response: HttpServletResponse?) {

        val target = originTarget!!.substring(1)
        val method = request?.method!!
        when (method) {
            "HEAD" -> {
                logger.info("head target: $target")
                doHead(target, baseRequest!!, response!!)
            }
            "GET" -> {
                if (target.endsWith(".sha1")) {
                    val sha1 = Cache.getSha1(target)
                    logger.info("response sha1 directly: $sha1")
                    if (sha1 != null) {
                        baseRequest!!.isHandled = true
                        response!!.outputStream.use {
                            it.write(sha1.toByteArray())
                        }
                        return
                    }
                }
                logger.info("get target: $target => ${Cache.getUrl(target)}")
                val entry = Cache.get(target)
                var resolvedUrl = entry?.url
                if (entry == null) {
                    probeFile(target, baseRequest!!)
                    if (!Cache.get(target)!!.found) {
                        baseRequest!!.isHandled = true
                        response!!.status = 404
                        return
                    }
                    resolvedUrl = Cache.getUrl(target)
                    logger.warn("probe-file[$target] before get => $resolvedUrl")
                }
                val location = URL(resolvedUrl).toString()
                if (location.endsWith("sha1")) {
                    logger.info("resolve sha1" + location.substringBeforeLast("sha1"))
                }
                logger.info("redirect to $location")
                if (MavenProxy.isAuthorizationRequired(location)) {
                    val httpUrlConnection = URL(location).openConnection() as HttpURLConnection
                    httpUrlConnection.connectTimeout = MavenProxy.httpTimeOut
                    httpUrlConnection.readTimeout = MavenProxy.httpTimeOut
                    MavenProxy.configureRealm(httpUrlConnection)
                    if (httpUrlConnection.responseCode == 200) {
                        response!!.setContentLengthLong(httpUrlConnection.contentLengthLong)
                        response.outputStream.use {
                            IOUtils.copy(httpUrlConnection.inputStream, it)
                        }
                        return
                    }

                } else {
                    response!!.sendRedirect(location)
                }
            }
            else -> {
                throw IllegalStateException("maven-proxy only accepts HEAD or GET request")
            }
        }
    }

    private fun doHead(target: String, baseRequest: Request, response: HttpServletResponse): Boolean {

        val entry: Entry? = Cache.get(target)
        if (entry != null && entry.found) {
            Cache.getHeader(target)!!
                    .filter {
                        // when header is represent for a status line, it will be null
                        Optional.ofNullable(it.key).isPresent
                    }
                    .forEach {
                        response.setHeader(it.key, it.value)
                    }
            logger.info("found cache[$target]")
            baseRequest.isHandled = true
            return true
        }

        if (probeFile(target, baseRequest)) {
            Cache.getHeader(target)!!.forEach {
                response.setHeader(it.key, it.value)
            }
            baseRequest.isHandled = true
            return true
        }

        return false
    }


    private fun probeFile(target: String, baseRequest: Request): Boolean {

        fun hasFile(target: String, url: URL): Boolean {
            logger.info("check url $url")
            val httpConnection = (url.openConnection() as HttpURLConnection)
            httpConnection.instanceFollowRedirects = true
            httpConnection.requestMethod = "HEAD"
            httpConnection.connectTimeout = MavenProxy.httpTimeOut
            httpConnection.readTimeout = MavenProxy.httpTimeOut
            MavenProxy.configureRealm(httpConnection)
            baseRequest.headerNames!!.asSequence().forEach { header ->
                httpConnection.setRequestProperty(header, baseRequest.getHeader(header))
            }

            logger.info("resolve file[$target](status: ${httpConnection.responseCode}) from [$url]")
            if (httpConnection.responseCode != 200) {
                return false
            }

            try {
                Cache.add(target, httpConnection)
            } catch (e: Exception) {
                e.printStackTrace()
                throw  e
            }
            return true
        }

        repos.forEach {

            // ensure the url of the repository is ending with '/'
            var baseUrl = it.url
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/"
            }

            if (hasFile(target, URL("$baseUrl$target"))) {
                return true
            }
        }

        if (target.endsWith("-sources.jar")) {
            Cache.markLost(target)
        } else {
            logger.warn("cannot find the file for $target")
        }
        return false
    }

}
