package com.example.data.api

import android.net.Uri
import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

object MovieSigner {
    const val PRIMARY_BASE_URL = "https://api6.aoneroom.com"
    val FALLBACK_URLS = listOf("https://api5.aoneroom.com", "https://api4.aoneroom.com")

    private const val SECRET_KEY_DEFAULT = "76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O"
    const val VERSION_CODE = 50020045

    val ANDROID_USER_AGENT =
        "com.community.oneroom/$VERSION_CODE (Linux; U; Android 13; en_US; 22101316G; Build/TQ2A.230405.003; Cronet/135.0.7012.3)"

    private val deviceId = UUID.randomUUID().toString().replace("-", "")
    private val gaid = UUID.randomUUID().toString()

    val clientInfoJson: String by lazy {
        JSONObject().apply {
            put("package_name", "com.community.oneroom")
            put("version_name", "3.0.03.0529.03")
            put("version_code", VERSION_CODE)
            put("os", "android")
            put("os_version", "13")
            put("install_ch", "ps")
            put("device_id", deviceId)
            put("install_store", "ps")
            put("gaid", gaid)
            put("brand", "Redmi")
            put("model", "22101316G")
            put("system_language", "en")
            put("net", "NETWORK_WIFI")
            put("region", "US")
            put("timezone", "America/New_York")
            put("sp_code", "40401")
            put("X-Play-Mode", "2")
        }.toString()
    }

    fun md5Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generateXClientToken(ts: Long): String {
        val reversedTs = ts.toString().reversed()
        val h = md5Hex(reversedTs.toByteArray(Charsets.UTF_8))
        return "$ts,$h"
    }

    fun sortedQueryString(urlStr: String): String {
        val query = try {
            val qIdx = urlStr.indexOf('?')
            if (qIdx != -1) urlStr.substring(qIdx + 1) else null
        } catch (e: Exception) {
            null
        } ?: return ""
        val pairs = query.split("&").filter { it.isNotEmpty() }.map { param ->
            val idx = param.indexOf('=')
            if (idx >= 0) {
                val key = try { URLDecoder.decode(param.substring(0, idx), "UTF-8") } catch (e: Exception) { param.substring(0, idx) }
                val value = try { URLDecoder.decode(param.substring(idx + 1), "UTF-8") } catch (e: Exception) { param.substring(idx + 1) }
                key to value
            } else {
                val key = try { URLDecoder.decode(param, "UTF-8") } catch (e: Exception) { param }
                key to ""
            }
        }.sortedBy { it.first }
        return pairs.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
    }

    fun generateXTrSignature(
        method: String,
        accept: String,
        contentType: String,
        fullUrl: String,
        body: String?,
        ts: Long
    ): String {
        val path = try {
            val withoutProto = if (fullUrl.contains("://")) fullUrl.substringAfter("://") else fullUrl
            val firstSlash = withoutProto.indexOf('/')
            if (firstSlash != -1) {
                val p = withoutProto.substring(firstSlash)
                val qIdx = p.indexOf('?')
                if (qIdx != -1) p.substring(0, qIdx) else p
            } else "/"
        } catch (e: Exception) {
            "/"
        }
        val sortedQuery = sortedQueryString(fullUrl)
        val canonicalUrl = if (sortedQuery.isNotEmpty()) "$path?$sortedQuery" else path

        var bodyHash = ""
        var bodyLength = ""
        if (!body.isNullOrEmpty()) {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val chunkLen = if (bodyBytes.size > 4096) 4096 else bodyBytes.size
            val chunk = bodyBytes.copyOfRange(0, chunkLen)
            bodyHash = md5Hex(chunk)
            bodyLength = bodyBytes.size.toString()
        }

        val canonicalParts = listOf(
            method.uppercase(),
            accept,
            contentType,
            bodyLength,
            ts.toString(),
            bodyHash,
            canonicalUrl
        )
        val canonicalString = canonicalParts.joinToString("\n")

        val secretBytes = Base64.decode(SECRET_KEY_DEFAULT, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signedBytes = mac.doFinal(canonicalString.toByteArray(Charsets.UTF_8))
        val base64Signature = Base64.encodeToString(signedBytes, Base64.NO_WRAP)

        return "$ts|2|$base64Signature"
    }

    fun buildHeaders(
        method: String,
        fullUrl: String,
        body: String? = null,
        authToken: String? = null,
        contentType: String = "application/json"
    ): Map<String, String> {
        val ts = System.currentTimeMillis()
        val accept = "application/json"

        val headers = mutableMapOf(
            "User-Agent" to ANDROID_USER_AGENT,
            "Accept" to accept,
            "Content-Type" to contentType,
            "Connection" to "keep-alive",
            "X-Client-Token" to generateXClientToken(ts),
            "x-tr-signature" to generateXTrSignature(method, accept, contentType, fullUrl, body, ts),
            "X-Client-Info" to clientInfoJson,
            "X-Client-Status" to "0",
            "X-Play-Mode" to "2"
        )

        if (!authToken.isNullOrEmpty()) {
            headers["Authorization"] = "Bearer $authToken"
        }

        return headers
    }
}
