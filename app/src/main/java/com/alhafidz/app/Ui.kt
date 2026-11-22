package com.alhafidz.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Async {
    private val ex = Executors.newFixedThreadPool(2)
    private val h = Handler(Looper.getMainLooper())
    fun run(f: () -> Unit) {
        ex.execute {
            try { f() } catch (t: Throwable) { android.util.Log.e("Alhafidz", "async", t) }
        }
    }
    fun ui(f: () -> Unit) { h.post(f) }
}

/** Palet warna terang/gelap (mengikuti sistem). */
object P {
    var night = false
    fun init(c: Context) {
        night = when (Prefs.int(c, K.THEME, -1)) {
            0 -> false
            1 -> true
            else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
    private fun n(l: Long, d: Long): Int = (if (night) d else l).toInt()

    val bgTop get() = n(0xFFD6E4FF, 0xFF0B1226)
    val bgMid get() = n(0xFFE9F0FF, 0xFF0F1A33)
    val bgBot get() = n(0xFFF8F3FF, 0xFF131730)
    val card get() = n(0xD9FFFFFF, 0xE61A2440)
    val cardSolid get() = n(0xFFFFFFFF, 0xFF1A2440)
    val text get() = n(0xFF0F1B3D, 0xFFEAF0FF)
    val sub get() = n(0xFF5A6788, 0xFF9AA8C9)
    val line get() = n(0x14000000, 0x22FFFFFF)
    val chipSel get() = n(0xFFDCE8FF, 0xFF264A8F)
    val blue = 0xFF2F6BF0.toInt()
    val blue2 = 0xFF4C86FF.toInt()
    val blueDark = 0xFF1F4FD0.toInt()
    val green = 0xFF34C759.toInt()
    val danger = 0xFFE5484D.toInt()

    fun lvText(l: Int): Int = when (l) {
        0 -> n(0xFFD32F2F, 0xFFFF8A80)
        1 -> n(0xFF1E5AD6, 0xFF8FB3FF)
        else -> n(0xFF1B7F3B, 0xFF6FE39A)
    }
    fun lvBadge(l: Int): Int = when (l) {
        0 -> n(0xFFFDE7E7, 0x44FF5252)
        1 -> n(0xFFE3ECFF, 0x442F6BF0)
        else -> n(0xFFDDF5E3, 0x4427C45A)
    }
    fun lvCard(l: Int): Int = when (l) {
        0 -> n(0xFFFFE9E9, 0x44FF5252)
        1 -> n(0xFFE9F1FF, 0x442F6BF0)
        else -> n(0xFFE6F8EA, 0x4427C45A)
    }
    fun lvStroke(l: Int): Int = when (l) {
        0 -> n(0xFFF6C9C9, 0x66FF8A80)
        1 -> n(0xFFCADBFB, 0x668FB3FF)
        else -> n(0xFFC1E8CB, 0x666FE39A)
    }
    fun lvRing(l: Int): Int = when (l) {
        0 -> 0xFFE5484D.toInt()
        1 -> 0xFF2F6BF0.toInt()
        else -> 0xFF2DB551.toInt()
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Int): Float = v * resources.displayMetrics.density
private fun Context.px(v: Int): Int = if (v < 0) v else dp(v)

fun Context.lp(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(px(w), px(h), weight).also { it.setMargins(dp(l), dp(t), dp(r), dp(b)) }

fun shape(color: Int, r: Float, stroke: Int = 0, sw: Int = 0): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = r
    if (sw > 0) setStroke(sw, stroke)
}

fun grad(c1: Int, c2: Int, r: Float, o: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR): GradientDrawable =
    GradientDrawable(o, intArrayOf(c1, c2)).apply { cornerRadius = r }

fun oval(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

fun pageBg(): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(P.bgTop, P.bgMid, P.bgBot))

/** w: 0 normal, 1 medium, 2 bold */
fun Context.tv(t: CharSequence, sp: Float = 14f, color: Int = P.text, w: Int = 0): TextView = TextView(this).apply {
    text = t
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(color)
    typeface = when (w) {
        2 -> Typeface.DEFAULT_BOLD
        1 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.DEFAULT
    }
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.cardBg(r: Int = 22, color: Int = P.card): GradientDrawable = shape(color, dpf(r))

fun View.soft(z: Int = 2) {
    elevation = context.dpf(z)
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        outlineAmbientShadowColor = 0xFF5B7BD5.toInt()
        outlineSpotShadowColor = 0xFF5B7BD5.toInt()
    }
}

fun View.pressable(onClick: () -> Unit) {
    isClickable = true
    setOnClickListener { onClick() }
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.alpha = 0.7f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
        }
        false
    }
}

fun Context.toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

fun Context.input(hint: String, multi: Boolean = false): EditText = EditText(this).apply {
    this.hint = hint
    setHintTextColor(P.sub)
    setTextColor(P.text)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    background = cardBg(16, P.cardSolid)
    setPadding(dp(16), dp(14), dp(16), dp(14))
    if (multi) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; minLines = 3; gravity = Gravity.TOP }
    else { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS; setSingleLine() }
}

