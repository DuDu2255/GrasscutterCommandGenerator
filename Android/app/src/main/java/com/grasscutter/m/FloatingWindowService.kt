package com.grasscutter.m

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
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
    private var expandedView: View? = null
    private var iconView: View? = null
    private var compact = true
    private var minimized = false
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
            windowWidth(),
            windowHeight(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(96) }
        expandedView = buildView()
        overlay = expandedView
        windowManager.addView(overlay, layoutParams)
    }

    private fun buildView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            // Keep the command controls readable while allowing the app behind the overlay to remain visible.
            setBackgroundColor(Color.argb(178, 245, 247, 250))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = if (compact) "指令悬浮窗 · 最小" else "指令悬浮窗"; textSize = 16f; setTextColor(Color.rgb(20, 30, 45)) }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        val mode = Button(this).apply { text = if (compact) "完整" else "最小"; setOnClickListener { compact = !compact; rebuild() } }
        header.addView(mode, LinearLayout.LayoutParams(dp(72), dp(42)))
        val close = Button(this).apply { text = "最小"; contentDescription = "最小化悬浮窗"; setOnClickListener { minimize() } }
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(42)))
        header.setOnTouchListener { _, event -> drag(event); true }
        root.addView(header)

        searchInput = EditText(this).apply {
            hint = "搜索命令名称"
            setSingleLine(true)
            setPadding(dp(8), 0, dp(8), 0)
            addTextChangedListener(SimpleTextWatcher { refreshResults() })
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(46)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, if (compact) dp(90) else dp(250)))
        commandInput = EditText(this).apply { hint = "指令（可直接输入，例如 /list）"; setSingleLine(false); minLines = 1; setTextColor(Color.DKGRAY) }
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
                val field = EditText(this).apply { hint = label; setText(selected!!.example.getOrNull(index).orEmpty()); setSingleLine(true) }
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
        expandedView = buildView()
        overlay = expandedView
        windowManager.addView(overlay, layoutParams.apply { width = windowWidth(); height = windowHeight() })
    }

    private fun minimize() {
        if (minimized || expandedView == null) return
        expandedView?.let { windowManager.removeView(it) }
        minimized = true
        if (iconView == null) iconView = buildIcon()
        overlay = iconView
        windowManager.addView(overlay, layoutParams.apply { width = windowWidth(); height = windowHeight() })
    }

    private fun restore() {
        if (!minimized || expandedView == null) return
        iconView?.let { windowManager.removeView(it) }
        minimized = false
        overlay = expandedView
        windowManager.addView(overlay, layoutParams.apply { width = windowWidth(); height = windowHeight() })
    }

    private fun buildIcon(): View = ImageView(this).apply {
        setImageResource(R.drawable.ic_launcher)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(150, 35, 93, 150))
        }
        alpha = 0.88f
        contentDescription = "展开 Grasscutter 指令悬浮窗"
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { drag(event); true }
                MotionEvent.ACTION_MOVE -> { drag(event); true }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - dragX) > dp(8) || abs(event.rawY - dragY) > dp(8)
                    drag(event)
                    if (!moved) restore()
                    true
                }
                else -> true
            }
        }
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
    private fun windowWidth() = dp(if (minimized) 44 else if (compact) 300 else 380)
    private fun windowHeight() = if (minimized) dp(44) else WindowManager.LayoutParams.WRAP_CONTENT

    override fun onDestroy() {
        overlay?.let { windowManager.removeView(it) }
        expandedView = null
        iconView = null
        serviceScope.coroutineContext.cancel()
        super.onDestroy()
    }
}

private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
