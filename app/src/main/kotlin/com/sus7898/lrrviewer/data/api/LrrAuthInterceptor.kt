package com.sus7898.lrrviewer.data.api

import com.sus7898.lrrviewer.data.AppSettings
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Base64

/**
 * Adds `Authorization: Bearer base64(apiKey)` to requests that target the configured
 * LANraragi server (and only those, so the key never leaks to GitHub or anywhere else),
 * and enforces the user's cleartext (plain HTTP) policy.
 */
class LrrAuthInterceptor(private val settings: StateFlow<AppSettings>) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val s = settings.value

        if (!s.allowCleartext && request.url.scheme.equals("http", ignoreCase = true)) {
            throw IOException("평문 HTTP 연결이 설정에서 차단되어 있습니다: ${request.url.host}")
        }

        val base = LrrUrls.parseBase(s.serverUrl)
        if (base == null || s.apiKey.isBlank() || !LrrUrls.isSameServer(base, request.url)) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", bearerValue(s.apiKey))
                .build(),
        )
    }

    companion object {
        fun bearerValue(apiKey: String): String =
            "Bearer " + Base64.getEncoder().encodeToString(apiKey.toByteArray(Charsets.UTF_8))
    }
}
