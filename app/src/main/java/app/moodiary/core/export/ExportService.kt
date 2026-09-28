package app.moodiary.core.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import app.moodiary.core.media.PhotoStore
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ExportService @Inject constructor(@ApplicationContext private val context: Context, private val repository: JournalRepository, private val photos: PhotoStore) {
    suspend fun export(uri: Uri, format: String, filter: SearchFilter, includePhotos: Boolean): Unit = withContext(Dispatchers.IO) {
        repository.withExclusive {
            repository.requireAvailable()
            val data = repository.snapshot()
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { context.localizedString(R.string.core_export_write_failed) }.use { output ->
                when (format) {
                    "JSON" -> output.write(ExportCodec.json(data).toByteArray(Charsets.UTF_8))
                    "CSV" -> output.write(ExportCodec.csv(data, ZoneId.systemDefault(), filter).toByteArray(Charsets.UTF_8))
                    "PDF" -> { val report = pdf(data, filter, includePhotos); try { report.writeTo(output) } finally { report.close() } }
                    else -> error(context.localizedString(R.string.core_export_unsupported_format))
                }
            }
        }
    }
    private fun pdf(data: JournalData, filter: SearchFilter, includePhotos: Boolean): PdfDocument {
        val document = PdfDocument()
        try {
            var pageNumber = 0
            var page: PdfDocument.Page? = null
            var y = 0f
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
            fun newPage() {
                page?.let { document.finishPage(it) }
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                page!!.canvas.drawColor(Color.WHITE)
                page!!.canvas.drawText(context.localizedString(R.string.core_pdf_footer, pageNumber), 42f, 810f, TextPaint(paint).apply { textSize = 9f })
                y = 42f
            }
            fun text(value: String, size: Float = 11f) {
                paint.textSize = size
                val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, 511).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f, 1f).setIncludePad(false).build()
                for (line in 0 until layout.lineCount) {
                    val height = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                    if (page == null || y + height > 778) newPage()
                    val canvas = page!!.canvas
                    canvas.save(); canvas.clipRect(42f, y, 553f, y + height)
                    canvas.translate(42f, y - layout.getLineTop(line)); layout.draw(canvas); canvas.restore()
                    y += height
                }
                y += 7
            }
            newPage()
            val entries = EntrySearch.filter(data, filter, ZoneId.systemDefault())
            text(context.localizedString(R.string.core_pdf_title), 22f)
            text(context.localizedString(R.string.core_pdf_date_range, filter.from?.toString() ?: context.localizedString(R.string.core_pdf_first_entry), filter.through?.toString() ?: context.localizedString(R.string.core_pdf_latest_entry)))
            text(if (entries.isEmpty()) context.localizedString(R.string.core_pdf_summary_empty, entries.size) else context.localizedString(R.string.core_pdf_summary, entries.size, entries.map { it.moodScore }.average()))
            text(context.localizedString(R.string.core_pdf_mood_counts, entries.groupingBy { it.moodName }.eachCount().entries.joinToString { context.localizedString(R.string.core_pdf_count_item, it.key, it.value) }))
            val activityNames = data.activities.associate { it.id to it.name }
            val links = data.entryActivities.groupBy { it.entryId }
            val entryIds = entries.mapTo(HashSet()) { it.id }
            text(context.localizedString(R.string.core_pdf_activity_counts, data.entryActivities.filter { it.entryId in entryIds }.groupingBy { it.activityId }.eachCount().entries.joinToString { context.localizedString(R.string.core_pdf_count_item, activityNames[it.key].orEmpty(), it.value) }))
            val photoByEntry = data.photos.groupBy { it.entryId }
            entries.forEach { entry ->
                text("${Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"))} · ${entry.moodName} (${entry.moodScore})", 14f)
                val activities = links[entry.id].orEmpty().mapNotNull { activityNames[it.activityId] }
                if (activities.isNotEmpty()) text(activities.joinToString(" · "))
                if (entry.note.isNotEmpty()) text(entry.note)
                if (includePhotos) photoByEntry[entry.id].orEmpty().sortedBy { it.sortOrder }.forEach { photo ->
                    val file = photos.file(photo.localPath)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
                    val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1) }
                    val bitmap = BitmapFactory.decodeFile(file.path, options)
                    if (bitmap != null) {
                        val width = minOf(300f, bitmap.width.toFloat())
                        val height = minOf(220f, width * bitmap.height / bitmap.width)
                        val actualWidth = height * bitmap.width / bitmap.height
                        if (y + height > 778) newPage()
                        page!!.canvas.drawBitmap(bitmap, null, RectF(42f, y, 42f + actualWidth, y + height), null); y += height + 12; bitmap.recycle()
                    } else text(context.localizedString(R.string.core_pdf_photo_unavailable))
                }
                y += 10
            }
            page?.let { document.finishPage(it) }
            return document
        } catch (failure: Exception) { document.close(); throw failure }
    }
}
