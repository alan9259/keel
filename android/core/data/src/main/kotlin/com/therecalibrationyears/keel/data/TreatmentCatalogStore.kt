package com.therecalibrationyears.keel.data

import android.content.Context
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import java.io.File

/**
 * The picker list for treatments and supplements (port of iOS `TreatmentCatalogService`).
 * Data, not code: the iOS `treatment-catalog.json` is copied into assets at build time, and
 * a newer cached copy in app storage wins (the seam a remote refresh drops into).
 */
class TreatmentCatalogStore(private val context: Context) {
    val catalog: TreatmentCatalog by lazy { loadCached() ?: loadBundled() ?: TreatmentCatalog.empty }

    private fun loadCached(): TreatmentCatalog? =
        File(context.filesDir, FILE_NAME).takeIf { it.isFile }?.let { runCatching { TreatmentCatalog.decode(it.readText()) }.getOrNull() }

    private fun loadBundled(): TreatmentCatalog? =
        runCatching { context.assets.open(FILE_NAME).bufferedReader().use { it.readText() } }.getOrNull()?.let(TreatmentCatalog::decode)

    private companion object {
        const val FILE_NAME = "treatment-catalog.json"
    }
}
