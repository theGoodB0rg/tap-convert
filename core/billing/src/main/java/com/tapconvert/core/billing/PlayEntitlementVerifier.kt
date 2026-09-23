package com.tapconvert.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.tapconvert.core.ads.SubscriptionPlan
import com.tapconvert.core.ads.SubscriptionTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

/**
 * Serverless Play verifier: BillingClient is source of truth.
 * - Filters PURCHASED only, acknowledges unacked, hashes tokens (never stores raw).
 * - Offline/error/empty -> FREE (fail-closed on Pro, fail-open on Free conversions).
 */
class PlayEntitlementVerifier(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : EntitlementVerifier {

    private val appContext = context.applicationContext
    private val _entitlement = MutableStateFlow(EntitlementSnapshot.FREE)
    override val entitlement: StateFlow<EntitlementSnapshot> = _entitlement.asStateFlow()

    private var billingClient: BillingClient? = null
    private var productDetailsCache: Map<String, QueryProductDetailsParams.Product> = emptyMap()

    private fun client(): BillingClient {
        billingClient?.let { return it }
        val params = PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        val c = BillingClient.newBuilder(appContext)
            .setListener { _, _ -> }
            .enablePendingPurchases(params)
            .build()
        billingClient = c
        return c
    }

    private suspend fun ensureConnected(): Boolean {
        val c = client()
        if (c.isReady) return true
        return suspendCancellableCoroutine { cont ->
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    cont.resume(r.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    override suspend fun refresh(activity: Activity?): EntitlementSnapshot {
        val now = clock()
        if (!ensureConnected()) {
            val s = EntitlementSnapshot(source = EntitlementSource.OFFLINE, queriedAtMs = now)
            _entitlement.value = s
            return s
        }
        return try {
            val c = client()
            val subs = queryPurchases(c, BillingClient.ProductType.SUBS)
            val inapp = queryPurchases(c, BillingClient.ProductType.INAPP)
            val all = (subs + inapp)
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

            // Acknowledge anything Play hasn't acked yet (required within 3 days).
            all.filter { !it.isAcknowledged }.forEach { p ->
                runCatching {
                    suspendCancellableCoroutine { cont ->
                        c.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(p.purchaseToken).build()
                        ) { _ -> if (cont.isActive) cont.resume(Unit) }
                    }
                }
            }

            val tier = resolveTier(all.map { it.products }.flatten())
            val snapshot = if (tier == SubscriptionTier.FREE || all.isEmpty()) {
                EntitlementSnapshot(source = EntitlementSource.PLAY_EMPTY, queriedAtMs = now)
            } else {
                EntitlementSnapshot(
                    isPro = true,
                    tier = tier,
                    purchaseTokensHash = all.map { sha256(it.purchaseToken) }.toSet(),
                    queriedAtMs = now,
                    source = EntitlementSource.PLAY_FRESH
                )
            }
            _entitlement.value = snapshot
            snapshot
        } catch (_: Throwable) {
            val s = EntitlementSnapshot(source = EntitlementSource.ERROR, queriedAtMs = now)
            _entitlement.value = s
            s
        }
    }

    override suspend fun purchase(activity: Activity, plan: SubscriptionPlan): Boolean {
        if (!ensureConnected()) return false
        val c = client()
        val productType = if (plan is SubscriptionPlan.Lifetime) {
            BillingClient.ProductType.INAPP
        } else {
            BillingClient.ProductType.SUBS
        }
        val productId = plan.productId
        val details = queryDetails(c, productType, productId) ?: return false
        val paramsList = when (productType) {
            BillingClient.ProductType.SUBS -> {
                val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return false
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            }
            else -> listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details).build()
            )
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(paramsList).build()
        val result = c.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun clear() {
        _entitlement.value = EntitlementSnapshot.FREE
        runCatching { billingClient?.endConnection() }
        billingClient = null
    }

    private suspend fun queryPurchases(c: BillingClient, productType: String): List<Purchase> {
        return suspendCancellableCoroutine { cont ->
            c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(productType).build()
            ) { result, purchases ->
                if (cont.isActive) {
                    cont.resume(
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases
                        else emptyList()
                    )
                }
            }
        }
    }

    private suspend fun queryDetails(
        c: BillingClient,
        productType: String,
        productId: String
    ): com.android.billingclient.api.ProductDetails? {
        return suspendCancellableCoroutine { cont ->
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId).setProductType(productType).build()
                )
            ).build()
            c.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(details.productDetailsList.firstOrNull())
                } else {
                    cont.resume(null)
                }
            }
        }
    }

    internal fun resolveTier(productIds: List<String>): SubscriptionTier = when {
        ProductCatalog.LIFETIME_ID in productIds -> SubscriptionTier.PRO_LIFETIME
        ProductCatalog.ANNUAL_ID in productIds -> SubscriptionTier.PRO_ANNUAL
        ProductCatalog.MONTHLY_ID in productIds -> SubscriptionTier.PRO_MONTHLY
        else -> SubscriptionTier.FREE
    }

    internal fun sha256(raw: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
