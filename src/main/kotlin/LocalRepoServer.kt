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
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

data class RepoistoryInformation(val name: String, val url: String)

fun asCacheName(target: String): String {
    return DigestUtils.sha256Hex(target!!)
}

data class Entry(val target: String, val url: String, val sha1: String,
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
        return load(target)?.url
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
                headers)
        FileWriter(toFile(".${asCacheName(target)}.json")).use {
            it.write(gson.toJson(entry))
        }
        logger.info("create cache at ${File(".${asCacheName(target)}.json").absolutePath}")
    }

    private fun load(target: String): Entry? {
        logger.info("load cache at ${File(".${asCacheName(target)}.json").absolutePath}")
        val file = toFile(".${asCacheName(target)}.json")
        if (!file.exists()) {
            return null
        }
        val content = FileUtils.readFileToString(file, "utf-8")
        return gson.fromJson<Entry>(content, Entry::class.java)
    }

    fun get(target: String): Map<String, String>? {
        return load(target)?.headers
    }


}


class GlobalHandler(val repos: Set<RepoistoryInformation>) : AbstractHandler() {

    override fun handle(target: String?, baseRequest: Request?, request: HttpServletRequest?,
                        response: HttpServletResponse?) {

        val method = request?.method!!
        when (method) {
            "HEAD" -> {
                logger.info("head target: $target")
                doHead(target!!, baseRequest!!, response!!)
            }
            "GET" -> {
                if (target!!.endsWith(".sha1")) {
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
                logger.info("get target: $target => ${Cache.getUrl(target!!)}")
                val location = URL(Cache.getUrl(target!!)).toString()
                if (location.endsWith("sha1")) {
                    logger.info("resolve sha1" + location.substringBeforeLast("sha1"))
                }
                logger.info("redirect to $location")
                response!!.sendRedirect(location)
            }
            else -> {
                throw IllegalStateException("maven-proxy only accepts HEAD or GET request")
            }
        }
    }

    private fun doHead(target: String, baseRequest: Request, response: HttpServletResponse): Boolean {

        if (Cache.get(target) != null) {
            Cache.get(target)!!.forEach {
                val header = it.key
                val value = it.value
                if (header != null) {
                    response.setHeader(header, value)
                }
            }
            logger.info("found at cache[$target]")
            baseRequest.isHandled = true
            return true
        }

        repos.forEach {
            val baseUrl = URL(it.url)
            val url = "$baseUrl$target"
            if (doHeadForEachRepository(target, URL(url), baseRequest, response)) {
                return true
            }
        }
        // TODO when sources.jar not found, would we do anything ?
        return false
    }

    private fun doHeadForEachRepository(target: String, url: URL, baseRequest: Request, response: HttpServletResponse): Boolean {
        logger.info("check url $url")
        val httpConnection = (url.openConnection() as HttpURLConnection)
        httpConnection.instanceFollowRedirects = true
        httpConnection.requestMethod = "HEAD"
        baseRequest.headerNames!!.asSequence().forEach { header ->
            httpConnection.setRequestProperty(header, baseRequest.getHeader(header))
        }

        if (httpConnection.responseCode != 200) {
            return false
        }

        response.status = 200
        Cache.add(target, httpConnection)
        Cache.get(target)!!.forEach {
            val header = it.key
            val value = it.value
            if (header != null) {
                response.setHeader(header, value)
            }
        }
        baseRequest.isHandled = true
        return true
    }
}