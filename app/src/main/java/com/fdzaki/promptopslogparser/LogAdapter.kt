package com.fdzaki.promptopslogparser

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val items = mutableListOf<LogEntry>()

    fun submitList(newItems: List<LogEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_line, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvLogLine)

        fun bind(entry: LogEntry) {
            textView.text = "${entry.lineNumber}: ${entry.rawText}"
            textView.setTextColor(
                when (entry.level) {
                    LogLevel.ERROR -> Color.parseColor("#FF5252")
                    LogLevel.WARNING -> Color.parseColor("#FFCA28")
                    LogLevel.CUSTOM -> Color.parseColor("#BA68C8")
                    LogLevel.NORMAL -> Color.parseColor("#E0E0E0")
                }
            )
        }
    }
}
