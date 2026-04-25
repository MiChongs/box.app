package com.box.app.data.model

sealed class LatencyResult {
    data object Loading : LatencyResult()

    /** 测量成功 */
    data class Success(val latencyMs: Long) : LatencyResult()

    /** 连接超时 */
    data object Timeout : LatencyResult()

    /** DNS 解析失败 */
    data object DnsError : LatencyResult()

    /** 连接被拒绝或网络不可达 */
    data object Unreachable : LatencyResult()

    /** HTTP 错误（4xx/5xx）*/
    data class HttpError(val code: Int) : LatencyResult()

    /** 未知错误 */
    data object NotAvailable : LatencyResult()
}
