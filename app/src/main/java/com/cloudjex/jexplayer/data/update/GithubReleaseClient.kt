package com.cloudjex.jexplayer.data.update

import com.cloudjex.jexplayer.data.local.MiniJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

interface ReleaseClient {
    suspend fun fetchReleases(): List<GithubRelease>
}

class GithubReleaseClient(
    private val endpoint: String = "https://api.github.com/repos/Cloudjexp/JexPlayer/releases?per_page=100"
) : ReleaseClient {
    override suspend fun fetchReleases(): List<GithubRelease> = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "JexPlayer")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val root = MiniJson.parse(connection.inputStream.bufferedReader().use { it.readText() })
            require(root is List<*>) { "Respuesta de releases inválida" }
            root.mapNotNull { item ->
                val release = item as? Map<*, *> ?: return@mapNotNull null
                if (release["draft"] == true) return@mapNotNull null
                val tag = release["tag_name"] as? String ?: return@mapNotNull null
                val url = release["html_url"] as? String ?: return@mapNotNull null
                GithubRelease(tag, release["body"] as? String ?: "", url, release["prerelease"] == true)
            }
        } finally {
            connection.disconnect()
        }
    }
}
