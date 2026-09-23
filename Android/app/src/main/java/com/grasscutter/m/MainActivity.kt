package com.grasscutter.m

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CommandGeneratorApp() }
    }
}

private data class CommandTemplate(
    val title: String,
    val fields: List<String>,
    val example: List<String>,
    val render: (List<String>) -> String,
)

private val templates = listOf(
    CommandTemplate("给予物品", listOf("物品 ID", "数量", "等级", "玩家 UID"), listOf("223", "1", "", "")) { v ->
        "/give ${v[0]} x${v[1]}" + v[2].takeIf { it.isNotBlank() }?.let { " lv$it" }.orEmpty() + v[3].takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
    },
    CommandTemplate("掉落物品", listOf("物品 ID", "数量"), listOf("223", "1")) { v ->
        "/drop ${v[0]} ${v[1]}"
    },
    CommandTemplate("给予角色", listOf("角色 ID", "等级 (1-90)", "命座 (0-6)", "技能等级", "玩家 UID"), listOf("10000007", "90", "0", "", "")) { v ->
        "/give ${v[0]} lv${v[1]} c${v[2]}" + v[3].takeIf { it.isNotBlank() }?.let { " sl$it" }.orEmpty() + v[4].takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
    },
    CommandTemplate("给予角色（兼容）", listOf("角色 ID", "等级 (1-90)"), listOf("10000007", "90")) { v ->
        "/givechar ${v[0]} ${v[1]}"
    },
    CommandTemplate("给予武器", listOf("武器 ID", "数量", "等级 (1-90)", "精炼 (1-5)", "玩家 UID"), listOf("11501", "1", "90", "1", "")) { v ->
        "/give ${v[0]} x${v[1]} lv${v[2]} r${v[3]}" + v[4].takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
    },
    CommandTemplate("给予圣遗物", listOf("圣遗物 ID", "等级 (0-20)", "主属性 ID（可选）", "副属性 ID 列表（可选）", "玩家 UID"), listOf("15001", "20", "", "", "")) { v ->
        val level = v[1].toIntOrNull()?.coerceIn(0, 20) ?: 0
        val subStats = v[3].trim().split(Regex("[;\\s]+")).mapNotNull { token ->
            val parts = token.split(',')
            when {
                parts.size == 2 && parts[0].toIntOrNull() != null && parts[1].toIntOrNull() != null -> "${parts[0]},${parts[1]}"
                parts.size == 1 && parts[0].toIntOrNull() != null -> parts[0]
                else -> null
            }
        }.distinct().joinToString(" ")
        "/give ${v[0].trim()} lv$level" + v[2].trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty() + subStats.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty() + v[4].trim().takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
    },
    CommandTemplate("生成怪物", listOf("怪物 ID", "数量", "等级"), listOf("20010101", "1", "1")) { v ->
        "/spawn ${v[0]} ${v[1]} ${v[2]}"
    },
    CommandTemplate("生成物品", listOf("物品 ID", "数量", "等级"), listOf("223", "1", "1")) { v ->
        "/spawn ${v[0]} x${v[1]} lv${v[2]}"
    },
    CommandTemplate("传送", listOf("场景 ID", "X 坐标", "Y 坐标", "Z 坐标"), listOf("3", "0", "0", "0")) { v ->
        "/tp ${v[1]} ${v[2]} ${v[3]} ${v[0]}"
    },
    CommandTemplate("场景", listOf("场景 ID"), listOf("3")) { v -> "/scene ${v[0]}" },
    CommandTemplate("地城", listOf("地城 ID"), listOf("")) { v -> "/dungeon ${v[0]}" },
    CommandTemplate("过场动画", listOf("过场 ID"), listOf("")) { v -> "/cutscene ${v[0]}" },
    CommandTemplate("天气", listOf("天气 ID"), listOf("1")) { v -> "/weather ${v[0]}" },
    CommandTemplate("任务", listOf("操作 add/finish", "任务 ID"), listOf("add", "")) { v -> "/quest ${v[0]} ${v[1]}" },
    CommandTemplate("成就", listOf("操作 grant/revoke", "成就 ID"), listOf("grant", "")) { v -> "/achievement ${v[0]} ${v[1]}" },
    CommandTemplate("成就全部", listOf("操作 grantall/revokeall"), listOf("grantall")) { v -> "/achievement ${v[0]}" },
    CommandTemplate("成就进度", listOf("成就 ID", "进度"), listOf("", "1")) { v -> "/achievement progress ${v[0]} ${v[1]}" },
    CommandTemplate("设置属性", listOf("属性名", "数值"), listOf("worldlevel", "8")) { v -> "/prop ${v[0]} ${v[1]}" },
    CommandTemplate("世界等级", listOf("世界等级"), listOf("8")) { v -> "/prop worldlevel ${v[0]}" },
    CommandTemplate("深境螺旋等级", listOf("等级"), listOf("12")) { v -> "/prop towerlevel ${v[0]}" },
    CommandTemplate("开放状态", listOf("操作 SetOpenState/UnsetOpenState", "状态 ID"), listOf("SetOpenState", "47")) { v -> "/prop ${v[0]} ${v[1]}" },
    CommandTemplate("锁定天气", listOf("on/off"), listOf("on")) { v -> "/prop is_weather_locked ${v[0]}" },
    CommandTemplate("锁定游戏时间", listOf("on/off"), listOf("on")) { v -> "/prop is_game_time_locked ${v[0]}" },
    CommandTemplate("解锁全部", emptyList(), emptyList()) { _ -> "/unlockall" },
    CommandTemplate("切换元素", listOf("元素 fire/water/wind/electric/ice/rock/grass"), listOf("fire")) { v -> "/se ${v[0]}" },
    CommandTemplate("天赋等级", listOf("天赋类型", "等级"), listOf("all", "10")) { v -> "/talent ${v[0]} ${v[1]}" },
    CommandTemplate("角色属性", listOf("属性名", "数值"), listOf("attack", "100")) { v -> "/setstats ${v[0]} ${v[1]}" },
    CommandTemplate("锁定角色属性", listOf("属性名", "数值"), listOf("attack", "100")) { v -> "/setstats lock ${v[0]} ${v[1]}" },
    CommandTemplate("解锁角色属性", listOf("属性名"), listOf("attack")) { v -> "/setstats unlock ${v[0]}" },
    CommandTemplate("设置命座", listOf("命座等级", "是否全部 all"), listOf("6", "")) { v -> "/setConst ${v[0]} ${v[1]}".trim() },
    CommandTemplate("重置命座", listOf("是否全部 all"), listOf("all")) { v -> "/resetConst ${v[0]}" },
    CommandTemplate("场景标签", listOf("操作 unlock/reset", "标签 ID"), listOf("unlock", "")) { v -> "/tag ${v[0]} ${v[1]}".trim() },
    CommandTemplate("权限管理", listOf("操作 grant/revoke/list/clear", "玩家 UID", "权限节点"), listOf("list", "", "")) { v -> "/permission ${v[0]} ${v[1].takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()} ${v[2]}".trim() },
    CommandTemplate("账号管理", listOf("操作 create/delete/rename", "用户名", "UID（可选）"), listOf("create", "", "")) { v -> "/account ${v[0]} ${v[1]} ${v[2]}".trim() },
    CommandTemplate("封禁玩家", listOf("玩家 UID", "Unix 解禁时间", "原因（可选）"), listOf("", "0", "")) { v -> "/ban @${v[0]} ${v[1]} ${v[2]}".trim() },
    CommandTemplate("解禁玩家", listOf("玩家 UID"), listOf("")) { v -> "/unban @${v[0]}" },
    CommandTemplate("发送邮件", listOf("收件人 UID 或 all", "标题", "内容", "发件人", "附件：物品ID 数量 等级"), listOf("all", "标题", "内容", "Grasscutter", "")) { v ->
        val attachments = v[4].lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" | ") { "/sendMail $it" }
        "/sendMail ${v[0]} | /sendMail ${v[1]} | /sendMail ${v[2].replace("\n", "\\n")} | /sendMail ${v[3]}" + attachments.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty() + " | /sendMail finish"
    },
    CommandTemplate("批量命令", listOf("每行一条命令"), listOf("/pos\n/list uid")) { v ->
        v[0].lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" | ")
    },
    CommandTemplate("自定义", listOf("完整指令"), listOf("/help")) { v -> v[0] },
)

