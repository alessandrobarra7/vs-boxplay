package com.boxplay.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseEntitlementTest {
    @Test
    fun boxOneOfFirstSceneIsAvailableWithoutPurchase() {
        val entitlement = PurchaseEntitlement.FreeOnly

        assertTrue(entitlement.canUseBox(sceneId = 1, boxId = 1))
    }

    @Test
    fun premiumBoxesInFirstSceneRequireUnlockPurchase() {
        val entitlement = PurchaseEntitlement.FreeOnly

        assertFalse(entitlement.canUseBox(sceneId = 1, boxId = 2))
        assertFalse(entitlement.canUseBox(sceneId = 1, boxId = 20))
        assertFalse(entitlement.canUseBox(sceneId = 1, boxId = 21))
        assertFalse(entitlement.canUseBox(sceneId = 1, boxId = 40))
    }

    @Test
    fun boxOneOfAnyOtherSceneRequiresUnlockPurchase() {
        val entitlement = PurchaseEntitlement.FreeOnly

        assertFalse(entitlement.canUseBox(sceneId = 2, boxId = 1))
        assertFalse(entitlement.canUseBox(sceneId = 15, boxId = 1))
    }

    @Test
    fun unlockAllPurchaseAllowsEveryBoxInEveryScene() {
        val entitlement = PurchaseEntitlement.purchased(
            source = EntitlementSource.Backend,
            verifiedAtMillis = 1_000L,
        )

        assertTrue(entitlement.canUseBox(sceneId = 1, boxId = 1))
        assertTrue(entitlement.canUseBox(sceneId = 1, boxId = 2))
        assertTrue(entitlement.canUseBox(sceneId = 1, boxId = 20))
        assertTrue(entitlement.canUseBox(sceneId = 1, boxId = 21))
        assertTrue(entitlement.canUseBox(sceneId = 1, boxId = 40))
        assertTrue(entitlement.canUseBox(sceneId = 2, boxId = 1))
        assertTrue(entitlement.canUseBox(sceneId = 15, boxId = 40))
    }

    @Test
    fun unrelatedPurchaseDoesNotUnlockPremiumBoxes() {
        val entitlement = PurchaseEntitlement(
            productId = "other_product",
            state = PurchaseState.Purchased,
            source = EntitlementSource.Backend,
            verifiedAtMillis = 1_000L,
        )

        assertFalse(entitlement.canUseBox(sceneId = 1, boxId = 2))
        assertFalse(entitlement.canUseBox(sceneId = 2, boxId = 1))
    }
}
