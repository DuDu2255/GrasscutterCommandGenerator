package io.github.jie65535.grasscutter.commandgenerator

import android.content.Context
import android.net.Uri
import org.json.JSONObject

internal object GoodImporter {
    fun importCommands(context: Context, uri: Uri): List<String> {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { JSONObject(it.readText()) }
            ?: error("无法读取 GOOD 文件")
        val catalog = ResourceCatalog(context)
        val commands = mutableListOf<String>()
        json.optJSONArray("characters")?.let { characters ->
            for (i in 0 until characters.length()) {
                val item = characters.getJSONObject(i)
                val id = catalog.idForGood("给予角色", item.optString("key")) ?: continue
                val talents = item.optJSONObject("talent")
                val skill = talents?.let { minOf(it.optInt("auto"), it.optInt("skill"), it.optInt("burst")) } ?: 0
                commands += "/give $id lv${item.optInt("level", 1)} c${item.optInt("constellation", 0)} sl$skill"
            }
        }
        json.optJSONArray("weapons")?.let { weapons ->
            for (i in 0 until weapons.length()) {
                val item = weapons.getJSONObject(i)
                val id = catalog.idForGood("给予武器", item.optString("key")) ?: continue
                commands += "/give $id lv${item.optInt("level", 1)} r${item.optInt("refinement", 1)}"
            }
        }
        json.optJSONObject("materials")?.keys()?.forEach { key ->
            val id = catalog.idForGood("给予物品", key) ?: return@forEach
            commands += "/give $id ${json.getJSONObject("materials").optInt(key)}"
        }
        return commands
    }
}
