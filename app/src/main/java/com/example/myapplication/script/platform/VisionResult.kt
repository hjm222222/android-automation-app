package com.example.myapplication.script.platform

/** 视觉能力的可判断结果，取消异常不应被转换为此类型。 */
sealed interface VisionResult<out T> {
    data class Success<T>(val value: T) : VisionResult<T>

    data object NotFound : VisionResult<Nothing>

    data object Timeout : VisionResult<Nothing>

    data object PermissionDenied : VisionResult<Nothing>

    data class Failed(
        val message: String,
        val cause: Throwable? = null
    ) : VisionResult<Nothing>
}
