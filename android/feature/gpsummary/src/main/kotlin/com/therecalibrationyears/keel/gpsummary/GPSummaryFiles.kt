package com.therecalibrationyears.keel.gpsummary

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.File

/**
 * The GP summary PDF on its way out (iOS `writeTemporaryFile` + `ShareSheet`) and the preview
 * of it. The file sits in Keel's private cache, reachable only through the share she starts.
 */
object GPSummaryFiles {
    private const val SHARED_DIR = "gp-summary" // not-copy: folder name (matches res/xml/gp_summary_paths.xml)

    private fun authority(context: Context) = "${context.packageName}.gpsummary" // not-copy: provider authority

    /**
     * Write the PDF for sharing and return its content URI. Any earlier summary is removed
     * first: Android can't say when the receiving app has finished reading a file, so the old
     * one is cleared when she makes a new one rather than when the share sheet closes.
     */
    fun writeForSharing(context: Context, bytes: ByteArray, fileName: String): Uri {
        val dir = File(context.cacheDir, SHARED_DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, fileName).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** The system share sheet for the PDF, with read access granted for this share only. */
    fun shareIntent(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf" // not-copy: MIME type
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    /**
     * Each page of the PDF as a bitmap [widthPx] wide, so the preview shows the very bytes that
     * are shared. The page is white paper whatever the app's theme, as on iOS.
     */
    fun rasterize(context: Context, bytes: ByteArray, widthPx: Int): List<Bitmap> {
        val file = File.createTempFile("gp-preview", ".pdf", context.cacheDir) // not-copy: temp file
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            val height = (widthPx.toFloat() * page.height / page.width).toInt()
                            createBitmap(widthPx, height).also {
                                it.eraseColor(android.graphics.Color.WHITE) // paper
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }
}
