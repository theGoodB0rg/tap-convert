package com.tapconvert.core.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

interface InAppReviewLauncher {
    suspend fun launchReview(activity: Activity?): Boolean
}

class FakeInAppReviewLauncher(
    var launchResult: Boolean = true
) : InAppReviewLauncher {
    var launchCount: Int = 0
        private set

    override suspend fun launchReview(activity: Activity?): Boolean {
        launchCount++
        return launchResult
    }
}

class PlayStoreFallbackReviewLauncher(
    private val context: Context
) : InAppReviewLauncher {

    override suspend fun launchReview(activity: Activity?): Boolean {
        val packageName = context.packageName
        return try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (activity != null) {
                activity.startActivity(marketIntent)
            } else {
                context.startActivity(marketIntent)
            }
            true
        } catch (_: Throwable) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (activity != null) {
                    activity.startActivity(webIntent)
                } else {
                    context.startActivity(webIntent)
                }
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
