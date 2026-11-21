//
//	キーと候補を描画するIMEのビュー
//
// FontMetricsは以下を参照
// http://wikiwiki.jp/android/?%A5%C6%A5%AD%A5%B9%A5%C8%A4%CE%C9%C1%B2%E8%28FontMetrics%29
//

package com.pitecan.slime2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CandButton {
    var rect: Rect = Rect(0, 0, 0, 0)
    var text: String = ""
    var pat: String = ""
    var valid: Boolean = false
    var visible: Boolean = false
    var row: Int = 0
}

class KeyView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var keybg32x53: Bitmap? = null
    private var keyfg32x53: Bitmap? = null
    private var keybg53x53: Bitmap? = null
    private var keyfg53x53: Bitmap? = null
    private var keybg53x106: Bitmap? = null
    private var keyfg53x106: Bitmap? = null
    private var keybg106x53: Bitmap? = null
    private var keyfg106x53: Bitmap? = null

    private var keyPaint: Paint? = null
    private var smallKeyPaint: Paint? = null
    private var tinyKeyPaint: Paint? = null
    private var buttonPaint: Paint? = null
    private var buttonTextPaint: Paint? = null

    private val buttonMarginX = 5 // ボタン間の隙間
    private val buttonMarginY = 6
    private val buttonTextMargin = 5
    private val buttonHeight = 28
    private val buttonTextSize = 18
    private val largeKeyTextSize = 36
    private val smallKeyTextSize = 24
    private val tinyKeyTextSize = 16

    var expand: Float = 1.0f // 拡大率
    var bottomInset: Int = 0

    var keys: Keys? = null
    var keyController: KeyController? = null
    var candLines: Int = 0
    private var keypat: Array<Key>? = null
    private var selectedKey: Key? = null
    private var selectedKey2: Key? = null
    private var candPage: Int = 0

    private var bgcolor: Int = 0

    companion object {
        lateinit var candButtons: Array<CandButton>
    }

    init {
        // キー画像の読み込み
        val r = context.resources
        keybg53x53 = BitmapFactory.decodeResource(r, R.drawable.keybg53x53)
        keyfg53x53 = BitmapFactory.decodeResource(r, R.drawable.keyfg53x53)
        keybg53x106 = BitmapFactory.decodeResource(r, R.drawable.keybg53x106)
        keyfg53x106 = BitmapFactory.decodeResource(r, R.drawable.keyfg53x106)
        keybg106x53 = BitmapFactory.decodeResource(r, R.drawable.keybg106x53)
        keyfg106x53 = BitmapFactory.decodeResource(r, R.drawable.keyfg106x53)
        keybg32x53 = BitmapFactory.decodeResource(r, R.drawable.keybg32x53)
        keyfg32x53 = BitmapFactory.decodeResource(r, R.drawable.keyfg32x53)

        // 候補「ボタン」の初期化
        candButtons = Array(Slime.MAXCANDS) { CandButton() }
    }

    private fun initGraphics() {
        // キートップ色
        keyPaint = Paint().apply {
            isAntiAlias = true
            textSize = largeKeyTextSize * expand
            color = 0xff000000.toInt() // argb 黒
        }
        smallKeyPaint = Paint().apply {
            isAntiAlias = true
            textSize = smallKeyTextSize * expand
            color = 0xff000000.toInt() // 黒
        }
        tinyKeyPaint = Paint().apply {
            isAntiAlias = true
            textSize = tinyKeyTextSize * expand
            color = 0xff000000.toInt() // 黒
        }
        // 背景色
        buttonPaint = Paint().apply {
            color = 0xffc0c0c0.toInt()
        }
        // 候補ボタンのテキスト色
        buttonTextPaint = Paint().apply {
            isAntiAlias = true
            textSize = buttonTextSize * expand
            color = 0xff000000.toInt() // 黒
        }
    }

    // イベント処理はkeyControllerに丸投げする
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        keyController?.onTouchEvent(ev)
        return true
    }

    fun draw2(keypat: Array<Key>?, selectedKey: Key?, selectedKey2: Key?, candPage: Int) {
        this.keypat = keypat
        this.selectedKey = selectedKey
        this.selectedKey2 = selectedKey2
        this.candPage = candPage
        bgcolor = 0xffd0d0d0.toInt()
        invalidate()
    }

    fun draw(keypat: Array<Key>?, selectedKey: Key?, selectedKey2: Key?, candPage: Int) {
        this.keypat = keypat
        this.selectedKey = selectedKey
        this.selectedKey2 = selectedKey2
        this.candPage = candPage
        bgcolor = 0xfff0f0f0.toInt()
        invalidate()
    }

    fun drawDefault() {
        this.keypat = Keys.keypat0
        this.selectedKey = null
        this.selectedKey2 = null
        this.candPage = 1
        bgcolor = 0xfff0f0f0.toInt()
        invalidate()
    }

    //
    // 候補バッファを12行ぶん用意しておいて3行ずつ表示する
    //
    private fun layoutCandButtons() {
        var x: Float
        var y: Float
        var w: Float
        var h: Float
        var buttonIndex = 0
        val leftlimit = IntArray(12) { 0 }
        val rightlimit = intArrayOf(320, 268, 268, 320, 268, 268, 320, 268, 268, 320, 320, 320)
        val curright = IntArray(12) { leftlimit[it] + buttonMarginX }

        while (buttonIndex < candButtons.size) {
            val button = candButtons[buttonIndex]
            val s = button.text
            if (s.isEmpty()) break
            val bp = buttonTextPaint ?: break
            val textWidth = bp.measureText(s) / expand
            w = textWidth + (buttonMarginX * 2) // ボタン幅
            h = buttonHeight.toFloat() // ボタン高さ
            // 空いている候補領域に候補ボタンを詰める
            var i = 0
            while (i < 12) {
                if (curright[i] + w + buttonMarginX <= rightlimit[i]) {
                    x = curright[i].toFloat()
                    y = buttonMarginY + (i % 3) * (buttonHeight + buttonMarginY).toFloat()
                    button.rect.left = x.toInt()
                    button.rect.top = y.toInt()
                    button.rect.right = (x + w).toInt()
                    button.rect.bottom = (y + h).toInt()
                    button.row = i
                    button.valid = true
                    curright[i] += (w + buttonMarginX).toInt()
                    break
                }
                i++
            }
            if (i >= 12) break
            buttonIndex++
        }
        while (buttonIndex < candButtons.size) {
            val button = candButtons[buttonIndex]
            button.valid = false
            button.visible = false
            buttonIndex++
        }
        candLines = 0
        while (candLines < 12) {
            if (curright[candLines] == leftlimit[candLines] + buttonMarginX) break
            candLines++
        }
        while (buttonIndex < candButtons.size) {
            val button = candButtons[buttonIndex]
            button.valid = false
            buttonIndex++
        }
    }

    override fun onDraw(canvas: Canvas) {
        var image: Bitmap?
        val kpat = keypat ?: Keys.keypat0

        canvas.drawColor(bgcolor)
        // [Kotlinらしい改善] インデックスベースのforループを forEach に置き換え、可読性を向上
        kpat.forEach { key ->
            val width = key.rect.right - key.rect.left
            val height = key.rect.bottom - key.rect.top
            val paint = if (width <= 24) tinyKeyPaint else if (width <= 32) smallKeyPaint else keyPaint
            val p = paint ?: return@forEach
            val fontMetrics = p.fontMetrics
            val ascent = fontMetrics.ascent
            val descent = fontMetrics.descent
            val shadewidth = 6f

            image = if ((selectedKey != null && key.str == selectedKey?.str) ||
                (selectedKey2 != null && key.str == selectedKey2?.str)
            ) {
                if (width == 32) keyfg32x53 else if (height == 106) keyfg53x106 else keyfg53x53
            } else {
                if (width == 32) keybg32x53 else if (height == 106) keybg53x106 else keybg53x53
            }

            if (!(key.str == "→" && (candPage == 0 || candPage == 4))) {
                val left = (key.rect.left * expand).toInt()
                val top = (key.rect.top * expand).toInt()
                val right = (key.rect.right * expand).toInt()
                val bottom = (key.rect.bottom * expand).toInt()
                val dst = Rect(left, top, right, bottom)
                if (image != null) {
                    canvas.drawBitmap(image, null, dst, null)
                }

                //
                // キー文字描画
                //
                val textWidth = p.measureText(key.str)
                val baseX = left + (right - left - (shadewidth * expand) - textWidth) / 2
                val baseY = top + (bottom - top - (shadewidth * expand) - (ascent + descent)) / 2
                canvas.drawText(key.str, baseX, baseY, p)
            }
        }

        if (candPage > 0) {
            val btp = buttonTextPaint ?: return
            val fontMetrics = btp.fontMetrics
            val ascent = fontMetrics.ascent / expand
            val descent = fontMetrics.descent / expand
            layoutCandButtons()
            // [Kotlinらしい改善] インデックスベースのループを forEach に置き換え、可読性を向上
            candButtons.forEach { button ->
                button.visible = button.valid && ((candPage - 1) * 3 <= button.row && button.row < candPage * 3)
            }
            candButtons.forEach { button ->
                if (!button.visible) return@forEach
                val y = buttonMarginY + (button.row % 3) * (buttonHeight + buttonMarginY)
                val bp = buttonPaint ?: return@forEach
                canvas.drawRect(
                    (button.rect.left * expand),
                    (button.rect.top * expand),
                    (button.rect.right * expand),
                    ((y + button.rect.bottom - button.rect.top) * expand),
                    bp
                )
                canvas.drawText(
                    button.text,
                    (button.rect.left + buttonTextMargin) * expand,
                    (button.rect.top + (buttonHeight - (ascent + descent)) / 2) * expand,
                    btp
                )
            }
        }
    }

    // よくわからないがこれを設定するとViewの大きさを決められるようだ...
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val imeWidth = width
        expand = imeWidth.toFloat() / 320.0f
        setMeasuredDimension((320 * expand).toInt(), (216 * expand).toInt() + bottomInset)
        initGraphics()
    }
}
