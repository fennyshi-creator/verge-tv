package com.xxxx.emby_tv.ui.update

/** Compares GitHub release tags with the running version. An older tag is not an update. */
object ReleaseVersions {
    fun isNewer(latestTag: String, currentName: String): Boolean {
        val latest = numbers(latestTag)
        val current = numbers(currentName)
        if (latest.isEmpty() || current.isEmpty()) return false
        val count = maxOf(latest.size, current.size)
        for (index in 0 until count) {
            val left = latest.getOrElse(index) { 0 }
            val right = current.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    fun numbers(raw: String): List<Int> {
        return Regex("\\d+").findAll(raw).map { it.value.toInt() }.toList()
    }
}

sealed class UpdateLookup {
    data class UpToDate(val latest: String) : UpdateLookup()
    data class Newer(val version: String) : UpdateLookup()
    data class Failed(val message: String) : UpdateLookup()
}
