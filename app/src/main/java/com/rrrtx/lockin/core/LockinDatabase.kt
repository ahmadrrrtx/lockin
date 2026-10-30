package com.rrrtx.lockin.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Single canonical store shared by the UI process and the `:lockin_core` process.
 * WAL journaling makes cross-process reads safe; writes are small and guarded by
 * conditional UPDATEs at the repository layer. Migrations are always explicit —
 * no destructive fallback.
 */
class LockinDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                state TEXT NOT NULL,
                started_at_wall INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                deadline_wall INTEGER NOT NULL,
                start_elapsed INTEGER NOT NULL,
                start_boot INTEGER NOT NULL,
                wall_adjust_ms INTEGER NOT NULL DEFAULT 0,
                crossed_reboot INTEGER NOT NULL DEFAULT 0,
                integrity TEXT NOT NULL DEFAULT 'OK',
                commitment TEXT,
                completed_at_wall INTEGER,
                end_reason TEXT,
                schedule_id INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE session_packages (
                session_id INTEGER NOT NULL,
                pkg TEXT NOT NULL,
                PRIMARY KEY (session_id, pkg)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE blocked_attempts (
                pkg TEXT PRIMARY KEY,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_at INTEGER NOT NULL DEFAULT 0,
                session_id INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE schedules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                label TEXT NOT NULL,
                days_mask INTEGER NOT NULL,
                hour INTEGER NOT NULL,
                minute INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                packages TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                last_run_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pending_actions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action_key TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sessions_state ON sessions(state)")
        db.execSQL("CREATE INDEX idx_sessions_completed ON sessions(state, completed_at_wall)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Explicit migrations only; never wipe silently.
        throw IllegalStateException(
            "No migration from $oldVersion to $newVersion defined"
        )
    }

    companion object {
        const val DB_NAME = "lockin.db"
        const val DB_VERSION = 1

        @Volatile
        private var instance: LockinDatabase? = null

        fun getInstance(context: Context): LockinDatabase {
            return instance ?: synchronized(this) {
                instance ?: LockinDatabase(context).also {
                    it.setWriteAheadLoggingEnabled(true)
                    instance = it
                }
            }
        }
    }
}
