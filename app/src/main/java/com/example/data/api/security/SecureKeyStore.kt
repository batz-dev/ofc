package com.example.data.api.security

/**
 * Multi-layer runtime obfuscated keystore to prevent plain-text extraction from DEX/smali/strings.
 */
object SecureKeyStore {
    private val MASK_KEY = byteArrayOf(0x5A.toByte(), 0xE3.toByte(), 0x8F.toByte(), 0x12.toByte(), 0xC7.toByte(), 0x39.toByte(), 0xAA.toByte(), 0x4D.toByte())

    private fun decode(cipher: ByteArray): String {
        val out = ByteArray(cipher.size)
        for (i in cipher.indices) {
            val k = MASK_KEY[i % MASK_KEY.size].toInt()
            val step2 = cipher[i].toInt() xor k
            val step1 = ((step2 ushr 3) and 0x1F) or ((step2 shl 5) and 0xFF)
            out[i] = (step1 xor ((i * 17 + 0x4B) and 0xFF)).toByte()
        }
        return String(out, Charsets.UTF_8)
    }

    private val ENC_PRIMARY_BASE_URL = byteArrayOf(0x43.toByte(), 0xA2.toByte(), 0x47.toByte(), 0x62.toByte(), 0x20.toByte(), 0xED.toByte(), 0x5E.toByte(), 0x22.toByte(), 0xCF.toByte(), 0x47.toByte(), 0x6B.toByte(), 0x93.toByte(), 0x0E.toByte(), 0x73.toByte(), 0x18.toByte(), 0x6C.toByte(), 0xAB.toByte(), 0x13.toByte(), 0x1F.toByte(), 0x1D.toByte(), 0x50.toByte(), 0xCD.toByte(), 0xBF.toByte(), 0xA0.toByte(), 0x2E.toByte())
    val PRIMARY_BASE_URL: String by lazy { decode(ENC_PRIMARY_BASE_URL) }

    private val ENC_FALLBACK_URL_1 = byteArrayOf(0x43.toByte(), 0xA2.toByte(), 0x47.toByte(), 0x62.toByte(), 0x20.toByte(), 0xED.toByte(), 0x5E.toByte(), 0x22.toByte(), 0xCF.toByte(), 0x47.toByte(), 0x6B.toByte(), 0x8B.toByte(), 0x0E.toByte(), 0x73.toByte(), 0x18.toByte(), 0x6C.toByte(), 0xAB.toByte(), 0x13.toByte(), 0x1F.toByte(), 0x1D.toByte(), 0x50.toByte(), 0xCD.toByte(), 0xBF.toByte(), 0xA0.toByte(), 0x2E.toByte())
    val FALLBACK_URL_1: String by lazy { decode(ENC_FALLBACK_URL_1) }

    private val ENC_FALLBACK_URL_2 = byteArrayOf(0x43.toByte(), 0xA2.toByte(), 0x47.toByte(), 0x62.toByte(), 0x20.toByte(), 0xED.toByte(), 0x5E.toByte(), 0x22.toByte(), 0xCF.toByte(), 0x47.toByte(), 0x6B.toByte(), 0x83.toByte(), 0x0E.toByte(), 0x73.toByte(), 0x18.toByte(), 0x6C.toByte(), 0xAB.toByte(), 0x13.toByte(), 0x1F.toByte(), 0x1D.toByte(), 0x50.toByte(), 0xCD.toByte(), 0xBF.toByte(), 0xA0.toByte(), 0x2E.toByte())
    val FALLBACK_URL_2: String by lazy { decode(ENC_FALLBACK_URL_2) }

    private val ENC_DEFAULT_SECRET_KEY = byteArrayOf(0xB9.toByte(), 0xB0.toByte(), 0xAF.toByte(), 0x73.toByte(), 0xD8.toByte(), 0xBD.toByte(), 0x9E.toByte(), 0xC0.toByte(), 0x45.toByte(), 0x07.toByte(), 0xBA.toByte(), 0x50.toByte(), 0xB6.toByte(), 0x2B.toByte(), 0xE8.toByte(), 0x74.toByte(), 0xAA.toByte(), 0x3A.toByte(), 0x6E.toByte(), 0xC5.toByte(), 0x82.toByte(), 0x75.toByte(), 0x8E.toByte(), 0xC9.toByte(), 0x17.toByte(), 0x96.toByte(), 0x0C.toByte(), 0xC1.toByte(), 0xB4.toByte(), 0x72.toByte(), 0xAA.toByte(), 0x04.toByte(), 0xB3.toByte(), 0xB1.toByte(), 0x42.toByte(), 0xD4.toByte(), 0x68.toByte(), 0xAC.toByte(), 0xB5.toByte(), 0x20.toByte())
    val DEFAULT_SECRET_KEY: String by lazy { decode(ENC_DEFAULT_SECRET_KEY) }