private val templateGroups = listOf("全部", "物品角色", "世界场景", "任务成就", "玩家管理", "高级操作", "自定义")

private fun templateGroup(title: String): String = when (title) {
    "给予物品", "掉落物品", "给予角色", "给予角色（兼容）", "给予武器", "给予圣遗物", "生成物品", "生成怪物" -> "物品角色"
    "传送", "场景", "地城", "过场动画", "天气", "设置属性", "世界等级", "深境螺旋等级", "开放状态", "解锁全部" -> "世界场景"
    "任务", "成就", "成就全部", "成就进度" -> "任务成就"
    "权限管理", "账号管理", "封禁玩家", "解禁玩家", "发送邮件" -> "玩家管理"
    "切换元素", "天赋等级", "角色属性", "锁定角色属性", "解锁角色属性", "设置命座", "重置命座", "场景标签" -> "高级操作"
    "自定义", "批量命令" -> "自定义"
    else -> "全部"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandGeneratorApp() {
    val context = LocalContext.current
    val store = remember { HistoryStore(context) }
    val appPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(appPreferences.getString("language", "zh-cn") ?: "zh-cn") }
    var darkTheme by remember { mutableStateOf(appPreferences.getBoolean("dark_theme", false)) }
    val connection = remember { OpenCommandSettings(context) }
    var host by remember { mutableStateOf(connection.host) }
    var token by remember { mutableStateOf(connection.token) }
    var playerId by remember { mutableStateOf(connection.playerId) }
    var verificationCode by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("未连接") }
    var connected by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(store.load()) }
    var goodStatus by remember { mutableStateOf("") }
    val historyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(JSONArray(history).toString(2)) }
        }
    }
    val historyImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val array = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { JSONArray(it.readText()) } ?: JSONArray()
            val imported = List(array.length()) { array.getString(it) }
            history = store.replace(imported)
        }
    }
    val settingsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            val settings = JSONObject().put("host", host).put("token", token).put("playerId", playerId).put("language", language).put("darkTheme", darkTheme)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(settings.toString(2)) }
        }
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val settings = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            host = settings.optString("host", host)
            token = settings.optString("token", token)
            playerId = settings.optString("playerId", playerId)
            language = settings.optString("language", language)
            darkTheme = settings.optBoolean("darkTheme", darkTheme)
            appPreferences.edit().putString("language", language).putBoolean("dark_theme", darkTheme).apply()
        }
    }
    var jsonName by remember { mutableStateOf("config.json") }
    var jsonText by remember { mutableStateOf("") }
    var jsonStatus by remember { mutableStateOf("") }
    val jsonSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            JSONTokener(jsonText).nextValue()
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(jsonText) }
            jsonStatus = "JSON 已保存"
        }.onFailure { jsonStatus = "保存失败：${it.message ?: "JSON 格式错误"}" }
    }
    val jsonOpenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) jsonName = cursor.getString(0)
            }
            if (!jsonName.endsWith(".json", ignoreCase = true)) jsonName += ".json"
            JSONTokener(jsonText).nextValue()
            jsonStatus = "已打开并通过 JSON 校验"
        }.onFailure { jsonStatus = "打开失败：${it.message ?: "JSON 格式错误"}" }
    }
    val goodLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { GoodImporter.importCommands(context, uri) }
                .onSuccess { commands -> commands.forEach { history = store.add(it) }; goodStatus = "GOOD 导入成功：生成 ${commands.size} 条命令" }
                .onFailure { goodStatus = "GOOD 导入失败：${it.message ?: "文件格式错误"}" }
        }
    }
    var selectedTitle by rememberSaveable { mutableStateOf(templates.first().title) }
    var selectedGroup by rememberSaveable { mutableStateOf("全部") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val visibleTemplates = templates.filter { selectedGroup == "全部" || templateGroup(it.title) == selectedGroup }
    val selected = visibleTemplates.firstOrNull { it.title == selectedTitle } ?: visibleTemplates.first()
    LaunchedEffect(Unit) {
        // Validate only the token loaded from preferences. A temporary sendCode token
        // must remain available for verify and must not mark the session connected.
        val savedToken = token
        if (savedToken.isNotBlank() && host.isNotBlank()) {
            runCatching { OpenCommandClient(host).ping(savedToken) }
                .onSuccess { connected = true; connectionStatus = "已连接" }
                .onFailure { connected = false; connectionStatus = "Token 已失效或服务器不可达" }
        }
    }
    LaunchedEffect(selectedGroup) {
        if (selectedTitle !in visibleTemplates.map { it.title }) selectedTitle = selected.title
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("7.0指令生成器") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("资源语言：$language", modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            language = listOf("zh-cn", "zh-tw", "en-us", "ru-ru").let { it[(it.indexOf(language) + 1) % it.size] }
                            appPreferences.edit().putString("language", language).apply()
                        }) { Text("切换语言") }
                        TextButton(onClick = {
                            darkTheme = !darkTheme
                            appPreferences.edit().putBoolean("dark_theme", darkTheme).apply()
                        }) { Text(if (darkTheme) "浅色主题" else "深色主题") }
                        TextButton(onClick = { settingsImportLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("导入设置") }
                        TextButton(onClick = { settingsExportLauncher.launch("grasscutter-settings.json") }) { Text("导出设置") }
                    }
                }
            }
            item {
                ScrollableTabRow(selectedTabIndex = templateGroups.indexOf(selectedGroup).coerceAtLeast(0)) {
                    templateGroups.forEach { group ->
                        Tab(selected = selectedGroup == group, onClick = { selectedGroup = group }, text = { Text(group) })
                    }
                }
            }
            item {
                Button(onClick = { goodLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                    Text("导入 GOOD 存档")
                }
                if (goodStatus.isNotBlank()) Text(goodStatus, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("JSON 配置编辑器", style = MaterialTheme.typography.titleLarge)
                        Text("用于 banners.json、商店和活动配置等桌面端资源文件", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { jsonOpenLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("打开 JSON") }
                            Button(enabled = jsonText.isNotBlank(), onClick = { jsonSaveLauncher.launch(jsonName) }) { Text("另存为") }
                            TextButton(enabled = jsonText.isNotBlank(), onClick = {
                                runCatching {
                                    val parsed = JSONTokener(jsonText).nextValue()
                                    jsonText = when (parsed) {
                                        is JSONObject -> parsed.toString(2)
                                        is JSONArray -> parsed.toString(2)
                                        else -> error("JSON 根节点必须是对象或数组")
                                    }
                                    jsonStatus = "JSON 已格式化"
                                }.onFailure { jsonStatus = "格式化失败：${it.message ?: "JSON 格式错误"}" }
                            }) { Text("格式化") }
                        }
                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = { jsonText = it },
                            label = { Text(jsonName) },
                            minLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (jsonStatus.isNotBlank()) Text(jsonStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                GachaBannerEditor(context, language)
            }
            item {
                RemoteConnectionPanel(
                    host = host,
                    onHostChange = { host = it; connected = false },
                    token = token,
                    onTokenChange = { token = it; connected = false },
                    playerId = playerId,
                    onPlayerIdChange = { playerId = it },
                    verificationCode = verificationCode,
                    onVerificationCodeChange = { verificationCode = it },
                    status = connectionStatus,
                    connected = connected,
                    scope = scope,
                    onStatus = { connectionStatus = it },
                    onConnected = { newToken ->
                        connected = true
                        connection.save(host.trim(), newToken.trim(), playerId)
                    },
                    onToken = { token = it },
                    onDisconnected = {
                        token = ""
                        connected = false
                        connection.save(host.trim(), "", playerId)
                        connectionStatus = "已断开"
                    },
                    onSave = {
                        connection.save(host.trim(), token.trim(), playerId.trim())
                        connectionStatus = "配置已保存"
                    },
                )
            }
            item {
                Text("指令类型", style = MaterialTheme.typography.titleMedium)
                visibleTemplates.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        row.forEach { template ->
                            AssistChip(onClick = { selectedTitle = template.title }, label = { Text(template.title) })
                        }
                    }
                }
            }
            item(key = selected.title + language) { CommandForm(selected, context, snackbar, scope, connected, host, token, language, onSaved = { command ->
                history = store.add(command)
            }) }
            item { HorizontalDivider() }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("最近生成", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { historyImportLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("导入") }
                        TextButton(onClick = { historyExportLauncher.launch("grasscutter-command-history.json") }) { Text("导出") }
                        TextButton(onClick = { history = store.clear() }) { Text("清空历史") }
                    }
                }
            }
            items(history, key = { it }) { command ->
                HistoryRow(command, connected, host, token, scope, snackbar, onCopy = { copy(context, command); scope.launch { snackbar.showSnackbar("已复制指令") } }, onDelete = {
                    history = store.remove(command)
                })
            }
        }
    }
    }
}

