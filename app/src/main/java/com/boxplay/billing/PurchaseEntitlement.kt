package com.boxplay.billing

data class PurchaseEntitlement(
    val productId: String = BoxPlayBillingConfig.UnlockAllProductId,
    val state: PurchaseState = PurchaseState.Unknown,
    val source: EntitlementSource = EntitlementSource.LocalCache,
    val verifiedAtMillis: Long? = null,
) {
    val unlocksAllBoxes: Boolean
        get() = productId == BoxPlayBillingConfig.UnlockAllProductId && state == PurchaseState.Purchased

    fun canUseBox(boxId: Int): Boolean =
        BoxPlayBillingConfig.isFreeBox(boxId) || (BoxPlayBillingConfig.isPremiumBox(boxId) && unlocksAllBoxes)

    companion object {
        val FreeOnly = PurchaseEntitlement(
            state = PurchaseState.NotPurchased,
            source = EntitlementSource.LocalCache,
        )

        fun purchased(
            source: EntitlementSource,
            verifiedAtMillis: Long,
        ): PurchaseEntitlement = PurchaseEntitlement(
            state = PurchaseState.Purchased,
            source = source,
            verifiedAtMillis = verifiedAtMillis,
        )
    }
}
