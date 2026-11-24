package com.xxxx.emby_tv.util

import com.xxxx.emby_tv.data.model.BaseItemDto

/**
 * Box-set catalog and child ids for one server session.
 * A null catalog means "not loaded yet". An empty list means the server has none.
 * Child ids are remembered even when this title is not a member, so the next
 * title does not walk the catalog again.
 */
class CollectionIndex {
    private var catalog: List<BaseItemDto>? = null
    private val childIds = linkedMapOf<String, List<String>>()
    private val works = linkedMapOf<String, List<BaseItemDto>>()

    fun catalog(): List<BaseItemDto>? = catalog

    fun rememberCatalog(boxes: List<BaseItemDto>) {
        catalog = boxes.filter { EmbyPresentation.isBoxSet(it) && !it.id.isNullOrBlank() }
    }

    fun children(boxId: String): List<String>? = childIds[boxId]

    fun rememberChildren(boxId: String, ids: List<String>) {
        childIds[boxId] = ids.filter { it.isNotBlank() }
    }

    fun works(boxId: String): List<BaseItemDto>? = works[boxId]

    fun rememberWorks(boxId: String, items: List<BaseItemDto>) {
        works[boxId] = items
    }

    /** Boxes whose child list is not cached. Direct hits still need a display list, not another id scan. */
    fun boxesToScan(candidates: List<BaseItemDto>): List<BaseItemDto> {
        return candidates.filter { box ->
            val id = box.id
            !id.isNullOrBlank() && !childIds.containsKey(id)
        }
    }

    /**
     * A cached list that does not contain this title is not reused. Those
     * boxes are asked again, along with any box that has not been read yet.
     */
    fun boxesToConfirm(candidates: List<BaseItemDto>, itemId: String): List<BaseItemDto> {
        return candidates.filter { box ->
            val id = box.id ?: return@filter false
            val cached = childIds[id] ?: return@filter true
            itemId !in cached
        }
    }

    fun memberBoxes(itemId: String, direct: List<BaseItemDto>): List<BaseItemDto> {
        val hits = linkedMapOf<String, BaseItemDto>()
        for (box in direct) {
            if (!EmbyPresentation.isBoxSet(box)) continue
            val id = box.id ?: continue
            hits.putIfAbsent(id, box)
        }
        for (box in catalog.orEmpty()) {
            val id = box.id ?: continue
            if (hits.containsKey(id)) continue
            val members = childIds[id] ?: continue
            if (EmbyPresentation.boxContainsTitle(members, itemId)) hits[id] = box
        }
        return hits.values.toList()
    }

    fun rowsFor(
        itemId: String,
        direct: List<BaseItemDto>,
    ): List<Pair<BaseItemDto, List<BaseItemDto>>> {
        return memberBoxes(itemId, direct).mapNotNull { box ->
            val id = box.id ?: return@mapNotNull null
            val members = works[id] ?: return@mapNotNull null
            val others = EmbyPresentation.collectionWorks(members, itemId)
            if (others.isEmpty()) null else box to others
        }
    }
}

object CollectionMemory {
    private val indexes = java.util.concurrent.ConcurrentHashMap<String, CollectionIndex>()

    fun index(server: String, user: String): CollectionIndex =
        indexes.getOrPut("$server\u0000$user") { CollectionIndex() }
}
