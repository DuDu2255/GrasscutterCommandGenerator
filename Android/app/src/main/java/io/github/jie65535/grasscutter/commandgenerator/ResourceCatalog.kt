package io.github.jie65535.grasscutter.commandgenerator

import android.content.Context

internal data class CatalogEntry(val id: String, val name: String)

internal class ResourceCatalog(private val context: Context) {
    private val cache = mutableMapOf<String, List<CatalogEntry>>()

    fun search(kind: String, query: String): List<CatalogEntry> {
        val entries = cache.getOrPut(kind) { load(kind) }
        if (query.isBlank()) return entries.take(8)
        return entries.filter { it.id.contains(query, true) || it.name.contains(query, true) }.take(8)
    }

    private fun load(kind: String): List<CatalogEntry> {
        val file = when (kind) {
            "给予物品", "给予圣遗物" -> "upstream/zh-cn/Item.txt"
            "给予角色" -> "upstream/zh-cn/Avatar.txt"
            "给予武器" -> "upstream/zh-cn/Weapon.txt"
            else -> return emptyList()
        }
        return runCatching {
            context.assets.open(file).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2 && parts[0].all(Char::isDigit)) CatalogEntry(parts[0], parts[1]) else null
                }.toList()
            }
        }.getOrDefault(emptyList())
    }
}
