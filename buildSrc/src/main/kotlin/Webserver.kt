import com.sun.net.httpserver.*
import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import java.io.*
import java.lang.management.*
import java.net.*
import java.nio.file.*
import java.util.concurrent.atomic.*
import kotlin.math.*

internal var _webServer: DecoratedHttpServer? = null

class DecoratedHttpServer(val server: HttpServer) {
    val port get() = server.address.port
    var updateVersion = AtomicInteger(0)
}

fun staticHttpServer(folder: File, address: String = "127.0.0.1", port: Int = 0, callback: (server: DecoratedHttpServer) -> Unit) {
    val server = staticHttpServer(folder, address, port)
    try {
        callback(server)
    } finally {
        server.server.stop(0)
    }
}

fun staticHttpServer(folder: File, address: String = "127.0.0.1", port: Int = 0): DecoratedHttpServer {
    val absFolder = folder.absoluteFile
    val server = HttpServer.create(InetSocketAddress(address, port), 0)
    val decorated = DecoratedHttpServer(server)

    fun getIpListFromIp(ip: String): List<String> = when (ip) {
        "0.0.0.0" -> try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
        } catch (e: Throwable) {
            e.printStackTrace()
            listOf(ip)
        }
        else -> listOf(ip)
    }

    println("Listening... (${ManagementFactory.getRuntimeMXBean().name}-${Thread.currentThread()}):")
    println("Serving... file://$folder")
    for (raddr in getIpListFromIp(address)) {
        println("  at http://$raddr:${server.address.port}/")
    }
    server.createContext("/") { t ->
        //println("t.requestURI.path=${t.requestURI.path}")
        if (t.requestURI.path == "/__version") {
            staticHttpServerRespond(t, TextContent("${decorated.updateVersion.get()}"))
        } else {
            val requested = File(folder, t.requestURI.path).absoluteFile

            if (requested.absolutePath.startsWith(absFolder.absolutePath)) {
                val req = if (requested.exists() && requested.isDirectory) File(requested, "index.html") else requested
                when {
                    req.exists() && !req.isDirectory -> staticHttpServerRespond(t, FileContent(req))
                    else -> staticHttpServerRespond(t, TextContent("<h1>404 - Not Found</h1>", contentType = "text/html"), code = 404)
                }
            } else {
                staticHttpServerRespond(t, TextContent("<h1>500 - Internal Server Error</h1>", contentType = "text/html"), code = 500)
            }
        }
    }
    server.start()
    return decorated
}

interface RangedContent {
    val length: Long
    val contentType: String
    fun write(out: OutputStream, range: LongRange)
}

class FileContent(val file: File) : RangedContent {
    override val length: Long by lazy { file.length() }
    override val contentType: String by lazy { file.miniMimeType() }

    private fun File.miniMimeType() = when (this.extension.toLowerCase()) {
        "htm", "html" -> "text/html"
        "css" -> "text/css"
        "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> if (this.exists()) Files.probeContentType(this.toPath()) ?: "application/octet-stream" else "text/plain"
    }

    override fun write(out: OutputStream, range: LongRange) {
        val len = (range.endInclusive - range.start) + 1
        //println("range=$range, len=$len")
        FileInputStream(file).use { f ->
            f.skip(range.start)
            var remaining = len
            val temp = ByteArray(64 * 1024)
            while (remaining > 0L) {
                val read = f.read(temp, 0, min(remaining, temp.size.toLong()).toInt())
                if (read <= 0) break
                //println("write $read")
                out.write(temp, 0, read)
                remaining -= read
            }
            //println("end")
        }
    }
}

open class TextContent(val text: String, override val contentType: String = "text/plain", val charset: java.nio.charset.Charset = Charsets.UTF_8) : ByteArrayContent(text.toByteArray(charset), contentType)

open class ByteArrayContent(val data: ByteArray, override val contentType: String) : RangedContent {
    override val length: Long get() = data.size.toLong()

    override fun write(out: OutputStream, range: LongRange) {
        out.write(data, range.start.toInt(), ((range.endInclusive - range.start) + 1).toInt())
    }
}

fun staticHttpServerRespond(ex: HttpExchange, content: RangedContent, headers: List<Pair<String, String>> = listOf(), code: Int? = null) {
    try {
        val range = ex.requestHeaders.getFirst("Range")
        val reqRange = if (range != null) {
            val rangeStr = range.removePrefix("bytes=")
            val parts = rangeStr.split("-", limit = 2)
            val start = parts.getOrNull(0)?.toLongOrNull() ?: error("Invalid request. Range: $range")
            val endInclusive = parts.getOrNull(1)?.toLongOrNull() ?: Long.MAX_VALUE
            start.coerceIn(0L, content.length - 1)..endInclusive.coerceIn(0L, content.length - 1)
        } else {
            null
        }
        val totalRange = 0L until content.length

        val partial = reqRange != null
        val length = if (partial) reqRange!!.endInclusive - reqRange.start + 1 else content.length

        ex.responseHeaders.add("Content-Type", content.contentType)
        ex.responseHeaders.add("Accept-Ranges", "bytes")
        //println("Partial: $content, $partial, $range, $reqRange")
        if (partial) {
            ex.responseHeaders.set(
                "Content-Range",
                "bytes ${reqRange!!.start}-${reqRange!!.endInclusive}/${content.length}"
            )
        }

        for (header in headers) {
            ex.responseHeaders.add(header.first, header.second)
        }
        ex.sendResponseHeaders(if (partial) 206 else code ?: 200, length)

        // Send body if not HEAD
        if (!ex.requestMethod.equals("HEAD", ignoreCase = true)) {
            //println("${this.requestMethod}")
            ex.responseBody.use { os ->
                content.write(os, reqRange ?: totalRange)
            }
        } else {
            //println("HEAD")
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

fun Project.openBrowser(url: String) {
    exec {
        when {
            isWindows -> {
                commandLine("cmd", "/c", "explorer.exe $url")
                isIgnoreExitValue = true
            }
            isLinux -> commandLine("xdg-open", url)
            else -> commandLine("open", url)
        }
    }
}

fun Project.runServer(blocking: Boolean) {
    if (_webServer == null) {
        val address = webBindAddress
        val port = webBindPort
        val server = staticHttpServer(wwwFolder, address = address, port = port)
        _webServer = server
        try {
            val openAddress = when (address) {
                "0.0.0.0" -> "127.0.0.1"
                else -> address
            }
            openBrowser("http://$openAddress:${server.port}/index.html")
            if (blocking) {
                while (true) {
                    Thread.sleep(1000L)
                }
            }
        } finally {
            if (blocking) {
                println("Stopping web server...")
                server.server.stop(0)
                _webServer = null
            }
        }
    }
    _webServer?.updateVersion?.incrementAndGet()
}

fun Project.configureWebserver() {
    val browserDebugEsbuildRun by tasks.creating(Task::class) {
        dependsOn("browserDebugEsbuild")
        doLast {
            runServer(!project.gradle.startParameter.isContinuous)
        }
    }

    val browserReleaseEsbuildRun by tasks.creating(Task::class) {
        dependsOn("browserReleaseEsbuild")
        doLast {
            runServer(!project.gradle.startParameter.isContinuous)
        }
    }

    val jsStopWeb by tasks.creating(Task::class) {
        doLast {
            println("jsStopWeb: ${ManagementFactory.getRuntimeMXBean().name}-${Thread.currentThread()}")
            _webServer?.server?.stop(0)
            _webServer = null
        }
    }

}