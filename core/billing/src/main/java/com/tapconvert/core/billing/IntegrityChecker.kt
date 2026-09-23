package com.tapconvert.core.billing

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Lightweight Play Integrity gate for high-value Pro ops.
 * Serverless v1: we check device verdict client-side and soft-degrade (not brick).
 * Uses the classic token API (deprecated in 1.3.0, still served); v2 migrates to
 * Standard Integrity + server-side decryption via a Cloud Function.
 */
class IntegrityChecker(private val context: Context) {

    data class Verdict(val meetsDevice: Boolean, val raw: String = "")

    @Suppress("DEPRECATION")
    suspend fun check(nonce: String = "tapconvert-pro-gate"): Verdict {
        return try {
            val manager = IntegrityManagerFactory.create(context)
            val request = IntegrityTokenRequest.builder().setNonce(nonce).build()
            val token = suspendCancellableCoroutine { cont ->
                manager.requestIntegrityToken(request)
                    .addOnSuccessListener { resp ->
                        if (cont.isActive) cont.resume(resp.token())
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume("")
                    }
            }
            // v1: token presence = Play-backed device. Decode server-side in v2.
            Verdict(meetsDevice = token.isNotBlank(), raw = "")
        } catch (_: Throwable) {
            Verdict(meetsDevice = false)
        }
    }
}
