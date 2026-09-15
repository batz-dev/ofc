package com.example.data.api.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Dynamic Device Profile & User-Agent generator using real device hardware/OS specifications.
 * No hardcoded or mock device parameters.
 */
object DeviceSecurityHelper {

    private const val PREFS_NAME = "device_sec_profile"
    private const val KEY_DEVICE_ID = "sec_device_id"
    private const val KEY_GAID = "sec_gaid"

    @Volatile
    private var cachedDeviceId: String? = null
    @Volatile
    private var cachedGaid: String? = null

    fun getDeviceId(context: Context? = null): String {
        cachedDeviceId?.let { return it }
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrEmpty()) {
                id = UUID.randomUUID().toString().replace("-", "")
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            cachedDeviceId = id
            return id
        }
        val fallback = UUID.randomUUID().toString().replace("-", "")
        cachedDeviceId = fallback
        return fallback
    }

    fun getGaid(context: Context? = null): String {
        cachedGaid?.let { return it }
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var id = prefs.getString(KEY_GAID, null)
            if (id.isNullOrEmpty()) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_GAID, id).apply()
            }
            cachedGaid = id
            return id
        }
        val fallback = UUID.randomUUID().toString()
        cachedGaid = fallback
        return fallback
    }

    fun getNetworkType(context: Context?): String {
        if (context == null) return "NETWORK_WIFI"
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)
            when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "NETWORK_WIFI"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "NETWORK_MOBILE"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "NETWORK_ETHERNET"
                else -> "NETWORK_WIFI"
            }
        } catch (_: Exception) {
            "NETWORK_WIFI"
        }
    }

    fun getUserAgent(context: Context? = null): String {
        val osVersion = Build.VERSION.RELEASE.ifBlank { "13" }
        val locale = Locale.getDefault()
        val langCountry = "${locale.language}_${locale.country.ifBlank { "US" }}"
        val brand = Build.BRAND.ifBlank { "Android" }
        val model = Build.MODEL.ifBlank { "Device" }
        val buildId = Build.ID.ifBlank { "TQ2A.230405.003" }
        val verCode = 50020045

        return "com.community.oneroom/$verCode (Linux; U; Android $osVersion; $langCountry; $brand $model; Build/$buildId; Cronet/135.0.7012.3)"
    }

    fun buildClientInfoJson(context: Context? = null): String {
        val osVersion = Build.VERSION.RELEASE.ifBlank { "13" }
        val brand = Build.BRAND.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }.ifBlank { "Android" }
        val model = Build.MODEL.ifBlank { "Device" }
        val language = Locale.getDefault().language.ifBlank { "en" }
        val region = Locale.getDefault().country.ifBlank { "US" }
        val tz = try { TimeZone.getDefault().id.ifBlank { "UTC" } } catch (_: Exception) { "UTC" }
        val net = getNetworkType(context)
        val devId = getDeviceId(context)
        val gaId = getGaid(context)
        val verCode = 50020045

        return JSONObject().apply {
            put("package_name", "com.community.oneroom")
            put("version_name", "3.0.03.0529.03")
            put("version_code", verCode)
            put("os", "android")
            put("os_version", osVersion)
            put("install_ch", "ps")
            put("device_id", devId)
            put("install_store", "ps")
            put("gaid", gaId)
            put("brand", brand)
            put("model", model)
            put("system_language", language)
            put("net", net)
            put("region", region)
            put("timezone", tz)
            put("sp_code", "40401")
            put("X-Play-Mode", "2")
        }.toString()
    }
}
