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
        json.optJSONArray("artifacts")?.let { artifacts ->
            for (i in 0 until artifacts.length()) {
                val item = artifacts.getJSONObject(i)
                val setId = catalog.idForGood("给予圣遗物套装", item.optString("setKey")) ?: continue
                val slot = when (item.optString("slotKey")) {
                    "flower" -> 10
                    "plume" -> 20
                    "sands" -> 30
                    "goblet" -> 40
                    "circlet" -> 50
                    else -> continue
                }
                val artifactId = setId.toInt() * 1000 + item.optInt("rarity", 5) * 100 + slot
                val main = item.optString("mainStatKey").takeIf { it.isNotBlank() }?.let { artifactMainStatId(it) }
                val subStats = item.optJSONArray("substats")?.let { stats ->
                    buildList {
                        for (j in 0 until stats.length()) {
                            val stat = stats.getJSONObject(j)
                            artifactSubStatId(stat.optString("key"))?.let { add(it) }
                        }
                    }.joinToString(" ")
                }.orEmpty()
                commands += "/give $artifactId lv${item.optInt("level", 0)}" + main?.let { " $it" }.orEmpty() + subStats.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            }
        }
        json.optJSONObject("materials")?.keys()?.forEach { key ->
            val id = catalog.idForGood("给予物品", key) ?: return@forEach
            commands += "/give $id ${json.getJSONObject("materials").optInt(key)}"
        }
        return commands
    }

    private fun artifactMainStatId(key: String): Int? = mapOf("hp" to 10001, "hp_" to 10002, "atk" to 10003, "atk_" to 10004, "def" to 10005, "def_" to 10006, "enerRech_" to 10007, "eleMas" to 10008, "critRate_" to 13007, "critDMG_" to 13008, "heal_" to 13009, "pyro_dmg_" to 15008, "electro_dmg_" to 15009, "cryo_dmg_" to 15010, "hydro_dmg_" to 15011, "anemo_dmg_" to 15012, "geo_dmg_" to 15013, "dendro_dmg_" to 15014, "physical_dmg_" to 15015)[key]

    private fun artifactSubStatId(key: String): Int? = mapOf("hp" to 102, "hp_" to 103, "atk" to 105, "atk_" to 106, "def" to 108, "def_" to 109, "critRate_" to 120, "critDMG_" to 122, "enerRech_" to 123, "eleMas" to 124)[key]
}
