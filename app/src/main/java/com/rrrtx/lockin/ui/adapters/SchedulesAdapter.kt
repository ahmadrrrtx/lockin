package com.rrrtx.lockin.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.DurationRules
import com.rrrtx.lockin.core.Schedule
import com.rrrtx.lockin.core.SchedulePlanner
import com.rrrtx.lockin.ui.formatDateTime
import java.text.DateFormat

class SchedulesAdapter(
    private val onOpen: (Schedule) -> Unit,
    private val onToggle: (Schedule, Boolean) -> Unit
) : RecyclerView.Adapter<SchedulesAdapter.Holder>() {

    private var items: List<Schedule> = emptyList()

    fun submit(list: List<Schedule>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        private val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        private val tvNext: TextView = view.findViewById(R.id.tvNext)
        private val switchEnabled: MaterialSwitch = view.findViewById(R.id.switchEnabled)

        fun bind(schedule: Schedule) {
            val ctx = itemView.context
            tvLabel.text = schedule.label

            val dayLabels = arrayOf(
                ctx.getString(R.string.day_mon),
                ctx.getString(R.string.day_tue),
                ctx.getString(R.string.day_wed),
                ctx.getString(R.string.day_thu),
                ctx.getString(R.string.day_fri),
                ctx.getString(R.string.day_sat),
                ctx.getString(R.string.day_sun)
            )
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).let {
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, schedule.hour)
                    set(java.util.Calendar.MINUTE, schedule.minute)
                }
                it.format(cal.time)
            }
            tvMeta.text = buildString {
                append(SchedulePlanner.labelFor(schedule.daysMask, dayLabels))
                append(" · ")
                append(time)
                append(" · ")
                append(DurationRules.formatShort(schedule.durationMs))
                append(" · ")
                append(ctx.getString(R.string.schedules_edit_apps_count, schedule.packages.size))
            }

            if (schedule.enabled) {
                val next = SchedulePlanner.nextRunAfter(
                    System.currentTimeMillis(),
                    schedule.daysMask,
                    schedule.hour,
                    schedule.minute
                )
                tvNext.text = if (next > 0L) {
                    ctx.getString(R.string.schedule_next_run, formatDateTime(ctx, next))
                } else {
                    ctx.getString(R.string.time_undefined)
                }
                tvNext.visibility = View.VISIBLE
            } else {
                tvNext.visibility = View.GONE
            }

            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = schedule.enabled
            switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(schedule, checked) }
            itemView.setOnClickListener { onOpen(schedule) }
        }
    }
}
