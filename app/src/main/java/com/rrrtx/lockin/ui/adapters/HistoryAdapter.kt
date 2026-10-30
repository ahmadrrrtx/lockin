package com.rrrtx.lockin.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.DurationRules
import com.rrrtx.lockin.core.HistoryRow
import com.rrrtx.lockin.ui.formatDate
import com.rrrtx.lockin.ui.formatTime

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private var rows: List<HistoryRow> = emptyList()

    fun submit(list: List<HistoryRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(rows[position])
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        private val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        private val tvRange: TextView = view.findViewById(R.id.tvRange)
        private val tvApps: TextView = view.findViewById(R.id.tvApps)
        private val tvAttempts: TextView = view.findViewById(R.id.tvAttempts)

        fun bind(row: HistoryRow) {
            val ctx = itemView.context
            val start = row.session.startedAtWallMs
            val end = row.session.completedAtWallMs ?: (start + row.session.durationMs)

            tvDate.text = formatDate(ctx, end)
            tvDuration.text =
                ctx.getString(R.string.history_row_duration, DurationRules.formatShort(row.session.durationMs))
            tvRange.text = ctx.getString(
                R.string.history_row_range,
                formatTime(ctx, start),
                formatTime(ctx, end)
            )
            tvApps.text = ctx.getString(R.string.history_row_apps, row.packages.size)
            tvAttempts.text = ctx.getString(R.string.history_row_attempts, row.attempts)
        }
    }
}
