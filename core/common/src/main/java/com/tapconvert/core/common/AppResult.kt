package com.tapconvert.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val throwable: Throwable, val message: String = throwable.message ?: "Unknown error") : AppResult<Nothing>
    data class Progress(val percentage: Int, val currentStep: String) : AppResult<Nothing>
}
