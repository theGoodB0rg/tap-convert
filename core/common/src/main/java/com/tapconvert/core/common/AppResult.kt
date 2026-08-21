package com.tapconvert.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(
        val throwable: Throwable,
        val message: String = throwable.message ?: "Unknown error"
    ) : AppResult<Nothing>
    data class Progress(
        val percentage: Int,
        val currentStep: String
    ) : AppResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    val isProgress: Boolean
        get() = this is Progress
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
    is AppResult.Progress -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Error -> this
    is AppResult.Progress -> this
}

fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> data
    else -> null
}

inline fun <T> AppResult<T>.getOrElse(default: (AppResult<T>) -> T): T = when (this) {
    is AppResult.Success -> data
    else -> default(this)
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (AppResult.Error) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(this)
    return this
}

inline fun <T> AppResult<T>.onProgress(action: (Int, String) -> Unit): AppResult<T> {
    if (this is AppResult.Progress) action(percentage, currentStep)
    return this
}

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onError: (AppResult.Error) -> R,
    onProgress: (Int, String) -> R
): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    is AppResult.Error -> onError(this)
    is AppResult.Progress -> onProgress(percentage, currentStep)
}

