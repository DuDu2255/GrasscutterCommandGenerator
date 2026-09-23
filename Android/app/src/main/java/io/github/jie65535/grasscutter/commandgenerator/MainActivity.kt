package io.github.jie65535.grasscutter.commandgenerator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.Composable
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
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CommandGeneratorApp() } }
    }
}

private data class CommandTemplate(
    val title: String,
    val fields: List<String>,
    val example: List<String>,
    val render: (List<String>) -> String,
)

private val templates = listOf(
    CommandTemplate("给予物品", listOf("物品 ID", "数量", "玩家 UID"), listOf("223", "1", "")) { v ->
        "/give ${v[0]} ${v[1]}" + v[2].takeIf { it.isNotBlank() }?.let { " @${it}" }.orEmpty()
    },
    CommandTemplate("给予角色", listOf("角色 ID", "等级 (1-90)", "玩家 UID"), listOf("10000007", "90", "")) { v ->
        "/givechar ${v[0]} ${v[1]}" + v[2].takeIf { it.isNotBlank() }?.let { " @${it}" }.orEmpty()
    },
    CommandTemplate("给予武器", listOf("武器 ID", "等级 (1-90)", "精炼 (1-5)", "玩家 UID"), listOf("11501", "90", "5", "")) { v ->
        "/give ${v[0]} 1 lv${v[1]} r${v[2]}" + v[3].takeIf { it.isNotBlank() }?.let { " @${it}" }.orEmpty()
    },
    CommandTemplate("给予圣遗物", listOf("圣遗物 ID", "数量", "玩家 UID"), listOf("15001", "1", "")) { v ->
        "/give ${v[0]} ${v[1]}" + v[2].takeIf { it.isNotBlank() }?.let { " @${it}" }.orEmpty()
    },
    CommandTemplate("传送", listOf("场景 ID", "X 坐标", "Y 坐标", "Z 坐标"), listOf("3", "0", "0", "0")) { v ->
        "/teleport ${v.joinToString(" ")}" 
    },
    CommandTemplate("天气", listOf("天气 ID", "场景 ID"), listOf("0", "3")) { v -> "/weather ${v[0]} ${v[1]}" },
    CommandTemplate("自定义", listOf("完整指令"), listOf("/help")) { v -> v[0] },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandGeneratorApp() {
    val context = LocalContext.current
    val store = remember { HistoryStore(context) }
    val connection = remember { OpenCommandSettings(context) }
    var host by remember { mutableStateOf(connection.host) }
    var token by remember { mutableStateOf(connection.token) }
    var playerId by remember { mutableStateOf(connection.playerId) }
    var verificationCode by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("未连接") }
    var connected by remember { mutableStateOf(connection.token.isNotBlank()) }
    var selectedTitle by rememberSaveable { mutableStateOf(templates.first().title) }
    var history by remember { mutableStateOf(store.load()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selected = templates.first { it.title == selectedTitle }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Grasscutter 指令生成器") },
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
                RemoteConnectionPanel(
                    host = host,
                    onHostChange = { host = it },
                    token = token,
                    onTokenChange = { token = it },
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
                )
            }
            item {
                Text("指令类型", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    templates.take(4).forEach { template ->
                        AssistChip(onClick = { selectedTitle = template.title }, label = { Text(template.title) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    templates.drop(4).forEach { template ->
                        AssistChip(onClick = { selectedTitle = template.title }, label = { Text(template.title) })
                    }
                }
            }
            item(key = selected.title) { CommandForm(selected, context, snackbar, scope, connected, host, token, onSaved = { command ->
                history = store.add(command)
            }) }
            item { HorizontalDivider() }
            item { Text("最近生成", style = MaterialTheme.typography.titleMedium) }
            items(history, key = { it }) { command ->
                HistoryRow(command, onCopy = { copy(context, command); scope.launch { snackbar.showSnackbar("已复制指令") } }, onDelete = {
                    history = store.remove(command)
                })
            }
        }
    }
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
                Button(enabled = host.isNotBlank() && playerId.isNotBlank(), onClick = {
                    scope.launch {
                        onStatus("验证码发送中...")
                        runCatching { OpenCommandClient(host).sendCode(playerId.toInt()) }
                            .onSuccess { onStatus("验证码已发送，请在游戏内查看") }
                            .onFailure { onStatus("发送失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("发送验证码") }
            }
            OutlinedTextField(verificationCode, onVerificationCodeChange, label = { Text("验证码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, onTokenChange, label = { Text("Token（可直接填写已保存 Token）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = host.isNotBlank() && verificationCode.isNotBlank(), onClick = {
                    scope.launch {
                        onStatus("验证中...")
                        runCatching { OpenCommandClient(host).verify(verificationCode.toInt()) }
                            .onSuccess { newToken -> onToken(newToken); onConnected(newToken); onStatus("OpenCommand 已连接") }
                            .onFailure { onStatus("验证失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("验证并连接") }
                Text(if (connected) "已连接" else status, color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
            if (status.isNotBlank() && !connected) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CommandForm(template: CommandTemplate, context: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, connected: Boolean, host: String, token: String, onSaved: (String) -> Unit) {
    var values by rememberSaveable(template.title) { mutableStateOf(template.example) }
    val catalog = remember { ResourceCatalog(context) }
    val command = template.render(values)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(template.title, style = MaterialTheme.typography.titleLarge)
            template.fields.forEachIndexed { index, label ->
                OutlinedTextField(
                    value = values[index],
                    onValueChange = { input -> values = values.toMutableList().also { it[index] = input } },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (template.title in setOf("给予物品", "给予角色", "给予武器", "给予圣遗物")) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    catalog.search(template.title, values.firstOrNull().orEmpty()).forEach { entry ->
                        AssistChip(
                            onClick = { values = values.toMutableList().also { it[0] = entry.id } },
                            label = { Text("${entry.id} ${entry.name}") },
                        )
                    }
                }
            }
            Text("生成结果", style = MaterialTheme.typography.labelLarge)
            Text(command, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
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
private fun HistoryRow(command: String, onCopy: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onCopy).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(command, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
        IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "复制") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Grasscutter command", text))
}

private class HistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("command_history", Context.MODE_PRIVATE)
    fun load(): List<String> = preferences.getStringSet("items", emptySet())!!.toList().take(20)
    fun add(command: String): List<String> = (listOf(command) + load().filter { it != command }).take(20).also { save(it) }
    fun remove(command: String): List<String> = load().filter { it != command }.also { save(it) }
    private fun save(items: List<String>) { preferences.edit().putStringSet("items", LinkedHashSet(items)).apply() }
}
