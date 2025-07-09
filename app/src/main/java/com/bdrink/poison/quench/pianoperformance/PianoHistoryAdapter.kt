package com.bdrink.poison.quench.pianoperformance

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 钢琴演奏历史记录适配器
 */
class PianoHistoryAdapter(
    private val onItemClick: (PianoRecord) -> Unit,
    private val onPlayPauseClick: (PianoRecord) -> Unit,
    private val onEditClick: (PianoRecord) -> Unit,
    private val onDownloadClick: (PianoRecord) -> Unit
) : RecyclerView.Adapter<PianoHistoryAdapter.HistoryViewHolder>() {

    private val records = mutableListOf<PianoRecord>()
    private var currentPlayingPosition = -1

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnPlayPause: ImageView = itemView.findViewById(R.id.btn_play_pause)
        val tvFileName: TextView = itemView.findViewById(R.id.tv_file_name)
        val tvRecordTime: TextView = itemView.findViewById(R.id.tv_record_time)
        val btnEdit: ImageView = itemView.findViewById(R.id.btn_edit)
        val btnDownload: ImageView = itemView.findViewById(R.id.btn_download)

        fun bind(record: PianoRecord, position: Int) {
            tvFileName.text = record.displayName
            tvRecordTime.text = "${record.recordTime}  ${record.getFormattedDuration()}"

            // 更新播放按钮状态
            updatePlayButtonState(position)

            // 设置点击监听器
            itemView.setOnClickListener {
                onItemClick(record)
            }

            btnPlayPause.setOnClickListener {
                onPlayPauseClick(record)
            }

            btnEdit.setOnClickListener {
                onEditClick(record)
            }

            btnDownload.setOnClickListener {
                onDownloadClick(record)
            }
        }

        private fun updatePlayButtonState(position: Int) {
            if (position == currentPlayingPosition) {
                btnPlayPause.setImageResource(R.drawable.icon_stop)
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_piano_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(records[position], position)
    }

    override fun getItemCount(): Int = records.size

    /**
     * 更新数据
     */
    fun updateRecords(newRecords: List<PianoRecord>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged()
    }

    /**
     * 设置当前播放的项目
     */
    fun setCurrentPlayingPosition(position: Int) {
        val oldPosition = currentPlayingPosition
        currentPlayingPosition = position

        // 更新旧的播放项目
        if (oldPosition >= 0 && oldPosition < records.size) {
            notifyItemChanged(oldPosition)
        }

        // 更新新的播放项目
        if (position >= 0 && position < records.size) {
            notifyItemChanged(position)
        }
    }

    /**
     * 停止播放状态
     */
    fun stopPlaying() {
        setCurrentPlayingPosition(-1)
    }

    /**
     * 更新播放进度
     */
    fun updatePlayProgress(position: Int, progress: Int) {
        if (position >= 0 && position < records.size) {
            notifyItemChanged(position, progress)
        }
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
    }

    /**
     * 移除项目
     */
    fun removeItem(position: Int) {
        if (position >= 0 && position < records.size) {
            records.removeAt(position)
            notifyItemRemoved(position)

            // 调整当前播放位置
            if (currentPlayingPosition == position) {
                currentPlayingPosition = -1
            } else if (currentPlayingPosition > position) {
                currentPlayingPosition--
            }
        }
    }

    /**
     * 更新项目名称
     * 注意：这里只更新内存中的显示，实际的持久化存储在 Activity 中完成
     */
    fun updateItemName(position: Int, newName: String) {
        if (position >= 0 && position < records.size) {
            val record = records[position]
            records[position] = record.copy(displayName = newName)
            notifyItemChanged(position)

            Log.d("PianoHistoryAdapter", "适配器中更新项目名称: 位置=$position, 新名称=$newName")
        }
    }

    /**
     * 获取当前播放位置
     */
    fun getCurrentPlayingPosition(): Int = currentPlayingPosition

    /**
     * 根据文件查找位置
     */
    fun findPositionByFile(file: java.io.File): Int {
        return records.indexOfFirst { it.file.absolutePath == file.absolutePath }
    }

    /**
     * 根据位置获取记录
     */
    fun getRecord(position: Int): PianoRecord? {
        return if (position >= 0 && position < records.size) records[position] else null
    }

    /**
     * 获取所有记录
     */
    fun getAllRecords(): List<PianoRecord> = records.toList()
}