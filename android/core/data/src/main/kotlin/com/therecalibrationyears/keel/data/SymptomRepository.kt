package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.CheckInDao
import com.therecalibrationyears.keel.data.db.SymptomDao
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.QuickChipCandidate
import com.therecalibrationyears.keel.domain.QuickChipRanking
import com.therecalibrationyears.keel.domain.SymptomCatalog
import com.therecalibrationyears.keel.domain.SymptomCategory
import com.therecalibrationyears.keel.domain.SyncStatus
import java.text.Collator
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The symptom catalog (port of iOS `SymptomRepository`). Built-ins are global reference
 * data (empty owner, never uploaded); her own symptoms are custom and hers.
 */
class SymptomRepository(
    private val dao: SymptomDao,
    private val checkIns: CheckInDao,
    private val settings: SettingsStore,
    private val ownerId: suspend () -> String,
    private val clock: KeelClock,
) {
    private val writes = Mutex()

    /** iOS `localizedCaseInsensitiveCompare`: ignores case, not accents. */
    private val sameName = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
    private val byName = Collator.getInstance(Locale.getDefault())

    /**
     * Bring the built-ins in line with [SymptomCatalog], once per catalog version (and again
     * if the store is empty, e.g. after a restore). Version-gated rather than every launch:
     * once she has moved or removed a chip herself, that stays her call.
     */
    suspend fun syncBuiltIns() = writes.withLock {
        val stored = settings.symptomCatalogVersion.first()
        if (stored >= SymptomCatalog.VERSION && dao.count() > 0) return@withLock
        reconcileBuiltIns()
        settings.setSymptomCatalogVersion(SymptomCatalog.VERSION)
    }

    private suspend fun reconcileBuiltIns() {
        val now = clock.now()
        // Written in one transaction at the end (iOS saves the context once), so nothing
        // ever reads a half-seeded catalog. Keyed by id: a later change to a row wins.
        val writes = linkedMapOf<String, SymptomEntity>()
        fun write(s: SymptomEntity) { writes[s.id] = s }
        val all = dao.all()
        val builtIns = all.filter { !it.isCustom }.associateBy { it.name.lowercase() }.toMutableMap()
        fun reference(s: SymptomEntity) = s.copy(updatedAt = now, syncStatusRaw = SyncStatus.SYNCED.raw)

        // Carry history across renamed wording rather than stranding it on a hidden chip.
        SymptomCatalog.renamed.forEach { (old, new) ->
            val s = builtIns[old.lowercase()] ?: return@forEach
            if (builtIns[new.lowercase()] != null) return@forEach
            val renamed = reference(s.copy(name = new))
            write(renamed)
            builtIns.remove(old.lowercase()); builtIns[new.lowercase()] = renamed
        }

        val catalogNames = mutableSetOf<String>()
        SymptomCatalog.builtIn.forEach { entry ->
            val key = entry.name.lowercase()
            catalogNames += key
            val existing = builtIns[key]
            if (existing != null) {
                write(reference(existing.copy(categoryRaw = entry.category.raw, isDefaultChip = entry.isDefault)))
            } else {
                write(
                    SymptomEntity(
                        id = UUID.randomUUID().toString().uppercase(), name = entry.name, categoryRaw = entry.category.raw,
                        isCustom = false, isDefaultChip = entry.isDefault, ownerID = "",
                        createdAt = now, updatedAt = now, syncStatusRaw = SyncStatus.SYNCED.raw,
                    ),
                )
            }
        }

        // Built-ins the catalog dropped: archive, never delete, so past entries keep their labels.
        builtIns.values.filter { !it.isArchived && it.name.lowercase() !in catalogNames }
            .forEach { write(reference(it.copy(isArchived = true))) }
        dao.upsertAll(writes.values.toList())
    }

    /** Active symptoms (not archived), by name in her language's order. */
    fun observeActive(): Flow<List<SymptomEntity>> = dao.observeActive().map { list -> list.sortedWith(compareBy(byName) { it.name }) }

    suspend fun allActive(): List<SymptomEntity> = observeActive().first()

    /**
     * The quick chips shown before "more": her most-logged first, then the default chips,
     * as many as there are default chips.
     */
    suspend fun defaultChips(): List<SymptomEntity> {
        val active = allActive()
        val candidates = active.map { QuickChipCandidate(it.id, it.name, it.isDefaultChip) }
        val limit = maxOf(candidates.count { it.isDefaultChip }, 1)
        val counts = checkIns.usageCounts().associate { it.symptomId to it.uses }
        val byId = active.associateBy { it.id }
        return QuickChipRanking.rankedIds(candidates, counts, limit).mapNotNull(byId::get)
    }

    /** Built-in groups that have symptoms, in display order (iOS `grouped()`). */
    suspend fun grouped(): List<Pair<SymptomCategory, List<SymptomEntity>>> {
        val all = allActive()
        return SymptomCategory.entries.mapNotNull { c ->
            all.filter { it.categoryRaw == c.raw }.takeIf { it.isNotEmpty() }?.let { c to it }
        }
    }

    suspend fun search(query: String): List<SymptomEntity> {
        val q = query.trim()
        return allActive().let { all -> if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) } }
    }

    /**
     * Her own symptom in a group (built-in raw value or a group she made). Reuses a match
     * ignoring case and surrounding spaces, so nothing is duplicated.
     */
    suspend fun findOrCreateCustom(name: String, categoryRaw: String): SymptomEntity = writes.withLock {
        val trimmed = name.trim()
        allActive().firstOrNull { sameName.compare(it.name, trimmed) == 0 }?.let { return@withLock it }
        val now = clock.now()
        SymptomEntity(
            id = UUID.randomUUID().toString().uppercase(), name = trimmed, categoryRaw = categoryRaw,
            isCustom = true, ownerID = ownerId(), createdAt = now, updatedAt = now,
        ).also { dao.upsert(it) }
    }

    /** "Remove from list": archived, so past entries keep it and it can come back. */
    suspend fun archive(symptom: SymptomEntity) = touch(symptom.copy(isArchived = true))

    suspend fun rename(symptom: SymptomEntity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) touch(symptom.copy(name = trimmed))
    }

    /** Move to another group (drag and drop in the picker). */
    suspend fun move(symptom: SymptomEntity, categoryRaw: String) {
        if (symptom.categoryRaw != categoryRaw) touch(symptom.copy(categoryRaw = categoryRaw))
    }

    private suspend fun touch(s: SymptomEntity) = writes.withLock {
        dao.upsert(s.copy(updatedAt = clock.now(), syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw))
    }
}
