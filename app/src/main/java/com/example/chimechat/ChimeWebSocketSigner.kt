package com.example.chimechat

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs Chime SDK Messaging WebSocket connect URLs with AWS Signature Version 4.
 * See: https://docs.aws.amazon.com/chime-sdk/latest/dg/websockets.html
 */
object ChimeWebSocketSigner {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"

    /** RFC 3986-style encoding for SigV4 (space as %20, not +). */
    private fun encodeParam(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private const val SIGNED_HEADERS = "host"
    private const val EMPTY_PAYLOAD_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // SHA256("")

    fun signConnectUrl(
        host: String,
        userArn: String,
        sessionId: String,
        credentials: Credentials,
        region: String,
        expiresSeconds: Int = 60
    ): String {
        val now = Instant.now().atZone(ZoneOffset.UTC)
        val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val credentialScope = "$dateStamp/$region/chime/aws4_request"

        val params = mutableMapOf<String, MutableList<String>>(
            "X-Amz-Algorithm" to mutableListOf(ALGORITHM),
            "X-Amz-Credential" to mutableListOf(encodeParam("${credentials.accessKeyId}/$credentialScope")),
            "X-Amz-Date" to mutableListOf(amzDate),
            "X-Amz-Expires" to mutableListOf(expiresSeconds.toString()),
            "X-Amz-SignedHeaders" to mutableListOf(SIGNED_HEADERS),
            "sessionId" to mutableListOf(encodeParam(sessionId)),
            "userArn" to mutableListOf(encodeParam(userArn))
        )

        val canonicalQueryString = params.toSortedMap().flatMap { (k, values) ->
            values.sorted().map { v -> "$k=$v" }
        }.joinToString("&")

        val canonicalHeaders = "host:${host.lowercase()}\n"
        val canonicalRequest = listOf(
            "GET",
            "/connect",
            canonicalQueryString,
            canonicalHeaders,
            SIGNED_HEADERS,
            EMPTY_PAYLOAD_HASH
        ).joinToString("\n")

        val hashedCanonicalRequest = sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            hashedCanonicalRequest
        ).joinToString("\n")

        val signingKey = getSignatureKey(
            credentials.secretAccessKey,
            dateStamp,
            region,
            "chime"
        )
        val signature = hmacSha256Hex(signingKey, stringToSign.toByteArray(StandardCharsets.UTF_8))

        val finalQuery = "$canonicalQueryString&X-Amz-Signature=${signature.lowercase()}"
        return "wss://$host/connect?$finalQuery"
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(data)
        return bytesToHex(md.digest())
    }

    private fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return bytesToHex(mac.doFinal(data))
    }

    private fun getSignatureKey(
        key: String,
        dateStamp: String,
        region: String,
        service: String
    ): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
