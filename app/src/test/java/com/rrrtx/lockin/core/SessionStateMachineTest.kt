package com.rrrtx.lockin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {

    private val draft = PrepDraft(listOf("com.example.a"), 30 * 60_000L)

    @Test
    fun idleCreatesPreparation() {
        assertEquals(
            SessionState.PREPARING,
            SessionMachine.transition(SessionState.IDLE, SessionEvent.Create(draft))
        )
    }

    @Test
    fun preparingCanBeAbandonedBeforeStart() {
        assertEquals(
            SessionState.IDLE,
            SessionMachine.transition(SessionState.PREPARING, SessionEvent.Abandon)
        )
    }

    @Test
    fun preparingConfirmsToActive() {
        assertEquals(
            SessionState.ACTIVE,
            SessionMachine.transition(SessionState.PREPARING, SessionEvent.Confirm)
        )
    }

    @Test
    fun activeCompletesOnlyOnVerifiedDeadline() {
        assertEquals(
            SessionState.COMPLETED,
            SessionMachine.transition(SessionState.ACTIVE, SessionEvent.DeadlineReached)
        )
    }

    @Test
    fun activeAllowsAddingPackagesOnly() {
        assertEquals(
            SessionState.ACTIVE,
            SessionMachine.transition(SessionState.ACTIVE, SessionEvent.AddPackage("com.example.b"))
        )
    }

    @Test
    fun activeRejectsEveryEscapeEvent() {
        val escapes = listOf(
            SessionEvent.CancelSession,
            SessionEvent.PauseSession,
            SessionEvent.ShortenSession,
            SessionEvent.UnlockEarly,
            SessionEvent.RemovePackage("com.example.a"),
            SessionEvent.WeakenProtection,
            SessionEvent.CreateWhileActive,
            SessionEvent.Abandon,
            SessionEvent.Confirm
        )
        escapes.forEach { event ->
            assertNull(
                "ACTIVE must reject $event",
                SessionMachine.transition(SessionState.ACTIVE, event)
            )
            assertTrue(
                "$event must be classified as forbidden-while-active",
                SessionMachine.isForbiddenWhileActive(event) ||
                    event is SessionEvent.Abandon ||
                    event is SessionEvent.Confirm
            )
        }
    }

    @Test
    fun completedIsTerminal() {
        val all = listOf(
            SessionEvent.CancelSession,
            SessionEvent.DeadlineReached,
            SessionEvent.AddPackage("x"),
            SessionEvent.Create(draft),
            SessionEvent.Confirm
        )
        all.forEach { event ->
            assertNull(
                "COMPLETED must be terminal (event=$event)",
                SessionMachine.transition(SessionState.COMPLETED, event)
            )
        }
    }

    @Test
    fun cannotConfirmOrFinishFromIdle() {
        assertNull(SessionMachine.transition(SessionState.IDLE, SessionEvent.Confirm))
        assertNull(SessionMachine.transition(SessionState.IDLE, SessionEvent.DeadlineReached))
        assertNull(SessionMachine.transition(SessionState.IDLE, SessionEvent.CancelSession))
    }

    @Test
    fun cannotCreateWhilePreparationExists() {
        assertNull(SessionMachine.transition(SessionState.PREPARING, SessionEvent.Create(draft)))
        assertNull(SessionMachine.transition(SessionState.PREPARING, SessionEvent.DeadlineReached))
    }

    @Test
    fun canTransitionMirrorsTransition() {
        assertTrue(SessionMachine.canTransition(SessionState.PREPARING, SessionEvent.Confirm))
        assertFalse(SessionMachine.canTransition(SessionState.ACTIVE, SessionEvent.CancelSession))
    }

    @Test
    fun onlyActiveStateEnforces() {
        assertTrue(SessionState.ACTIVE.isEnforcing)
        assertFalse(SessionState.IDLE.isEnforcing)
        assertFalse(SessionState.PREPARING.isEnforcing)
        assertFalse(SessionState.COMPLETED.isEnforcing)
    }
}
