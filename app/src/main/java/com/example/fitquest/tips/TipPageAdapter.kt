package com.example.fitquest.tips

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fitquest.R

class TipPageAdapter(private val pages: List<TipPage>) :
    RecyclerView.Adapter<TipPageAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.pageTitle)
        val body: TextView = itemView.findViewById(R.id.pageBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tip_page, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val page = pages[position]
        holder.title.text = page.title
        holder.body.text = page.body
    }

    override fun getItemCount(): Int = pages.size
}
