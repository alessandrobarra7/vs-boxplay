package com.boxplay.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseEntitlementTest {
    @Test
    fun boxOneIsAvailableWithoutPurchase() {
        val entitlement = PurchaseEntitlement.FreeOnly

        assertTrue(entitlement.canUseBox(1))
    }

    @Test
    fun premiumBoxesRequireUnlockPurchase() {
        val entitlement = PurchaseEntitlement.FreeOnly

        assertFalse(entitlement.canUseBox(2))
        assertFalse(entitlement.canUseBox(20))
    }

    @Test
    fun unlockAllPurchaseAllowsPremiumBoxes() {
        val entitlement = PurchaseEntitlement.purchased(
            source = EntitlementSource.Backend,
            verifiedAtMillis = 1_000L,
        )

        assertTrue(entitlement.canUseBox(1))
        assertTrue(entitlement.canUseBox(2))
        assertTrue(entitlement.canUseBox(20))
    }

    @Test
    fun unrelatedPurchaseDoesNotUnlockPremiumBoxes() {
        val entitlement = PurchaseEntitlement(
            productId = "other_product",
            state = PurchaseState.Purchased,
            source = EntitlementSource.Backend,
            verifiedAtMillis = 1_000L,
        )

        assertFalse(entitlement.canUseBox(2))
    }
}
