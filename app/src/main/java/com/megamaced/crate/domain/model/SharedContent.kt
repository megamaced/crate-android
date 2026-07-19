package com.megamaced.crate.domain.model

// Derived views over a [SharedWithMe] payload, mirroring
// crate/src/composables/useSharedContent.js so the Android "Shared with me"
// landing and per-category subpages bucket content exactly like the web app.
//
// An item can reach the viewer through several shares at once (a whole-library
// share, a category share, a single-item share and/or a playlist share); these
// helpers dedupe by id within each category and keep the writable copy when an
// item is shared more than one way.

// Standard category order used across the app (mirrors HomeView display order).
private val CATEGORY_ORDER =
    listOf(Category.Music, Category.Films, Category.Books, Category.Games, Category.Comics)

data class SharedCategorySummary(
    val category: Category,
    val label: String,
    val count: Int,
    val items: List<MediaItem>,
    // Owners who granted write access covering this category — via a read/write
    // whole-library share or a read/write share of this category. Empty means
    // read-only: no Add affordance. The subpage adds into the first owner.
    val writeOwners: List<String>,
    // Distinct owners contributing items to this category. More than one means
    // the subpage labels each item with its owner (mixed-owner category).
    val owners: List<String>,
)

/** Deduped map of category → shared items. Mirrors itemsByCategory. */
fun SharedWithMe.itemsByCategory(): Map<Category, List<MediaItem>> {
    val byCat = LinkedHashMap<Category, LinkedHashMap<Long, MediaItem>>()

    fun add(item: MediaItem) {
        val map = byCat.getOrPut(item.category) { LinkedHashMap() }
        val existing = map[item.id]
        // Prefer the writable copy when the same item is shared multiple ways.
        if (existing == null || (item.canWrite && !existing.canWrite)) {
            map[item.id] = item
        }
    }

    // Single-item shares — already carry category + canWrite.
    albums.forEach(::add)
    // Category shares — the wrapper's category applies to every item.
    categories.forEach { share -> share.items.forEach(::add) }
    // Whole-library shares — items span every category the owner has.
    libraries.forEach { share -> share.items.forEach(::add) }
    // Playlist shares are read-only at the item level, but their items should
    // still surface in the owner's category subpages. Forced canWrite=false so
    // any writable library/category copy wins the dedupe above.
    playlists.forEach { share -> share.items.forEach { add(it.copy(canWrite = false)) } }

    return byCat.mapValues { (_, map) -> map.values.toList() }
}

/** Owners who granted write access covering [category]. Mirrors writeOwnersForCategory. */
fun SharedWithMe.writeOwnersForCategory(category: Category): List<String> {
    val owners = LinkedHashSet<String>()
    libraries.forEach { if (it.canWrite) owners.add(it.sharedByUser) }
    categories.forEach { if (it.canWrite && it.category == category) owners.add(it.sharedByUser) }
    return owners.toList()
}

/**
 * Categories with at least one shared item, in the app's standard order, each
 * with its label, count, items and owner metadata. Drives the landing tiles and
 * the per-category subpages. Mirrors sharedCategories.
 */
fun SharedWithMe.sharedCategories(): List<SharedCategorySummary> {
    val map = itemsByCategory()
    return CATEGORY_ORDER
        .filter { (map[it]?.size ?: 0) > 0 }
        .map { category ->
            val items = map.getValue(category)
            SharedCategorySummary(
                category = category,
                label = category.label,
                count = items.size,
                items = items,
                writeOwners = writeOwnersForCategory(category),
                owners = items.mapNotNull { it.userId }.distinct(),
            )
        }
}
