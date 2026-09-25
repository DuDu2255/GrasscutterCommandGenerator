package com.grasscutter.m

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** System overlay for quick command work while Grasscutter or the game is foregrounded. */
class FloatingWindowService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var compact = false
    private var selected: CommandTemplate? = null
    private val fields = mutableListOf<EditText>()
    private lateinit var commandInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var content: LinearLayout
    private var dragX = 0f
    private var dragY = 0f
    private var startX = 0
    private var startY = 0
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlay == null) showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = WindowManager.LayoutParams(
            dp(if (compact) 300 else 380),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(96) }
        overlay = buildView()
        windowManager.addView(overlay, layoutParams)
    }

    private fun buildView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = if (compact) "指令悬浮窗 · 最小" else "指令悬浮窗"; textSize = 16f; setTextColor(Color.rgb(20, 30, 45)) }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        val mode = Button(this).apply { text = if (compact) "完整" else "最小"; setOnClickListener { compact = !compact; rebuild() } }
        header.addView(mode, LinearLayout.LayoutParams(dp(72), dp(42)))
        val close = Button(this).apply { text = "×"; setOnClickListener { stopSelf() } }
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(42)))
        header.setOnTouchListener { _, event -> drag(event); true }
        root.addView(header)

        searchInput = EditText(this).apply {
            hint = "搜索命令名称"
            singleLine = true
            setPadding(dp(8), 0, dp(8), 0)
            addTextChangedListener(SimpleTextWatcher { refreshResults() })
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(46)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, if (compact) dp(90) else dp(250)))
        commandInput = EditText(this).apply { hint = "指令（可直接输入，例如 /list）"; singleLine = false; minLines = 1; setTextColor(Color.DKGRAY) }
        root.addView(commandInput, LinearLayout.LayoutParams(-1, dp(54)))
        val send = Button(this).apply { text = "发送指令"; setOnClickListener { sendCommand(commandInput.text.toString()) } }
        root.addView(send, LinearLayout.LayoutParams(-1, dp(46)))
        refreshResults()
        return root
    }

    private fun refreshResults() {
        if (!::content.isInitialized) return
        content.removeAllViews(); fields.clear()
        val query = searchInput.text.toString().trim()
        templates.filter { query.isBlank() || it.title.contains(query, true) }.take(if (compact) 8 else 40).forEach { template ->
            val button = Button(this).apply {
                text = template.title
                setOnClickListener { selectTemplate(template) }
            }
            content.addView(button, LinearLayout.LayoutParams(-1, dp(42)))
        }
        if (!compact && selected != null) {
            selected!!.fields.forEachIndexed { index, label ->
                val field = EditText(this).apply { hint = label; setText(selected!!.example.getOrNull(index).orEmpty()); singleLine = true }
                fields += field; content.addView(field, LinearLayout.LayoutParams(-1, dp(44)))
            }
            val render = Button(this).apply { text = "生成 ${selected!!.title}"; setOnClickListener { commandInput.setText(selected!!.render(fields.map { it.text.toString() })) } }
            content.addView(render, LinearLayout.LayoutParams(-1, dp(42)))
        }
    }

    private fun selectTemplate(template: CommandTemplate) { selected = template; refreshResults() }

    private fun sendCommand(command: String) {
        val value = command.trim()
        if (value.isBlank()) { toast("请输入或生成指令"); return }
        val settings = OpenCommandSettings(this)
        if (settings.token.isBlank()) { toast("请先在主界面设置并验证连接"); return }
        serviceScope.launch {
            runCatching { OpenCommandClient(settings.host).invoke(settings.token, value) }
                .onSuccess { toast("指令已发送") }
                .onFailure { toast("发送失败：${it.message ?: "服务器不可达"}") }
        }
    }

    private fun rebuild() {
        overlay?.let { windowManager.removeView(it) }
        overlay = buildView(); windowManager.addView(overlay, layoutParams.apply { width = dp(if (compact) 300 else 380) })
    }

    private fun drag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragX = event.rawX; dragY = event.rawY; startX = layoutParams.x; startY = layoutParams.y }
            MotionEvent.ACTION_MOVE -> { layoutParams.x = startX - (event.rawX - dragX).toInt(); layoutParams.y = startY + (event.rawY - dragY).toInt(); windowManager.updateViewLayout(overlay, layoutParams) }
        }
        return true
    }

    private fun toast(message: String) { serviceScope.launch { Toast.makeText(this@FloatingWindowService, message, Toast.LENGTH_SHORT).show() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() { overlay?.let { windowManager.removeView(it) }; serviceScope.coroutineContext.cancel(); super.onDestroy() }
}

private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
