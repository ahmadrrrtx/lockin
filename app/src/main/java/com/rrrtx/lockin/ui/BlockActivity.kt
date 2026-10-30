package com.rrrtx.lockin.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.DurationRules
import com.rrrtx.lockin.core.IntegrityFlag
import com.rrrtx.lockin.core.RefreshBus
import com.rrrtx.lockin.core.SessionEngine
import com.rrrtx.lockin.core.SessionRepository
import android.text.format.DateFormat
import java.util.Date

/**
 * Full-screen informational block shown when a locked app is opened.
 * Deliberately contains no control that can alter the session — the only action
 * opens Lockin's own session view (which also has no early-unlock path).
 */
class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var blockedLabel: String? = null
    private var blockedIcon: Drawable? = null

    private lateinit var tvCountdown: TextView
    private lateinit var tvEnds: TextView
    private lateinit var tvAppsCount: TextView
    private lateinit var tvAppState: TextView
    private lateinit var tvIntegrity: TextView

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvCountdown = findViewById(R.id.tvCountdown)
        tvEnds = findViewById(R.id.tvEnds)
        tvAppsCount = findViewById(R.id.tvAppsCount)
        tvAppState = findViewById(R.id.tvAppState)
        tvIntegrity = findViewById(R.id.tvIntegrity)

        blockedLabel = intent.getStringExtra(EXTRA_PACKAGE)?.let { pkg ->
            runCatching {
                val info = packageManager.getApplicationInfo(pkg, 0)
                blockedIcon = packageManager.getApplicationIcon(info)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(pkg)
        }

        findViewById<ImageView>(R.id.imgBlockedApp)?.let { view ->
            blockedIcon?.let { view.setImageDrawable(it) } ?: view.setImageResource(R.drawable.ic_apps)
        }
        tvAppState.text = getString(
            R.string.block_opened_label,
            blockedLabel ?: getString(R.string.app_name)
        )

        findViewById<android.widget.Button>(R.id.btnOpenLockin)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(MainActivity.EXTRA_ROUTE, "active")
            })
            finish()
        }

        receiver = RefreshBus.register(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refresh()
            }
        })

        refresh()
        handler.post(tick)
    }

    private fun refresh() {
        val evaluation = runCatching { SessionEngine.evaluate(this) }.getOrNull()
        if (evaluation == null) {
            finish()
            return
        }
        if (evaluation.result.expired) {
            // Still enforcing until SessionEngine completes the transition.
            runCatching { SessionEngine.completeIfDue(this) }
        }
        val session = evaluation.session
        tvCountdown.text = DurationRules.formatCountdown(evaluation.result.remainingMs)
        tvEnds.text = getString(
            R.string.active_end_time,
            DateFormat.getTimeFormat(this).format(Date(session.deadlineWallMs))
        )
        val appCount = runCatching {
            SessionRepository(this).packagesOf(session.id).size
        }.getOrDefault(0)
        tvAppsCount.text = getString(R.string.block_apps_label, appCount)
        tvIntegrity.visibility =
            if (session.integrity == IntegrityFlag.DEGRADED) android.view.View.VISIBLE
            else android.view.View.GONE
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        receiver?.let { RefreshBus.unregister(this, it) }
        receiver = null
        super.onDestroy()
    }
}
