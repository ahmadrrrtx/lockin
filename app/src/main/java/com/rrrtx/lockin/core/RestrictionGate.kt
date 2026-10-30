package com.rrrtx.lockin.core

/**
 * Conservative gate for settings that affect protection strength.
 *
 * While a session is ACTIVE:
 *  - changes that strengthen enforcement apply immediately;
 *  - changes that weaken enforcement (or whose effect cannot be proven) are deferred
 *    until the session completes;
 *  - with no active session everything applies immediately.
 *
 * This supersedes a timed "settings change delay": Lockin has no in-app setting that
 * weakens the active session itself (the block set is append-only in SQL), so the gate
 * covers the remaining preference-level actions such as releasing device admin.
 */
object RestrictionGate {

    enum class Effect { STRENGTHENS, WEAKENS, UNPROVEN }

    enum class Decision { ALLOW_NOW, DEFER_UNTIL_SESSION_END }

    /** Unknown comparisons are conservatively treated as weakening. */
    fun classifyStrengthChange(currentStrength: Int, requestedStrength: Int): Effect = when {
        requestedStrength > currentStrength -> Effect.STRENGTHENS
        requestedStrength < currentStrength -> Effect.WEAKENS
        else -> Effect.UNPROVEN
    }

    fun decide(effect: Effect, sessionActive: Boolean): Decision = when {
        !sessionActive -> Decision.ALLOW_NOW
        effect == Effect.STRENGTHENS -> Decision.ALLOW_NOW
        else -> Decision.DEFER_UNTIL_SESSION_END
    }

    /** Canonical gated actions that can be queued until session completion. */
    sealed class DeferredAction {
        data object ReleaseDeviceAdmin : DeferredAction()
        data class Custom(val key: String) : DeferredAction()
    }

    fun decideAction(
        action: DeferredAction,
        sessionActive: Boolean
    ): Decision = decide(if (action is DeferredAction.ReleaseDeviceAdmin) Effect.WEAKENS else Effect.UNPROVEN, sessionActive)
}
