package com.rrrtx.lockin.core

/**
 * The Lockin session state machine.
 *
 * Persisted sessions are only ever ACTIVE or COMPLETED; IDLE and PREPARING exist so
 * the whole authoring flow is modeled (and testable) in one place. There is no
 * transition out of ACTIVE except a verified deadline — cancel, pause, shorten and
 * early-unlock events are recognized by the machine purely so they can be REJECTED.
 */
enum class SessionState {
    IDLE,
    PREPARING,
    ACTIVE,
    COMPLETED;

    val isEnforcing: Boolean get() = this == ACTIVE
}

sealed class SessionEvent {

    /* Legal transitions. */
    data class Create(val draft: PrepDraft) : SessionEvent()
    data object Abandon : SessionEvent()           // PREPARING -> IDLE (before START only)
    data object Confirm : SessionEvent()           // PREPARING -> ACTIVE
    data object DeadlineReached : SessionEvent()   // ACTIVE -> COMPLETED (time verified by TimeGuard)

    /** Adding packages during a session only strengthens it: legal self-transition. */
    data class AddPackage(val pkg: String) : SessionEvent()

    /* Recognized-but-forbidden transitions. Every one of these must be rejected. */
    data object CancelSession : SessionEvent()
    data object PauseSession : SessionEvent()
    data object ShortenSession : SessionEvent()
    data object UnlockEarly : SessionEvent()
    data class RemovePackage(val pkg: String) : SessionEvent()
    data object WeakenProtection : SessionEvent()
    data object CreateWhileActive : SessionEvent()
}

object SessionMachine {

    /** Returns the next state, or null when the transition is forbidden. */
    fun transition(from: SessionState, event: SessionEvent): SessionState? = when (from) {
        SessionState.IDLE -> when (event) {
            is SessionEvent.Create -> SessionState.PREPARING
            else -> null
        }

        SessionState.PREPARING -> when (event) {
            is SessionEvent.Abandon -> SessionState.IDLE
            is SessionEvent.Confirm -> SessionState.ACTIVE
            else -> null
        }

        SessionState.ACTIVE -> when (event) {
            is SessionEvent.DeadlineReached -> SessionState.COMPLETED
            is SessionEvent.AddPackage -> SessionState.ACTIVE
            else -> null
        }

        SessionState.COMPLETED -> null // terminal for this session instance
    }

    fun canTransition(from: SessionState, event: SessionEvent): Boolean =
        transition(from, event) != null

    /** True for every event that must never do anything while a session is enforcing. */
    fun isForbiddenWhileActive(event: SessionEvent): Boolean =
        event is SessionEvent.CancelSession ||
            event is SessionEvent.PauseSession ||
            event is SessionEvent.ShortenSession ||
            event is SessionEvent.UnlockEarly ||
            event is SessionEvent.RemovePackage ||
            event is SessionEvent.WeakenProtection ||
            event is SessionEvent.CreateWhileActive
}
