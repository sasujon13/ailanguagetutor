package com.cheradip.ailanguagetutor.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BillingUnavailableException(message: String) : Exception(message)

class PurchaseCancelledException : Exception("Purchase cancelled")

@Singleton
class PlayBillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectMutex = Mutex()
    private var pendingPurchase: CompletableDeferred<Result<Purchase>>? = null
    private var productCache: Map<String, ProductDetails> = emptyMap()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val deferred = pendingPurchase ?: return@PurchasesUpdatedListener
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (purchase != null) {
                    deferred.complete(Result.success(purchase))
                } else {
                    deferred.complete(Result.failure(BillingUnavailableException("No completed purchase")))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                deferred.complete(Result.failure(PurchaseCancelledException()))
            else ->
                deferred.complete(
                    Result.failure(
                        BillingUnavailableException(billingResult.debugMessage ?: "Billing error ${billingResult.responseCode}"),
                    ),
                )
        }
        pendingPurchase = null
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    suspend fun connect(): Boolean = connectMutex.withLock {
        if (billingClient.isReady) return true
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                billingClient.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() = Unit
                    },
                )
            }
        }
    }

    suspend fun refreshProductDetails(): Result<Map<String, ProductDetails>> {
        if (!connect()) {
            return Result.failure(BillingUnavailableException("Google Play Billing unavailable"))
        }
        return withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                val products = PlayProductIds.allSubscriptionIds().map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(products)
                    .build()
                billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        cont.resume(
                            Result.failure(
                                BillingUnavailableException(result.debugMessage ?: "Product query failed"),
                            ),
                        )
                        return@queryProductDetailsAsync
                    }
                    productCache = productDetailsList.orEmpty().associateBy { it.productId }
                    cont.resume(Result.success(productCache))
                }
            }
        }
    }

    fun formattedPrice(productId: String): String? =
        productCache[productId]?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice

    suspend fun launchSubscription(activity: Activity, productId: String): Result<Purchase> {
        if (!connect()) {
            return Result.failure(BillingUnavailableException("Google Play Billing unavailable"))
        }
        if (productCache.isEmpty()) {
            refreshProductDetails().onFailure { return Result.failure(it) }
        }
        val details = productCache[productId]
            ?: return Result.failure(BillingUnavailableException("Product $productId not found in Play Console"))
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return Result.failure(BillingUnavailableException("No subscription offer for $productId"))

        val deferred = CompletableDeferred<Result<Purchase>>()
        pendingPurchase = deferred

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()

        val launchResult = withContext(Dispatchers.Main) {
            billingClient.launchBillingFlow(activity, flowParams)
        }
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            return Result.failure(
                BillingUnavailableException(launchResult.debugMessage ?: "Could not start purchase flow"),
            )
        }
        return deferred.await()
    }

    suspend fun queryActivePurchases(): List<Purchase> {
        if (!connect()) return emptyList()
        return withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                billingClient.queryPurchasesAsync(params) { result, purchases ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        cont.resume(
                            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED },
                        )
                    } else {
                        cont.resume(emptyList())
                    }
                }
            }
        }
    }

    suspend fun acknowledgeIfNeeded(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true
        if (!connect()) return false
        return withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }
        }
    }
}