@Composable
private fun GachaBannerEditor(context: Context, language: String) {
    var bannersText by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var itemQuery by rememberSaveable { mutableStateOf("") }
    var pathQuery by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var status by rememberSaveable { mutableStateOf("") }
    val catalog = remember(language) { ResourceCatalog(context, language) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            bannersText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            JSONArray(bannersText)
            selectedIndex = 0
            status = "已加载卡池文件"
        }.onFailure { status = "加载失败：${it.message ?: "JSON 格式错误"}" }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            val formatted = JSONArray(bannersText).toString(2)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(formatted) }
            status = "Banners.json 已保存"
        }.onFailure { status = "保存失败：${it.message ?: "JSON 格式错误"}" }
    }
    val array = remember(bannersText) { runCatching { JSONArray(bannersText) }.getOrNull() }
    val matchingIndexes = remember(array, query) {
        if (array == null) emptyList() else (0 until array.length()).filter { index ->
            val banner = array.optJSONObject(index) ?: return@filter false
            query.isBlank() || banner.optString("comment").contains(query, true) ||
                banner.optInt("gachaType", -1).toString().contains(query, true) ||
                banner.optInt("scheduleId", -1).toString().contains(query, true)
        }.take(30)
    }
    val selected = array?.optJSONObject(selectedIndex)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("卡池编辑器", style = MaterialTheme.typography.titleLarge)
            Text("编辑 Grasscutter 的 Banners.json，保留原始字段并支持常用卡池参数。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching {
                        bannersText = context.assets.open("upstream/Banners.json").bufferedReader().use { it.readText() }
                        selectedIndex = 0
                        status = "已加载内置 Banners.json"
                    }.onFailure { status = "内置资源不可用：${it.message}" }
                }) { Text("加载内置") }
                Button(onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("打开文件") }
                Button(enabled = array != null, onClick = { saveLauncher.launch("Banners.json") }) { Text("导出") }
            }
            if (array != null) {
                OutlinedTextField(query, { query = it }, label = { Text("搜索备注、卡池类型或计划 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val next = JSONObject().put("comment", "新卡池").put("gachaType", 301).put("scheduleId", 1)
                            .put("prefabPath", "GachaShowPanel_A007").put("titlePath", "UI_GACHA_SHOW_PANEL_A007_TITLE")
                            .put("costItemId", 223).put("endTime", 1924992000).put("sortId", 1)
                            .put("rateUpItems4", JSONArray()).put("rateUpItems5", JSONArray()).put("bannerType", "EVENT")
                        array.put(next)
                        bannersText = array.toString(2)
                        selectedIndex = array.length() - 1
                        status = "已添加新卡池"
                    }) { Text("新增卡池") }
                    Button(enabled = selected != null, onClick = {
                        array.remove(selectedIndex)
                        bannersText = array.toString(2)
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        status = "已删除卡池"
                    }) { Text("删除选中") }
                    Button(enabled = selected != null, onClick = {
                        selected?.let { source ->
                            val copy = JSONObject(source.toString())
                            copy.put("scheduleId", (array.length() + 1) * 100)
                            copy.put("comment", copy.optString("comment", "卡池") + " 副本")
                            array.put(copy)
                            bannersText = array.toString(2)
                            selectedIndex = array.length() - 1
                            status = "已复制卡池"
                        }
                    }) { Text("复制选中") }
                    Button(onClick = { status = validateGachaBanners(array) }) { Text("校验") }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    matchingIndexes.forEach { index ->
                        val banner = array.optJSONObject(index)
                        TextButton(onClick = { selectedIndex = index }, modifier = Modifier.fillMaxWidth()) {
                            Text("${if (index == selectedIndex) "▶ " else ""}${banner?.optString("comment", "未命名") ?: "未命名"}  type=${banner?.optInt("gachaType", 0)} schedule=${banner?.optInt("scheduleId", 0)}")
                        }
                    }
                }
                if (selected != null) {
                    GachaField("备注", selected.optString("comment"), "comment") { key, value ->
                        selected.put(key, value); bannersText = array.toString(2)
                    }
                    GachaField("卡池类型 ID", selected.optInt("gachaType", 0).toString(), "gachaType") { key, value ->
                        value.toIntOrNull()?.let { selected.put(key, it); bannersText = array.toString(2) }
                    }
                    GachaField("计划 ID", selected.optInt("scheduleId", 0).toString(), "scheduleId") { key, value ->
                        value.toIntOrNull()?.let { selected.put(key, it); bannersText = array.toString(2) }
                    }
                    OutlinedTextField(pathQuery, { pathQuery = it }, label = { Text("搜索祈愿预设或标题资源") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (pathQuery.isNotBlank()) {
                        catalog.search("祈愿预设", pathQuery).take(6).forEach { entry ->
                            TextButton(onClick = { selected.put("prefabPath", entry.id); bannersText = array.toString(2) }, modifier = Modifier.fillMaxWidth()) {
                                Text("预设：${entry.id}  ${entry.name}")
                            }
                        }
                        catalog.search("祈愿标题", pathQuery).take(6).forEach { entry ->
                            TextButton(onClick = { selected.put("titlePath", entry.id); bannersText = array.toString(2) }, modifier = Modifier.fillMaxWidth()) {
                                Text("标题：${entry.id}  ${entry.name}")
                            }
                        }
                    }
                    GachaField("Prefab 路径", selected.optString("prefabPath"), "prefabPath") { key, value -> selected.put(key, value); bannersText = array.toString(2) }
                    GachaField("标题路径", selected.optString("titlePath"), "titlePath") { key, value -> selected.put(key, value); bannersText = array.toString(2) }
                    GachaField("消耗道具 ID", selected.optInt("costItemId", selected.optInt("costItemId10", 223)).toString(), "costItemId") { key, value ->
                        value.toIntOrNull()?.let { selected.put(key, it); bannersText = array.toString(2) }
                    }
                    GachaField("十连消耗道具 ID", selected.optInt("costItemId10", selected.optInt("costItemId", 223)).toString(), "costItemId10") { key, value ->
                        value.toIntOrNull()?.let { selected.put(key, it); bannersText = array.toString(2) }
                    }
                    GachaField("单抽消耗数量", selected.optInt("coseItemAmount", 1).toString(), "coseItemAmount") { key, value -> value.toIntOrNull()?.coerceAtLeast(1)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("十连消耗数量", selected.optInt("coseItemAmount10", 10).toString(), "coseItemAmount10") { key, value -> value.toIntOrNull()?.coerceAtLeast(1)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("开始时间 Unix", selected.optInt("beginTime", 0).toString(), "beginTime") { key, value -> value.toIntOrNull()?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("结束时间 Unix", selected.optInt("endTime", 1924992000).toString(), "endTime") { key, value -> value.toIntOrNull()?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("抽卡次数限制", selected.optInt("gachaTimesLimit", Int.MAX_VALUE).toString(), "gachaTimesLimit") { key, value -> value.toIntOrNull()?.coerceAtLeast(0)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("UP 四星 ID（逗号分隔）", jsonIntArrayText(selected, "rateUpItems4"), "rateUpItems4") { key, value -> selected.put(key, parseIntArray(value)); bannersText = array.toString(2) }
                    GachaField("UP 五星 ID（逗号分隔）", jsonIntArrayText(selected, "rateUpItems5"), "rateUpItems5") { key, value -> selected.put(key, parseIntArray(value)); bannersText = array.toString(2) }
                    OutlinedTextField(itemQuery, { itemQuery = it }, label = { Text("搜索角色或武器名称/ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (itemQuery.isNotBlank()) {
                        val itemResults = (catalog.search("给予角色", itemQuery) + catalog.search("给予武器", itemQuery)).distinctBy { it.id }.take(8)
                        itemResults.forEach { entry ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("${entry.id}  ${entry.name}", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        appendUniqueInt(selected, "rateUpItems4", entry.id)
                                        bannersText = array.toString(2)
                                    }) { Text("加入四星 UP") }
                                    TextButton(onClick = {
                                        appendUniqueInt(selected, "rateUpItems5", entry.id)
                                        bannersText = array.toString(2)
                                    }) { Text("加入五星 UP") }
                                }
                            }
                        }
                        if (itemResults.isEmpty()) Text("没有找到匹配的角色或武器", style = MaterialTheme.typography.bodySmall)
                    }
                    GachaField("普通三星池 ID", jsonIntArrayText(selected, "fallbackItems3"), "fallbackItems3") { key, value -> selected.put(key, parseIntArray(value)); bannersText = array.toString(2) }
                    GachaField("普通四星角色池 ID", jsonIntArrayText(selected, "fallbackItems4Pool1"), "fallbackItems4Pool1") { key, value -> selected.put(key, parseIntArray(value)); bannersText = array.toString(2) }
                    GachaField("普通四星武器池 ID", jsonIntArrayText(selected, "fallbackItems4Pool2"), "fallbackItems4Pool2") { key, value -> selected.put(key, parseIntArray(value)); bannersText = array.toString(2) }
                    GachaField("普通五星角色池 ID", jsonIntArrayText(selected, "fallbackItems5Pool1"), "fallbackItems5Pool1") { key, value -> selected.put(key, parseIntArray(value)); bannersText = array.toString(2) }
                    GachaField("普通五星武器池 ID", jsonIntArrayText(selected, "fallbackItems5Pool2"), "fallbackItems5Pool2") { key, value -> selected.put(key, parseIntArray(value)); bannersText = array.toString(2) }
                    GachaField("四星权重 JSON", jsonMatrixText(selected, "weights4"), "weights4") { key, value -> parseMatrix(value)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("五星权重 JSON", jsonMatrixText(selected, "weights5"), "weights5") { key, value -> parseMatrix(value)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("四星奖池平衡权重 JSON", jsonMatrixText(selected, "poolBalanceWeights4"), "poolBalanceWeights4") { key, value -> parseMatrix(value)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("五星奖池平衡权重 JSON", jsonMatrixText(selected, "poolBalanceWeights5"), "poolBalanceWeights5") { key, value -> parseMatrix(value)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("四星 UP 概率", selected.optInt("eventChance4", 50).toString(), "eventChance4") { key, value -> value.toIntOrNull()?.coerceIn(0, 100)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("五星 UP 概率", selected.optInt("eventChance5", 50).toString(), "eventChance5") { key, value -> value.toIntOrNull()?.coerceIn(0, 100)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("自动从普通池移除 UP（true/false）", selected.optBoolean("autoStripRateUpFromFallback", true).toString(), "autoStripRateUpFromFallback") { key, value -> parseBoolean(value)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("移除命座满级角色（true/false）", selected.optBoolean("removeC6FromPool", false).toString(), "removeC6FromPool") { key, value -> parseBoolean(value)?.let { selected.put(key, it); bannersText = array.toString(2) } }
                    GachaField("卡池类型（STANDARD/EVENT/WEAPON）", selected.optString("bannerType", "EVENT"), "bannerType") { key, value -> selected.put(key, value.uppercase()); bannersText = array.toString(2) }
                }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GachaField(label: String, value: String, key: String, onChange: (String, String) -> Unit) {
    var current by remember(value, key) { mutableStateOf(value) }
    OutlinedTextField(current, { current = it; onChange(key, it) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

private fun jsonIntArrayText(objectValue: JSONObject, key: String): String {
    val values = objectValue.optJSONArray(key) ?: return ""
    return (0 until values.length()).map { values.optInt(it) }.joinToString(",")
}

private fun jsonMatrixText(objectValue: JSONObject, key: String): String = objectValue.optJSONArray(key)?.toString() ?: "[]"

private fun parseIntArray(value: String): JSONArray {
    val result = JSONArray()
    value.split(Regex("[,;\\s]+"))
        .mapNotNull { it.toIntOrNull() }
        .distinct()
        .forEach { result.put(it) }
    return result
}

private fun parseMatrix(value: String): JSONArray? = runCatching {
    val parsed = JSONTokener(value).nextValue()
    require(parsed is JSONArray)
    parsed
}.getOrNull()

private fun parseBoolean(value: String): Boolean? = when (value.trim().lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}

private fun appendUniqueInt(objectValue: JSONObject, key: String, rawId: String) {
    val id = rawId.toIntOrNull() ?: return
    val current = objectValue.optJSONArray(key) ?: JSONArray()
    if ((0 until current.length()).none { current.optInt(it) == id }) current.put(id)
    objectValue.put(key, current)
}

private fun validateGachaBanners(banners: JSONArray): String {
    if (banners.length() == 0) return "校验失败：卡池列表为空"
    val identities = mutableSetOf<String>()
    val problems = mutableListOf<String>()
    for (index in 0 until banners.length()) {
        val banner = banners.optJSONObject(index)
        if (banner == null) {
            problems += "第 ${index + 1} 项不是 JSON 对象"
            continue
        }
        val name = banner.optString("comment", "第 ${index + 1} 项")
        val gachaType = banner.optInt("gachaType", 0)
        val scheduleId = banner.optInt("scheduleId", 0)
        if (gachaType <= 0) problems += "$name：gachaType 无效"
        if (scheduleId <= 0) problems += "$name：scheduleId 无效"
        if (!identities.add("$gachaType:$scheduleId")) problems += "$name：类型和计划 ID 重复"
        if (banner.optString("prefabPath").isBlank()) problems += "$name：prefabPath 为空"
        if (banner.optString("titlePath").isBlank()) problems += "$name：titlePath 为空"
        val begin = banner.optLong("beginTime", 0)
        val end = banner.optLong("endTime", 0)
        if (end > 0 && end <= begin) problems += "$name：结束时间必须晚于开始时间"
        listOf("weights4", "weights5", "poolBalanceWeights4", "poolBalanceWeights5").forEach { key ->
            val weights = banner.optJSONArray(key) ?: return@forEach
            if ((0 until weights.length()).any { weights.optJSONArray(it)?.length() != 2 }) problems += "$name：$key 必须由 [次数, 权重] 组成"
        }
    }
    return if (problems.isEmpty()) "校验通过：${banners.length()} 个卡池" else "发现 ${problems.size} 个问题：\n${problems.take(8).joinToString("\n")}"
}

@Composable
private fun RemoteConnectionPanel(
    host: String,
    onHostChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    playerId: String,
    onPlayerIdChange: (String) -> Unit,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    status: String,
    connected: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    onStatus: (String) -> Unit,
    onConnected: (String) -> Unit,
    onToken: (String) -> Unit,
    onDisconnected: () -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("远程执行", style = MaterialTheme.typography.titleLarge)
            Text("需要服务器安装 gc-opencommand-plugin", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(host, onHostChange, label = { Text("服务器地址，例如 http://192.168.1.10:443") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(playerId, onPlayerIdChange, label = { Text("玩家 UID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        onStatus("检测中...")
                        runCatching { OpenCommandClient(host).ping() }
                            .onSuccess { version -> onStatus("已连接，插件版本 $version") }
                            .onFailure { onStatus("连接失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("检测服务器") }
                Button(enabled = host.isNotBlank(), onClick = {
                    scope.launch {
                        onStatus("查询状态中...")
                        runCatching { OpenCommandClient(host).serverStatus() }
                            .onSuccess { onStatus("服务器：$it") }
                            .onFailure { onStatus("状态查询失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("服务器状态") }
                Button(enabled = host.isNotBlank() && playerId.isNotBlank(), onClick = {
                    scope.launch {
                        onStatus("验证码发送中...")
                        val uid = playerId.trim().toIntOrNull()
                        if (uid == null) {
                            onStatus("发送失败：玩家 UID 必须是数字")
                            return@launch
                        }
                        runCatching { OpenCommandClient(host).sendCode(uid) }
                            .onSuccess { temporaryToken ->
                                if (temporaryToken.isBlank()) {
                                    onStatus("验证码已发送，但服务器没有返回临时 Token")
                                } else {
                                    onToken(temporaryToken)
                                    onStatus("验证码已发送，请在游戏内查看")
                                }
                            }
                            .onFailure { onStatus("发送失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("发送验证码") }
            }
            OutlinedTextField(verificationCode, onVerificationCodeChange, label = { Text("验证码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, onTokenChange, label = { Text("Token（可直接填写已保存 Token）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = host.isNotBlank() && verificationCode.isNotBlank() && token.isNotBlank(), onClick = {
                    scope.launch {
                        onStatus("验证中...")
                        val code = verificationCode.trim().toIntOrNull()
                        if (code == null) {
                            onStatus("验证失败：验证码必须是数字")
                            return@launch
                        }
                        runCatching { OpenCommandClient(host).verify(code, token) }
                            .onSuccess { returnedToken ->
                                val activeToken = returnedToken.ifBlank { token }
                                onToken(activeToken)
                                onConnected(activeToken)
                                onStatus("OpenCommand 已连接")
                            }
                            .onFailure { onStatus("验证失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("验证并连接") }
                TextButton(enabled = connected, onClick = onDisconnected) { Text("断开连接") }
                TextButton(onClick = onSave) { Text("保存配置") }
                Text(if (connected) "已连接" else status, color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
            if (status.isNotBlank() && !connected) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CommandForm(template: CommandTemplate, context: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, connected: Boolean, host: String, token: String, language: String, onSaved: (String) -> Unit) {
    var values by rememberSaveable(template.title) { mutableStateOf(template.example) }
    var searchQuery by rememberSaveable(template.title + "-search") { mutableStateOf("") }
    var substatQuery by rememberSaveable(template.title + "-substat-search") { mutableStateOf("") }
    val catalog = remember(language) { ResourceCatalog(context, language) }
    val command = template.render(values)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(template.title, style = MaterialTheme.typography.titleLarge)
            template.fields.forEachIndexed { index, label ->
                OutlinedTextField(
                    value = values[index],
                    onValueChange = { input -> values = values.toMutableList().also { it[index] = input } },
                    label = { Text(label) },
                    singleLine = template.title != "批量命令" && !(template.title == "发送邮件" && index == 4),
                    minLines = if (template.title == "批量命令" || (template.title == "发送邮件" && index == 4)) 4 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (template.title in setOf("给予物品", "掉落物品", "给予角色", "给予角色（兼容）", "给予武器", "给予圣遗物", "生成怪物", "生成物品", "场景", "地城", "过场动画", "天气", "任务", "成就", "设置属性")) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索名称或 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    val results = catalog.search(template.title, searchQuery)
                    results.forEach { entry ->
                        TextButton(
                            onClick = {
                                val idField = if (template.title == "任务" || template.title == "成就") 1 else 0
                                values = values.toMutableList().also { it[idField] = entry.id }
                                searchQuery = "${entry.id} ${entry.name}"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${entry.id}  ${entry.name}", modifier = Modifier.fillMaxWidth()) }
                    }
                    if (searchQuery.isNotBlank() && results.isEmpty()) {
                        Text("没有找到匹配的名称或 ID", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (template.title == "给予圣遗物") {
                    Text("主属性和副属性使用原仓库属性 ID，例如 13007", style = MaterialTheme.typography.bodySmall)
                    Text("主属性", style = MaterialTheme.typography.labelLarge)
                    catalog.search("给予圣遗物主属性", searchQuery).take(4).forEach { entry ->
                        TextButton(onClick = { values = values.toMutableList().also { it[2] = entry.id } }) { Text("${entry.id}  ${entry.name}") }
                    }
                    OutlinedTextField(substatQuery, { substatQuery = it }, label = { Text("搜索副属性") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    catalog.search("给予圣遗物副属性", substatQuery).take(4).forEach { entry ->
                        TextButton(onClick = {
                            val current = values[3].trim()
                            val updated = if (current.isBlank()) entry.id else "$current ${entry.id}"
                            values = values.toMutableList().also { it[3] = updated }
                        }) { Text("${entry.id}  ${entry.name}") }
                    }
                }
            }
            if (template.title == "自定义") {
                OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("搜索预设名称或命令") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(modifier = Modifier.fillMaxWidth()) {
                    catalog.customCommands().filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.id.contains(searchQuery, true) }.take(8).forEach { entry ->
                        TextButton(onClick = { values = listOf(entry.id) }) { Text(entry.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
            Text("生成结果", style = MaterialTheme.typography.labelLarge)
            Text(command, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            if (template.title == "给予圣遗物") {
                Text("等级范围 0-20；副属性可用空格、逗号或分号分隔。", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copy(context, command); onSaved(command); scope.launch { snackbar.showSnackbar("已复制指令") } }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.padding(2.dp)); Text("复制")
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, command) })
                }) { Icon(Icons.Default.Share, contentDescription = null); Text("分享") }
                TextButton(enabled = connected, onClick = {
                    scope.launch {
                        runCatching { OpenCommandClient(host).invoke(token, command) }
                            .onSuccess { snackbar.showSnackbar(it.ifBlank { "指令已发送" }) }
                            .onFailure { snackbar.showSnackbar("发送失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("发送到服务器") }
            }
        }
    }
}

@Composable
private fun HistoryRow(command: String, connected: Boolean, host: String, token: String, scope: kotlinx.coroutines.CoroutineScope, snackbar: SnackbarHostState, onCopy: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onCopy).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(command, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
        IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "复制") }
        IconButton(enabled = connected, onClick = {
            scope.launch {
                runCatching { OpenCommandClient(host).invoke(token, command) }
                    .onSuccess { snackbar.showSnackbar(it.ifBlank { "指令已发送" }) }
                    .onFailure { snackbar.showSnackbar("发送失败：${it.message ?: "未知错误"}") }
            }
        }) { Text("发送") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Grasscutter command", text))
}

private class HistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("command_history", Context.MODE_PRIVATE)
    fun load(): List<String> = runCatching {
        val stored = preferences.getString("items_json", null)
        if (stored == null) {
            val legacy = preferences.getStringSet("items", emptySet())?.toList().orEmpty()
            if (legacy.isNotEmpty()) save(legacy)
            return@runCatching legacy.take(20)
        }
        val array = JSONArray(stored)
        List(array.length()) { array.getString(it) }.take(20)
    }.getOrDefault(emptyList())
    fun add(command: String): List<String> = (listOf(command) + load().filter { it != command }).take(20).also { save(it) }
    fun remove(command: String): List<String> = load().filter { it != command }.also { save(it) }
    fun clear(): List<String> = emptyList<String>().also { save(it) }
    fun replace(items: List<String>): List<String> = items.filter { it.isNotBlank() }.distinct().take(20).also { save(it) }
    private fun save(items: List<String>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        preferences.edit().putString("items_json", array.toString()).remove("items").apply()
    }
}
