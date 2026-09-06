package com.boxplay.billing

object BoxPlayBillingConfig {
    const val PackageName = "com.boxplay"
    const val UnlockAllProductId = "boxplay_unlock_all"
    const val FreeBoxId = 1
    const val FirstPremiumBoxId = 2
    const val LastPremiumBoxId = 20

    fun isFreeBox(boxId: Int): Boolean = boxId == FreeBoxId

    fun isPremiumBox(boxId: Int): Boolean = boxId in FirstPremiumBoxId..LastPremiumBoxId
}

enum class PurchaseState {
    Unknown,
    NotPurchased,
    Pending,
    Purchased,
    Revoked,
    Error,
}

enum class EntitlementSource {
    LocalCache,
    PlayBilling,
    Backend,
}

data class PurchaseVerificationRequest(
    val packageName: String,
    val productId: String,
    val purchaseToken: String,
)
