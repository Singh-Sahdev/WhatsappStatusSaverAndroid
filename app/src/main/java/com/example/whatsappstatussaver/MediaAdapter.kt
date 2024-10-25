import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.media.ThumbnailUtils
import com.example.whatsappstatussaver.MediaItem
import com.example.whatsappstatussaver.R



//data class MediaItem(
//    val uri: Uri,
//    val displayName: String,
//    val type: String  // "image" or "video"
//)

class MediaAdapter(
    private val mediaList: List<MediaItem>,
    private val onDownloadClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val titleText: TextView = view.findViewById(R.id.titleText)
        val downloadButton: Button = view.findViewById(R.id.downloadButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = mediaList[position]

        holder.titleText.text = mediaItem.displayName

        // Set the appropriate thumbnail depending on file type (image or video)
        if (mediaItem.type == "image") {
            // For images, directly set the URI to the ImageView
            holder.imageView.setImageURI(mediaItem.uri)
        } else if (mediaItem.type == "video") {
            // For videos, generate and set the thumbnail
            val videoThumbnail = generateVideoThumbnail(holder.itemView.context, mediaItem.uri)
            holder.imageView.setImageBitmap(videoThumbnail)
        }

        // Handle download button click
        holder.downloadButton.setOnClickListener {
            onDownloadClick(mediaItem)
        }
    }

    override fun getItemCount(): Int = mediaList.size

    // Helper function to generate a video thumbnail
    private fun generateVideoThumbnail(context: Context, videoUri: Uri): Bitmap? {
        return try {
            val filePath = File(videoUri.path ?: "")
            if (filePath.exists()) {
                // Create a video thumbnail
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    videoUri.lastPathSegment?.toLong() ?: 0L,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            } else {
                // Fallback: Use ThumbnailUtils if the path does not exist
                ThumbnailUtils.createVideoThumbnail(filePath.path, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
