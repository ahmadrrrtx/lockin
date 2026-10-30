package com.rrrtx.lockin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionGateTest {

    @Test
    fun strengtheningAppliesImmediatelyEvenDuringSession() {
        assertEquals(
            RestrictionGate.Decision.ALLOW_NOW,
            RestrictionGate.decide(RestrictionGate.Effect.STRENGTHENS, sessionActive = true)
        )
    }

    @Test
    fun weakeningIsDeferredWhileSessionActive() {
        assertEquals(
            RestrictionGate.Decision.DEFER_UNTIL_SESSION_END,
            RestrictionGate.decide(RestrictionGate.Effect.WEAKENS, sessionActive = true)
        )
    }

    @Test
    fun unprovenIsConservativelyDeferredWhileActive() {
        assertEquals(
            RestrictionGate.Decision.DEFER_UNTIL_SESSION_END,
            RestrictionGate.decide(RestrictionGate.Effect.UNPROVEN, sessionActive = true)
        )
    }

    @Test
    fun everythingAllowedWhenIdle() {
        RestrictionGate.Effect.entries.forEach { effect ->
            assertEquals(
                RestrictionGate.Decision.ALLOW_NOW,
                RestrictionGate.decide(effect, sessionActive = false)
            )
        }
    }

    @Test
    fun classifierIsConservative() {
        assertEquals(
            RestrictionGate.Effect.STRENGTHENS,
            RestrictionGate.classifyStrengthChange(currentStrength = 1, requestedStrength = 2)
        )
        assertEquals(
            RestrictionGate.Effect.WEAKENS,
            RestrictionGate.classifyStrengthChange(currentStrength = 2, requestedStrength = 1)
        )
        assertEquals(
            RestrictionGate.Effect.UNPROVEN,
            RestrictionGate.classifyStrengthChange(currentStrength = 2, requestedStrength = 2)
        )
    }

    @Test
    fun releasingAdminIsAGatedWeakening() {
        assertEquals(
            RestrictionGate.Decision.DEFER_UNTIL_SESSION_END,
            RestrictionGate.decideAction(
                RestrictionGate.DeferredAction.ReleaseDeviceAdmin,
                sessionActive = true
            )
        )
        assertEquals(
            RestrictionGate.Decision.ALLOW_NOW,
            RestrictionGate.decideAction(
                RestrictionGate.DeferredAction.ReleaseDeviceAdmin,
                sessionActive = false
            )
        )
    }

    @Test
    fun unknownCustomActionsAreDeferredWhileActive() {
        assertEquals(
            RestrictionGate.Decision.DEFER_UNTIL_SESSION_END,
            RestrictionGate.decideAction(
                RestrictionGate.DeferredAction.Custom("toggle_something"),
                sessionActive = true
            )
        )
        assertTrue(RestrictionGate.DeferredAction.Custom("x") is RestrictionGate.DeferredAction)
    }
}
