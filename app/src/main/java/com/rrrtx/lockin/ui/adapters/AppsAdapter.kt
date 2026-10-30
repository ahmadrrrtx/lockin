package com.rrrtx.lockin.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rrrtx.lockin.R
import com.rrrtx.lockin.data.AppInventory

/**
 * App picker row adapter. Protected rows stay VISIBLE with their explanation and an
 * inert checkbox — Lockin never silently hides an application from the list.
 */
class AppsAdapter(
    private val onClick: (AppInventory.AppEntry) -> Unit
) : RecyclerView.Adapter<AppsAdapter.Holder>() {

    private var items: List<AppInventory.AppEntry> = emptyList()
    private val selected: MutableSet<String> = mutableSetOf()

    fun submit(list: List<AppInventory.AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    fun setSelected(packages: Collection<String>) {
        selected.clear()
        selected.addAll(packages)
        notifyDataSetChanged()
    }

    fun selectedPackages(): List<String> = selected.toList()

    fun toggle(entry: AppInventory.AppEntry) {
        if (entry.isProtected) return
        if (!selected.add(entry.packageName)) selected.remove(entry.packageName)
        notifyItemChanged(items.indexOfFirst { it.packageName == entry.packageName })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val imgIcon: ImageView = view.findViewById(R.id.imgIcon)
        private val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        private val tvReason: TextView = view.findViewById(R.id.tvReason)
        private val check: CheckBox = view.findViewById(R.id.check)

        fun bind(entry: AppInventory.AppEntry) {
            tvLabel.text = entry.label
            imgIcon.setImageDrawable(entry.icon)
            imgIcon.contentDescription = entry.label

            if (entry.isProtected) {
                tvReason.visible()
                tvReason.text = entry.protectionExplanation
                check.visibility = View.INVISIBLE
                check.isChecked = false
                itemView.alpha = 0.75f
                itemView.setOnClickListener(null)
            } else {
                tvReason.gone()
                check.visibility = View.VISIBLE
                check.isChecked = entry.packageName in selected
                itemView.alpha = 1f
                itemView.setOnClickListener { onClick(entry) }
            }
        }

        private fun View.visible() {
            visibility = View.VISIBLE
        }

        private fun View.gone() {
            visibility = View.GONE
        }
    }
}
