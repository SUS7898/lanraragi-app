package com.sus7898.lrrviewer.data.api

import java.io.IOException

sealed class LrrException(message: String) : IOException(message) {
    class NotConfigured(message: String = "서버가 설정되지 않았습니다.") : LrrException(message)
    class Unauthorized : LrrException("인증 실패 (401)")
    class HttpError(val code: Int, val detail: String?) : LrrException("HTTP $code${detail?.let { ": $it" } ?: ""}")
}

/** Human readable (Korean) message for anything that can go wrong while talking to the server. */
fun Throwable.userMessage(): String = when (this) {
    is LrrException.NotConfigured -> "서버가 설정되지 않았습니다. 설정에서 서버 주소를 입력하세요."
    is LrrException.Unauthorized -> "인증 실패(401): API 키를 확인하세요. No-Fun 모드 서버는 API 키가 필수입니다."
    is LrrException.HttpError -> "서버 오류 (HTTP $code)${detail?.let { ": $it" } ?: ""}"
    is java.net.UnknownHostException -> "서버 주소를 찾을 수 없습니다. ($message)"
    is java.net.ConnectException -> "서버에 연결할 수 없습니다. 주소/포트와 네트워크(같은 Wi-Fi 또는 VPN)를 확인하세요."
    is java.net.SocketTimeoutException -> "서버 응답 시간이 초과되었습니다."
    is javax.net.ssl.SSLException -> "TLS 오류: 자체 서명 인증서라면 기기에 CA 인증서를 설치하거나 http 주소를 사용하세요. ($message)"
    is kotlinx.serialization.SerializationException -> "서버 응답을 해석하지 못했습니다. ($message)"
    is IllegalArgumentException -> message ?: "잘못된 요청입니다."
    else -> message ?: toString()
}