    private val ENC_PATH_TAB_OPERATING = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xC5.toByte(), 0x30.toByte(), 0xAF.toByte(), 0xCD.toByte(), 0xA0.toByte(), 0xC6.toByte(), 0x6F.toByte(), 0x34.toByte(), 0xA9.toByte(), 0x5D.toByte(), 0xB3.toByte(), 0x93.toByte(), 0xA4.toByte())
    val PATH_TAB_OPERATING: String by lazy { decode(ENC_PATH_TAB_OPERATING) }

    private val ENC_PATH_SEARCH = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xAF.toByte(), 0xF7.toByte(), 0xF0.toByte(), 0x5E.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0x7D.toByte(), 0xB3.toByte(), 0x99.toByte(), 0x04.toByte(), 0x2A.toByte(), 0x0B.toByte(), 0x70.toByte(), 0xFD.toByte(), 0xF9.toByte())
    val PATH_SEARCH: String by lazy { decode(ENC_PATH_SEARCH) }

    private val ENC_PATH_SEARCH_SUGGEST = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xAF.toByte(), 0xF7.toByte(), 0xF0.toByte(), 0x5E.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0x7D.toByte(), 0xB3.toByte(), 0x99.toByte(), 0x04.toByte(), 0x2A.toByte(), 0x0B.toByte(), 0x70.toByte(), 0xFD.toByte(), 0xF9.toByte(), 0x56.toByte(), 0xBF.toByte(), 0xF1.toByte(), 0xFE.toByte(), 0xF8.toByte(), 0x0C.toByte(), 0xB8.toByte(), 0xDD.toByte())
    val PATH_SEARCH_SUGGEST: String by lazy { decode(ENC_PATH_SEARCH_SUGGEST) }

    private val ENC_PATH_SUBJECT_DETAIL = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xAF.toByte(), 0xF7.toByte(), 0xF0.toByte(), 0x5E.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0x7D.toByte(), 0xB3.toByte(), 0x99.toByte(), 0xBC.toByte(), 0x2A.toByte(), 0xA3.toByte(), 0xE8.toByte(), 0xAD.toByte(), 0xD9.toByte())
    val PATH_SUBJECT_DETAIL: String by lazy { decode(ENC_PATH_SUBJECT_DETAIL) }

    private val ENC_PATH_SUBJECT_GET = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xAF.toByte(), 0xF7.toByte(), 0xF0.toByte(), 0x5E.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0x7D.toByte(), 0xB3.toByte(), 0x99.toByte(), 0xA4.toByte(), 0x2A.toByte(), 0xA3.toByte())
    val PATH_SUBJECT_GET: String by lazy { decode(ENC_PATH_SUBJECT_GET) }

    private val ENC_PATH_SEASON_INFO = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xAF.toByte(), 0xF7.toByte(), 0xF0.toByte(), 0x5E.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0x7D.toByte(), 0xB3.toByte(), 0x99.toByte(), 0x04.toByte(), 0x2A.toByte(), 0x0B.toByte(), 0x78.toByte(), 0x9D.toByte(), 0xC9.toByte(), 0x56.toByte(), 0x6F.toByte(), 0x29.toByte(), 0xF6.toByte(), 0xB8.toByte())
    val PATH_SEASON_INFO: String by lazy { decode(ENC_PATH_SEASON_INFO) }

    private val ENC_PATH_PLAY_INFO = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xAF.toByte(), 0xF7.toByte(), 0xF0.toByte(), 0x5E.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0x7D.toByte(), 0xB3.toByte(), 0x99.toByte(), 0x1C.toByte(), 0x62.toByte(), 0x0B.toByte(), 0x28.toByte(), 0x8F.toByte(), 0xF1.toByte(), 0x4C.toByte(), 0x17.toByte(), 0x21.toByte())
    val PATH_PLAY_INFO: String by lazy { decode(ENC_PATH_PLAY_INFO) }

    private val ENC_PATH_PLAY_RELATED_REC = byteArrayOf(0x79.toByte(), 0xBA.toByte(), 0xCF.toByte(), 0xD2.toByte(), 0x90.toByte(), 0x17.toByte(), 0x04.toByte(), 0x32.toByte(), 0xAF.toByte(), 0xBF.toByte(), 0x33.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x53.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0xB3.toByte(), 0xB3.toByte(), 0x1D.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xAF.toByte(), 0xF7.toByte(), 0xF0.toByte(), 0x5E.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0x7D.toByte(), 0xB3.toByte(), 0x99.toByte(), 0x1C.toByte(), 0x62.toByte(), 0x0B.toByte(), 0x28.toByte(), 0x8F.toByte(), 0x29.toByte(), 0x14.toByte(), 0x47.toByte(), 0x51.toByte(), 0x66.toByte(), 0xE8.toByte(), 0x04.toByte(), 0x4A.toByte(), 0xED.toByte(), 0x50.toByte(), 0x7B.toByte())
    val PATH_PLAY_RELATED_REC: String by lazy { decode(ENC_PATH_PLAY_RELATED_REC) }

    private val ENC_HDR_USER_AGENT = byteArrayOf(0xAA.toByte(), 0x9A.toByte(), 0xCF.toByte(), 0x72.toByte(), 0xD2.toByte(), 0x36.toByte(), 0x1C.toByte(), 0x70.toByte(), 0xB7.toByte(), 0x67.toByte())
    val HDR_USER_AGENT: String by lazy { decode(ENC_HDR_USER_AGENT) }

    private val ENC_HDR_ACCEPT = byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0xFF.toByte(), 0xCA.toByte(), 0x38.toByte(), 0x9F.toByte())
    val HDR_ACCEPT: String by lazy { decode(ENC_HDR_ACCEPT) }

    private val ENC_HDR_CONTENT_TYPE = byteArrayOf(0x1A.toByte(), 0x7A.toByte(), 0x97.toByte(), 0x42.toByte(), 0x90.toByte(), 0x4F.toByte(), 0x84.toByte(), 0x32.toByte(), 0x66.toByte(), 0x0F.toByte(), 0xA3.toByte(), 0x09.toByte())
    val HDR_CONTENT_TYPE: String by lazy { decode(ENC_HDR_CONTENT_TYPE) }

    private val ENC_HDR_CONNECTION = byteArrayOf(0x1A.toByte(), 0x7A.toByte(), 0x97.toByte(), 0x92.toByte(), 0x90.toByte(), 0x27.toByte(), 0x84.toByte(), 0x10.toByte(), 0xBF.toByte(), 0xB7.toByte())
    val HDR_CONNECTION: String by lazy { decode(ENC_HDR_CONNECTION) }

    private val ENC_HDR_CLIENT_TOKEN = byteArrayOf(0xC2.toByte(), 0x68.toByte(), 0xFE.toByte(), 0x82.toByte(), 0xF0.toByte(), 0x17.toByte(), 0x54.toByte(), 0xF8.toByte(), 0xAD.toByte(), 0x66.toByte(), 0x5B.toByte(), 0x79.toByte(), 0x54.toByte(), 0x0B.toByte())
    val HDR_CLIENT_TOKEN: String by lazy { decode(ENC_HDR_CLIENT_TOKEN) }

    private val ENC_HDR_TR_SIGNATURE = byteArrayOf(0xC3.toByte(), 0x68.toByte(), 0x47.toByte(), 0x72.toByte(), 0xD2.toByte(), 0xA7.toByte(), 0x6C.toByte(), 0x60.toByte(), 0xB7.toByte(), 0xCF.toByte(), 0x83.toByte(), 0x89.toByte(), 0xEC.toByte(), 0x53.toByte())
    val HDR_TR_SIGNATURE: String by lazy { decode(ENC_HDR_TR_SIGNATURE) }

    private val ENC_HDR_CLIENT_INFO = byteArrayOf(0xC2.toByte(), 0x68.toByte(), 0xFE.toByte(), 0x82.toByte(), 0xF0.toByte(), 0x17.toByte(), 0x54.toByte(), 0xF8.toByte(), 0xAD.toByte(), 0x8E.toByte(), 0x53.toByte(), 0x11.toByte(), 0x04.toByte())
    val HDR_CLIENT_INFO: String by lazy { decode(ENC_HDR_CLIENT_INFO) }

    private val ENC_HDR_CLIENT_STATUS = byteArrayOf(0xC2.toByte(), 0x68.toByte(), 0xFE.toByte(), 0x82.toByte(), 0xF0.toByte(), 0x17.toByte(), 0x54.toByte(), 0xF8.toByte(), 0xAD.toByte(), 0x5E.toByte(), 0x83.toByte(), 0x29.toByte(), 0xDC.toByte(), 0xD3.toByte(), 0xF8.toByte())
    val HDR_CLIENT_STATUS: String by lazy { decode(ENC_HDR_CLIENT_STATUS) }

    private val ENC_HDR_PLAY_MODE = byteArrayOf(0xC2.toByte(), 0x68.toByte(), 0x66.toByte(), 0x82.toByte(), 0xB0.toByte(), 0xF7.toByte(), 0x4E.toByte(), 0x31.toByte(), 0xBF.toByte(), 0xE7.toByte(), 0x0B.toByte())
    val HDR_PLAY_MODE: String by lazy { decode(ENC_HDR_PLAY_MODE) }

    private val ENC_HDR_AUTHORIZATION = byteArrayOf(0x0A.toByte(), 0xAA.toByte(), 0x47.toByte(), 0xA2.toByte(), 0xC0.toByte(), 0xAF.toByte(), 0x6C.toByte(), 0x88.toByte(), 0xCF.toByte(), 0x67.toByte(), 0x6B.toByte(), 0x59.toByte(), 0x0C.toByte())
    val HDR_AUTHORIZATION: String by lazy { decode(ENC_HDR_AUTHORIZATION) }

    private val ENC_HDR_X_USER = byteArrayOf(0xC3.toByte(), 0x68.toByte(), 0x4F.toByte(), 0x7A.toByte(), 0x90.toByte(), 0xAF.toByte())
    val HDR_X_USER: String by lazy { decode(ENC_HDR_X_USER) }

    private val ENC_CLOUDFRONT_POLICY_KEY = byteArrayOf(0x1A.toByte(), 0x62.toByte(), 0x9F.toByte(), 0x4A.toByte(), 0x98.toByte(), 0x0E.toByte(), 0xB4.toByte(), 0x20.toByte(), 0xB7.toByte(), 0x67.toByte(), 0x49.toByte(), 0xA0.toByte(), 0x04.toByte(), 0x1B.toByte(), 0x28.toByte(), 0x04.toByte(), 0x4B.toByte(), 0x69.toByte())
    val CLOUDFRONT_POLICY_KEY: String by lazy { decode(ENC_CLOUDFRONT_POLICY_KEY) }

    private val ENC_DUMMY_VIDEO_MD5 = byteArrayOf(0xC9.toByte(), 0x0A.toByte(), 0x65.toByte(), 0x40.toByte(), 0x0A.toByte(), 0xB5.toByte(), 0x34.toByte(), 0x40.toByte(), 0x5D.toByte(), 0x0D.toByte(), 0x03.toByte(), 0x29.toByte(), 0xE6.toByte(), 0xB9.toByte(), 0xAA.toByte(), 0xAE.toByte(), 0x31.toByte(), 0x19.toByte(), 0x77.toByte(), 0x55.toByte(), 0xA2.toByte(), 0x9F.toByte(), 0xB7.toByte(), 0xC8.toByte(), 0xCC.toByte(), 0xFD.toByte(), 0x26.toByte(), 0x6B.toByte(), 0x77.toByte(), 0xDB.toByte(), 0x79.toByte(), 0xAC.toByte())
    val DUMMY_VIDEO_MD5: String by lazy { decode(ENC_DUMMY_VIDEO_MD5) }

    private val ENC_DUMMY_VIDEO_PATH = byteArrayOf(0x79.toByte(), 0x7A.toByte(), 0x47.toByte(), 0xA2.toByte(), 0x90.toByte(), 0xAF.toByte(), 0x5E.toByte(), 0xCA.toByte(), 0x45.toByte(), 0x55.toByte(), 0x91.toByte(), 0x5B.toByte(), 0xFE.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0x9E.toByte(), 0x09.toByte(), 0xF9.toByte())
    val DUMMY_VIDEO_PATH: String by lazy { decode(ENC_DUMMY_VIDEO_PATH) }

}
