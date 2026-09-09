package com.boxplay.billing

object BoxPlayBillingConfig {
    const val PackageName = "com.boxplay"
    const val UnlockAllProductId = "boxplay_unlock_all"

    // Exactly ONE box in the whole app is free: the first box (id
    // AudioBoxConfig.FIRST_BOX_ID == 1) of the first/default scene
    // (AudioSceneConfig.DEFAULT_SCENE_ID == 1). Every other box — including
    // ids 21..40 in any scene and box 1 of every scene beyond the first —
    // requires the boxplay_unlock_all purchase. Gating is on the
    // (sceneId, boxId) pair, NOT on boxId alone, because box ids are local
    // to each scene and id 1 recurs in every scene.
    const val FreeSceneId = 1
    const val FreeBoxId = 1

    fun isFreeBox(sceneId: Int, boxId: Int): Boolean =
        sceneId == FreeSceneId && boxId == FreeBoxId

    fun isPremiumBox(sceneId: Int, boxId: Int): Boolean = !isFreeBox(sceneId, boxId)
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
