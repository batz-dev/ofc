package com.example.data.api

import android.content.Context
import android.util.Base64
import com.example.data.api.security.DeviceSecurityHelper
import com.example.data.api.security.SecureKeyStore
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MovieSigner {
    val PRIMARY_BASE_URL: String get() = SecureKeyStore.PRIMARY_BASE_URL
    val FALLBACK_URLS: List<String> get() = listOf(SecureKeyStore.FALLBACK_URL_1, SecureKeyStore.FALLBACK_URL_2)

    private val SECRET_KEY_DEFAULT: String get() = SecureKeyStore.DEFAULT_SECRET_KEY

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
        contentType: String = "application/json",
        context: Context? = null
    ): Map<String, String> {
        val ts = System.currentTimeMillis()
        val accept = "application/json"

        val headers = mutableMapOf(
            SecureKeyStore.HDR_USER_AGENT to DeviceSecurityHelper.getUserAgent(context),
            SecureKeyStore.HDR_ACCEPT to accept,
            SecureKeyStore.HDR_CONTENT_TYPE to contentType,
            SecureKeyStore.HDR_CONNECTION to "keep-alive",
            SecureKeyStore.HDR_CLIENT_TOKEN to generateXClientToken(ts),
            SecureKeyStore.HDR_TR_SIGNATURE to generateXTrSignature(method, accept, contentType, fullUrl, body, ts),
            SecureKeyStore.HDR_CLIENT_INFO to DeviceSecurityHelper.buildClientInfoJson(context),
            SecureKeyStore.HDR_CLIENT_STATUS to "0",
            SecureKeyStore.HDR_PLAY_MODE to "2"
        )

        if (!authToken.isNullOrEmpty()) {
            headers[SecureKeyStore.HDR_AUTHORIZATION] = "Bearer $authToken"
        }

        return headers
    }
}
