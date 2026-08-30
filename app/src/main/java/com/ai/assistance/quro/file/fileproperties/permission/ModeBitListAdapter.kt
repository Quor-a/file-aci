/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.fileproperties.permission

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.ai.assistance.quro.file.databinding.ModeBitItemBinding
import com.ai.assistance.quro.file.provider.common.PosixFileModeBit
import com.ai.assistance.quro.file.util.layoutInflater

class ModeBitListAdapter(
    private val modeBits: List<PosixFileModeBit>,
    private val modeBitNames: Array<String>
) : BaseAdapter() {
    var mode: Set<PosixFileModeBit> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = modeBits.size

    override fun getItem(position: Int): PosixFileModeBit = modeBits[position]

    override fun hasStableIds(): Boolean = true

    override fun getItemId(position: Int): Long = getItem(position).ordinal.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val modeBit = getItem(position)
        val binding = convertView?.tag as ModeBitItemBinding?
            ?: ModeBitItemBinding.inflate(parent.context.layoutInflater, parent, false)
                .apply { root.tag = this }
        binding.modeBitCheck.text = modeBitNames[position]
        binding.modeBitCheck.isChecked = modeBit in mode
        return binding.root
    }
}
