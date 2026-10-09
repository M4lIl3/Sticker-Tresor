package de.stickertresor.app

import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StickerAdapter(
    private val scope: CoroutineScope,
    private val columns: Int,
    private val onClick: (Uri) -> Unit
) : RecyclerView.Adapter<StickerAdapter.Holder>() {

    private var items: List<Uri> = emptyList()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.sticker_image)
        var job: Job? = null
    }

    fun submit(list: List<Uri>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
        val size = parent.measuredWidth / columns
        if (size > 0) {
            view.layoutParams = view.layoutParams.apply { height = size }
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val uri = items[position]
        holder.job?.cancel()
        holder.image.setImageDrawable(null)
        holder.itemView.setOnClickListener { onClick(uri) }
        val ctx = holder.itemView.context
        val size = holder.itemView.layoutParams.height.takeIf { it > 0 } ?: 256
        holder.job = scope.launch {
            val drawable = withContext(Dispatchers.IO) { StickerStore.decode(ctx, uri, size) }
            holder.image.setImageDrawable(drawable)
            (drawable as? AnimatedImageDrawable)?.start()
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.job?.cancel()
        holder.image.setImageDrawable(null)
    }
}