fun Context.primaryButton(label: String): TextView = tv(label, 16f, Color.WHITE, 2).apply {
    gravity = Gravity.CENTER
    background = grad(P.blue2, P.blueDark, dpf(18), GradientDrawable.Orientation.TOP_BOTTOM)
    setPadding(dp(16), dp(16), dp(16), dp(16))
    elevation = dpf(4)
}

fun Context.ghostButton(label: String): TextView = tv(label, 15f, P.blue, 2).apply {
    gravity = Gravity.CENTER
    background = cardBg(18, P.cardSolid)
    setPadding(dp(14), dp(14), dp(14), dp(14))
}

fun Context.sw(checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
    isChecked = checked
    val on = intArrayOf(android.R.attr.state_checked)
    thumbTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(Color.WHITE, 0xFFB7C0D4.toInt()))
    trackTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(P.green, 0x66808FA8))
    setOnCheckedChangeListener { _, v -> onChange(v) }
}

fun Context.badge(label: String, level: Int): TextView {
    val s = SpannableString("\u25CF  $label")
    s.setSpan(RelativeSizeSpan(0.7f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    s.setSpan(ForegroundColorSpan(P.lvText(level)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return tv(s, 12.5f, P.lvText(level), 2).apply {
        background = cardBg(14, P.lvBadge(level))
        setPadding(dp(10), dp(5), dp(12), dp(5))
    }
}

private val avColors = intArrayOf(0xFF6C8CF5.toInt(), 0xFF3DB6A4.toInt(), 0xFFF0A04B.toInt(), 0xFFE0709B.toInt(), 0xFF8E7CE0.toInt(), 0xFF59A869.toInt())

fun Context.avatar(m: Member?, size: Int): View {
    val f = FrameLayout(this)
    f.background = oval(avColors[((m?.id ?: 0L) % avColors.size).toInt()])
    f.clipToOutline = true
    val ini = (m?.name?.trim()?.firstOrNull()?.uppercaseChar() ?: '?').toString()
    val t = tv(ini, size * 0.42f, Color.WHITE, 2)
    f.addView(t, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    if (m?.avatar != null) {
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        f.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
        Thumbs.avatar(this, iv, m.avatar)
    }
    f.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    return f
}

fun Activity.confirm(title: String, msg: String, ok: String, onOk: () -> Unit) {
    AlertDialog.Builder(this).setTitle(title).setMessage(msg)
        .setPositiveButton(ok) { _, _ -> onOk() }.setNegativeButton("Batal", null).show()
}

fun Activity.choose(title: String, items: Array<String>, onPick: (Int) -> Unit) {
    AlertDialog.Builder(this).setTitle(title).setItems(items) { _, i -> onPick(i) }.show()
}

fun Activity.promptText(title: String, initial: String, onOk: (String) -> Unit) {
    val et = input("Nama")
    et.setText(initial)
    val box = FrameLayout(this)
    box.setPadding(dp(20), dp(8), dp(20), 0)
    box.addView(et)
    AlertDialog.Builder(this).setTitle(title).setView(box)
        .setPositiveButton("Simpan") { _, _ -> val s = et.text.toString().trim(); if (s.isNotEmpty()) onOk(s) }
        .setNegativeButton("Batal", null).show()
}

fun Activity.promptPassword(title: String, msg: String, confirm: Boolean, onOk: (CharArray) -> Unit) {
    val a = input("Kata sandi (min. 6 karakter)")
    a.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    val b = input("Ulangi kata sandi")
    b.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    val box = vbox()
    box.setPadding(dp(20), dp(8), dp(20), 0)
    box.addView(tv(msg, 13f, P.sub), lp(MATCH, WRAP, b = 10))
    box.addView(a, lp(MATCH, WRAP, b = 8))
    if (confirm) box.addView(b, lp(MATCH, WRAP))
    val dlg = AlertDialog.Builder(this).setTitle(title).setView(box)
        .setPositiveButton("Lanjut", null).setNegativeButton("Batal", null).create()
    dlg.show()
    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val p1 = a.text.toString()
        when {
            p1.length < 6 -> toast("Minimal 6 karakter")
            confirm && p1 != b.text.toString() -> toast("Kata sandi tidak sama")
            else -> { dlg.dismiss(); onOk(p1.toCharArray()) }
        }
    }
}

/** Cincin hitung-mundur dengan angka + satuan (hari/bulan/tahun). */
class RingView(c: Context) : View(c) {
    var progress = 1f
    var color = 0
    var track = 0
    var num = ""
    var unit = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = w * 0.11f
        val r = RectF(s / 2, s / 2, w - s / 2, h - s / 2)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s
        p.strokeCap = Paint.Cap.ROUND
        p.color = track
        cv.drawArc(r, 0f, 360f, false, p)
        p.color = color
        cv.drawArc(r, -90f, 360f * progress.coerceIn(0.02f, 1f), false, p)
        tp.color = color
        if (unit.isEmpty()) {
            tp.textSize = w * 0.42f
            cv.drawText(num, w / 2, h / 2 + tp.textSize * 0.35f, tp)
        } else {
            tp.textSize = if (num.length > 2) w * 0.26f else w * 0.32f
            cv.drawText(num, w / 2, h * 0.52f, tp)
            tp.textSize = w * 0.17f
            tp.typeface = Typeface.DEFAULT
            cv.drawText(unit, w / 2, h * 0.52f + w * 0.19f, tp)
            tp.typeface = Typeface.DEFAULT_BOLD
        }
    }
}

/** ImageView dengan pinch-zoom, geser, dan double-tap. */
class ZoomImageView(c: Context) : ImageView(c) {
    private val m = Matrix()
    private var base = 1f
    private var scale = 1f
    private val sd = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { zoom(d.scaleFactor, d.focusX, d.focusY); return true }
    })
    private val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            m.postTranslate(-dx, -dy); fix(); imageMatrix = m; return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > base * 1.05f) reset() else zoom(2.5f, e.x, e.y)
            return true
        }
    })

    init { scaleType = ScaleType.MATRIX }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); reset() }
    override fun setImageBitmap(bm: android.graphics.Bitmap?) { super.setImageBitmap(bm); reset() }

    private fun reset() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        base = minOf(width / dw, height / dh)
        scale = base
        m.reset()
        m.postScale(base, base)
        m.postTranslate((width - dw * base) / 2f, (height - dh * base) / 2f)
        imageMatrix = m
    }

    private fun zoom(f: Float, fx: Float, fy: Float) {
        val ns = (scale * f).coerceIn(base, base * 6f)
        val a = ns / scale
        m.postScale(a, a, fx, fy)
        scale = ns
        fix()
        imageMatrix = m
    }

    private fun fix() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(r)
        var dx = 0f
        var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else { if (r.left > 0) dx = -r.left; if (r.right < width) dx = width - r.right }
        if (r.height() <= height) dy = (height - r.height()) / 2f - r.top
        else { if (r.top > 0) dy = -r.top; if (r.bottom < height) dy = height - r.bottom }
        m.postTranslate(dx, dy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        sd.onTouchEvent(e)
        gd.onTouchEvent(e)
        return true
    }
}

/** Pesan error yang ramah (termasuk OutOfMemoryError yang bukan Exception). */
fun Throwable.msg(): String =
    if (this is OutOfMemoryError) "Memori tidak cukup (foto terlalu besar)" else (message ?: javaClass.simpleName)
