package com.boxplay.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real Google Play Billing wiring for the plan approved in
 * docs/BOXPLAY_PLANO_COMPRA_UNICA_PLAYSTORE_V1.txt: a single non-consumable
 * ("managed") in-app product ([BoxPlayBillingConfig.UnlockAllProductId]) that
 * unlocks boxes [BoxPlayBillingConfig.FirstPremiumBoxId]..[BoxPlayBillingConfig.LastPremiumBoxId].
 * Box [BoxPlayBillingConfig.FreeBoxId] is always free.
 *
 * This class only talks to the Play Billing library and reports what it found
 * as a [PurchaseEntitlement] with [EntitlementSource.PlayBilling]. It is a
 * fast, mostly-offline signal — it is NOT the final source of truth once the
 * backend described in the plan (Cloud Function validating against the Google
 * Play Developer API + Firestore) exists. Until that backend is wired in,
 * this local check is the only signal the app has, which is acceptable for
 * testing but must not be treated as tamper-proof for real sales — see
 * section 4 ("SEGURANCA") of the plan document.
 *
 * IMPORTANT — Play Console setup required before this can do anything real:
 *   1. Upload the app to at least an internal testing track.
 *   2. Create a managed in-app product with the exact id
 *      [BoxPlayBillingConfig.UnlockAllProductId], set its price, activate it.
 *   3. Add a license tester account to test purchases without being charged.
 */
class BillingManager(context: Context) {

    private val appContext = context.applicationContext

    // Kept as a named property (not just inside the scope's constructor call)
    // so destroy() can cancel it explicitly — the onBillingServiceDisconnected
    // retry-with-backoff below can otherwise keep firing after the owning
    // ViewModel has already been cleared.
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    @Volatile
    private var isDestroyed = false

    private val _entitlement = MutableStateFlow(PurchaseEntitlement.FreeOnly)
    val entitlement: StateFlow<PurchaseEntitlement> = _entitlement.asStateFlow()

    private val _unlockProduct = MutableStateFlow<ProductDetails?>(null)
    val unlockProduct: StateFlow<ProductDetails?> = _unlockProduct.asStateFlow()

    private val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase -> scope.launch { handlePurchase(purchase) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // user backed out of the purchase sheet; nothing to do
            }
            else -> {
                _billingError.value = billingResult.debugMessage
                Log.w(TAG, "onPurchasesUpdated: ${billingResult.responseCode} ${billingResult.debugMessage}")
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    fun start() {
        if (isDestroyed) return
        connect()
    }

    private fun connect() {
        if (isDestroyed) return
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (isDestroyed) return
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else {
                    _billingError.value = billingResult.debugMessage
                    Log.w(TAG, "setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                if (isDestroyed) return
                scope.launch {
                    delay(2_000)
                    if (!isDestroyed) connect()
                }
            }
        })
    }

    /** Re-checks owned purchases and refreshes the product's price/details. */
    fun refresh() {
        if (isDestroyed) return
        scope.launch { queryProductDetails() }
        scope.launch { queryOwnedPurchases() }
    }

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BoxPlayBillingConfig.UnlockAllProductId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _unlockProduct.value = result.productDetailsList?.firstOrNull()
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
    }

    private suspend fun queryOwnedPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val owned = result.purchasesList.any { purchase ->
                purchase.products.contains(BoxPlayBillingConfig.UnlockAllProductId) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (owned) {
                _entitlement.value = PurchaseEntitlement.purchased(
                    source = EntitlementSource.PlayBilling,
                    verifiedAtMillis = System.currentTimeMillis(),
                )
            } else {
                _entitlement.value = PurchaseEntitlement.FreeOnly
            }
            result.purchasesList.forEach { purchase -> scope.launch { handlePurchase(purchase) } }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(BoxPlayBillingConfig.UnlockAllProductId)) return

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                _entitlement.value = PurchaseEntitlement.purchased(
                    source = EntitlementSource.PlayBilling,
                    verifiedAtMillis = System.currentTimeMillis(),
                )
                if (!purchase.isAcknowledged) {
                    // Non-consumable purchases MUST be acknowledged within 3 days
                    // or Google will automatically refund them.
                    val ackResult = billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build(),
                    )
                    if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "acknowledge failed: ${ackResult.debugMessage}")
                    }
                }
                // TODO(backend): send purchase.purchaseToken + productId to the
                // verification endpoint described in
                // docs/BOXPLAY_PLANO_COMPRA_UNICA_PLAYSTORE_V1.txt section 3 as
                // soon as that endpoint exists, and prefer its response
                // (EntitlementSource.Backend) over this local one.
            }
            Purchase.PurchaseState.PENDING -> {
                _billingError.value = "Pagamento pendente de confirmação."
            }
            else -> Unit
        }
    }

    /** Must be called from a UI layer with a real Activity — Play Billing requires it. */
    fun launchPurchaseFlow(activity: Activity) {
        if (isDestroyed) return
        val product = _unlockProduct.value
        if (product == null) {
            _billingError.value = "Produto indisponível no momento. Tente novamente em instantes."
            refresh()
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingError.value = result.debugMessage
        }
    }

    fun clearError() {
        _billingError.value = null
    }

    fun destroy() {
        isDestroyed = true
        job.cancel()
        billingClient.endConnection()
    }

    companion object {
        private const val TAG = "BillingManager"
    }
}
