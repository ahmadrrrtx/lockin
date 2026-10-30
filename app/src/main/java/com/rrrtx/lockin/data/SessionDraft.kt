package com.rrrtx.lockin.data

import com.rrrtx.lockin.core.DurationRules

/** Where the app-selection screen is currently picking for. */
enum class PickTarget {
    /** Building a brand-new session draft. */
    NEW_SESSION,

    /** Strengthening an ACTIVE session (append-only). */
    ADD_TO_ACTIVE,

    /** Choosing packages for a recurring schedule. */
    FOR_SCHEDULE
}

/**
 * Process-scoped draft for the authoring flow. Survives rotation; intentionally
 * cleared when a flow finishes. Not a source of truth — only SessionEngine's
 * database write turns a draft into a session.
 */
object SessionDraft {

    var target: PickTarget = PickTarget.NEW_SESSION
    var packages: MutableList<String> = mutableListOf()
    var durationMs: Long = 25L * 60_000L
    var commitmentText: String? = null
    var scheduleId: Long? = null
    var scheduleLabel: String? = null

    fun startNew() {
        target = PickTarget.NEW_SESSION
        packages = mutableListOf()
        durationMs = 25L * 60_000L
        commitmentText = null
        scheduleId = null
        scheduleLabel = null
    }

    fun startAddToActive(existing: Collection<String>) {
        target = PickTarget.ADD_TO_ACTIVE
        packages = existing.toMutableList()
        durationMs = 0L
        commitmentText = null
    }

    fun startForSchedule(scheduleId: Long?, label: String?) {
        target = PickTarget.FOR_SCHEDULE
        packages = mutableListOf()
        durationMs = 25L * 60_000L
        commitmentText = null
        this.scheduleId = scheduleId
        this.scheduleLabel = label
    }

    val isValidForSession: Boolean
        get() = packages.isNotEmpty() && DurationRules.isValid(durationMs)
}
