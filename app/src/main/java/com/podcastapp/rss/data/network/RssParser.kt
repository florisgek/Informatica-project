package com.podcastapp.rss.data.network

import com.podcastapp.rss.data.model.Episode
import com.podcastapp.rss.data.model.Podcast
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * RSS Feed Parser for podcast feeds
 * Supports RSS 2.0 and common podcast extensions (iTunes, etc.)
 */
class RssParser {

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    )

    fun parse(feedUrl: String, xmlContent: String): Podcast {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xmlContent)))

        val channel = document.getElementsByTagName("channel").item(0)

        var title = ""
        var description = ""
        var imageUrl: String? = null
        var author: String? = null
        var link: String? = null
        var language: String? = null
        val episodes = mutableListOf<Episode>()

        // Parse channel info
        val channelNodes = channel.childNodes
        for (i in 0 until channelNodes.length) {
            val node = channelNodes.item(i)
            when (node.nodeName) {
                "title" -> title = node.textContent?.trim() ?: ""
                "description" -> description = cleanHtml(node.textContent?.trim() ?: "")
                "link" -> link = node.textContent?.trim()
                "language" -> language = node.textContent?.trim()
                "itunes:author", "author" -> author = node.textContent?.trim()
                "itunes:image" -> {
                    imageUrl = node.attributes?.getNamedItem("href")?.textContent
                }
                "image" -> {
                    // Standard RSS image
                    val imageNodes = node.childNodes
                    for (j in 0 until imageNodes.length) {
                        if (imageNodes.item(j).nodeName == "url") {
                            imageUrl = imageUrl ?: imageNodes.item(j).textContent?.trim()
                        }
                    }
                }
                "item" -> {
                    parseEpisode(node)?.let { episodes.add(it) }
                }
            }
        }

        return Podcast(
            title = title,
            description = description,
            feedUrl = feedUrl,
            imageUrl = imageUrl,
            author = author,
            link = link,
            language = language,
            episodes = episodes.sortedByDescending { it.publishDate }
        )
    }

    private fun parseEpisode(itemNode: org.w3c.dom.Node): Episode? {
        var title = ""
        var description = ""
        var audioUrl = ""
        var imageUrl: String? = null
        var duration: Long = 0
        var durationText: String? = null
        var publishDate: Long = 0
        var publishDateText: String? = null
        var fileSize: Long = 0
        var guid: String? = null

        val itemNodes = itemNode.childNodes
        for (i in 0 until itemNodes.length) {
            val node = itemNodes.item(i)
            when (node.nodeName) {
                "title" -> title = node.textContent?.trim() ?: ""
                "description", "itunes:summary" -> {
                    if (description.isEmpty()) {
                        description = cleanHtml(node.textContent?.trim() ?: "")
                    }
                }
                "enclosure" -> {
                    val attrs = node.attributes
                    val type = attrs?.getNamedItem("type")?.textContent ?: ""
                    if (type.startsWith("audio/") || type.isEmpty()) {
                        audioUrl = attrs?.getNamedItem("url")?.textContent ?: ""
                        fileSize = attrs?.getNamedItem("length")?.textContent?.toLongOrNull() ?: 0
                    }
                }
                "itunes:duration" -> {
                    durationText = node.textContent?.trim()
                    duration = parseDuration(durationText)
                }
                "pubDate" -> {
                    publishDateText = node.textContent?.trim()
                    publishDate = parseDate(publishDateText)
                }
                "itunes:image" -> {
                    imageUrl = node.attributes?.getNamedItem("href")?.textContent
                }
                "guid" -> guid = node.textContent?.trim()
                "link" -> {
                    // Some feeds use link instead of enclosure
                    if (audioUrl.isEmpty()) {
                        val linkContent = node.textContent?.trim() ?: ""
                        if (linkContent.endsWith(".mp3") || linkContent.endsWith(".m4a")) {
                            audioUrl = linkContent
                        }
                    }
                }
            }
        }

        // Skip items without audio
        if (audioUrl.isEmpty()) return null

        return Episode(
            title = title,
            description = description,
            audioUrl = audioUrl,
            imageUrl = imageUrl,
            duration = duration,
            durationText = durationText,
            publishDate = publishDate,
            publishDateText = formatDisplayDate(publishDate),
            fileSize = fileSize,
            guid = guid ?: audioUrl
        )
    }

    private fun parseDuration(durationStr: String?): Long {
        if (durationStr.isNullOrEmpty()) return 0

        return try {
            // Try parsing as seconds first
            val seconds = durationStr.toLongOrNull()
            if (seconds != null) return seconds

            // Parse HH:MM:SS or MM:SS format
            val parts = durationStr.split(":").map { it.trim().toLongOrNull() ?: 0 }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0

        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time ?: 0
            } catch (e: Exception) {
                // Try next format
            }
        }
        return 0
    }

    private fun formatDisplayDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return try {
            val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            format.format(timestamp)
        } catch (e: Exception) {
            ""
        }
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "") // Remove HTML tags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }
}
