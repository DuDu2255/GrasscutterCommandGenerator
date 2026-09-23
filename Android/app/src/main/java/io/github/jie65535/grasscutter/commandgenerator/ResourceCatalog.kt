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

    fun idForGood(kind: String, key: String): String? {
        val file = when (kind) {
            "给予物品" -> "upstream/en-us/Item.txt"
            "给予角色" -> "upstream/en-us/Avatar.txt"
            "给予武器" -> "upstream/en-us/Weapon.txt"
            else -> return null
        }
        val needle = key.filter(Char::isLetterOrDigit).lowercase()
        return runCatching {
            context.assets.open(file).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2 && parts[1].filter(Char::isLetterOrDigit).lowercase() == needle) parts[0] else null
                }.firstOrNull()
            }
        }.getOrNull()
    }

    fun customCommands(): List<CatalogEntry> = cache.getOrPut("自定义") {
        runCatching {
            context.assets.open("upstream/zh-cn/CustomCommands.txt").bufferedReader().readLines()
                .chunked(2).mapNotNull { pair ->
                    if (pair.size == 2 && pair[1].startsWith('/')) CatalogEntry(pair[1], pair[0]) else null
                }
        }.getOrDefault(emptyList())
    }

    private fun load(kind: String): List<CatalogEntry> {
        val file = when (kind) {
            "给予物品", "给予圣遗物", "生成物品" -> "upstream/zh-cn/Item.txt"
            "给予角色" -> "upstream/zh-cn/Avatar.txt"
            "给予武器" -> "upstream/zh-cn/Weapon.txt"
            "生成怪物" -> "upstream/zh-cn/Monsters.txt"
            "场景" -> "upstream/zh-cn/Scene.txt"
            "地城" -> "upstream/zh-cn/Dungeon.txt"
            "过场动画" -> "upstream/zh-cn/Cutscene.txt"
            "天气" -> "upstream/zh-cn/Weather.txt"
            "任务" -> "upstream/zh-cn/Quest.txt"
            "成就" -> "upstream/zh-cn/Achievement.txt"
            "活动" -> "upstream/zh-cn/Activity.txt"
            "祈愿预设" -> "upstream/zh-cn/GachaBannerPrefab.txt"
            "祈愿标题" -> "upstream/zh-cn/GachaBannerTitle.txt"
            "商店" -> "upstream/zh-cn/ShopType.txt"
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
