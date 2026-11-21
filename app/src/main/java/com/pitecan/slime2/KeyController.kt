//
//	キー操作に対するアクションメインルーチン
//
package com.pitecan.slime2

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.ArrayList
import java.util.regex.Pattern

// 候補に重みづけするための比較ルーチン
// [Kotlinらしい改善] Javaの冗長なComparator実装を、compareByビルダーを用いて簡潔に記述
class CandidateComparator : Comparator<Any> {
    override fun compare(o1: Any, o2: Any): Int {
        val c1 = o1 as Candidate
        val c2 = o2 as Candidate
        return compareBy<Candidate>({ it.weight }, { it.word.length }).compare(c1, c2)
    }
}

class KeyController {
    // これらはSlime.onCreateInputView()でセットされる
    lateinit var keyView: KeyView
    lateinit var keys: Keys
    lateinit var dict: LocalDict
    lateinit var sqlDict: SQLDict
    lateinit var search: Search
    lateinit var slime: Slime

    companion object {
        var keypat: Array<Key>? = null
        var candPage: Int = 1   // 候補の何ページ目か
    }

    private var selectedCand = -1
    private var downKey: Key? = null
    private var selectedKey: Key? = null
    private var secondKey: Key? = null

    private var searchTask: SearchTask? = null

    var inputPatArray = ArrayList<String>()
    var inputCharArray = ArrayList<String>()

    //
    // 座標からキー番号を計算
    // [Kotlinらしい改善] 手動のforループによる検索を、コレクションの標準拡張関数 find { } に置き換え
    //
    private fun findKey(keypat: Array<Key>?, x: Int, y: Int): Key? {
        return keypat?.find { it.rect.contains(x, y) }
    }

    //
    // 座標から候補ボタン番号を計算
    //
    private fun findCand(x: Int, y: Int): Int {
        for (i in KeyView.candButtons.indices) {
            val button = KeyView.candButtons[i]
            if (!button.visible) continue
            val rect = button.rect
            var xx = rect.left
            var yy = rect.top
            var ww = rect.right - rect.left
            var hh = rect.bottom - rect.top
            if (xx <= 5) {
                xx -= 5
                ww += 8
            } else {
                xx -= 2
                ww += 5
            }
            if (yy <= 6) {
                yy -= 6
                hh += 9
            } else {
                yy -= 3
                hh += 6
            }
            val extendedRect = Rect(xx, yy, xx + ww, yy + hh)
            if (extendedRect.contains(x, y)) return i
        }
        return -1
    }

    enum class Event { UP1, UP2, DOWN1, DOWN2, MOVE, SHIFTTIMER, SHIFTLOCKTIMER }
    enum class State { STATE0, STATE1, STATE2, STATE3, STATE4, STATE5, STATE6, STATE7, STATEC }

    private var state = State.STATE0

    private var mousex = 0f
    private var mousey = 0f
    private var mousex2 = 0f
    private var mousey2 = 0f
    private var downx = 0f
    private var downy = 0f

    val shiftTimeoutHandler = Handler(Looper.getMainLooper())
    var shiftTimeout: Runnable = Runnable {
        trans(Event.SHIFTTIMER)
    }
    private val SHIFTTIME = 180

    val shiftLockTimeoutHandler = Handler(Looper.getMainLooper())
    var shiftLockTimeout: Runnable = Runnable {
        trans(Event.SHIFTLOCKTIMER)
    }
    private val SHIFTLOCKTIME = 1500

    var googleDisplayed = false
    var useGoogle = true

    private val SWIPEWIDTH = 30

    //
    // 戻るキーが押されたとき
    //
    fun backKey() {
        if (state == State.STATE7) {
            reset()
        } else {
            slime.hide()
        }
    }

    //
    // タッチイベント処理
    //
    fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        val pointerCount = ev.pointerCount
        val actionIndex = ev.actionIndex
        val pointerId = ev.getPointerId(actionIndex)
        val pointerIndex = ev.findPointerIndex(pointerId)

        mousex = ev.getX(pointerIndex) / keyView.expand
        mousey = ev.getY(pointerIndex) / keyView.expand

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                mousex2 = mousex
                mousey2 = mousey
                if (pointerId == 0) {
                    trans(Event.DOWN1)
                } else {
                    trans(Event.DOWN2)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId == 0) {
                    trans(Event.UP1)
                } else {
                    trans(Event.UP2)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                mousex2 = mousex
                mousey2 = mousey
                if (pointerCount == 2) {
                    mousex = ev.getX(1) / keyView.expand
                    mousey = ev.getY(1) / keyView.expand
                }
                trans(Event.MOVE)
            }
        }
        return true
    }

    private fun trans(e: Event) {
        selectedKey = findKey(keypat, mousex.toInt(), mousey.toInt())
        when (state) {
            State.STATE0 -> {
                when (e) {
                    Event.DOWN1 -> {
                        downx = mousex
                        downy = mousey
                        downKey = findKey(keypat, downx.toInt(), downy.toInt())
                        if (downKey != null) {
                            if (downKey?.str == "→") {
                                candPage++
                                keyView.draw(keypat, downKey, null, candPage)
                            } else if (downKey?.str == "←" && candPage > 1) {
                                if (candPage > 1) candPage--
                                keyView.draw(keypat, downKey, null, candPage)
                            } else {
                                keyView.draw(keypat, downKey, null, 0)
                                shiftTimeout = Runnable {
                                    trans(Event.SHIFTTIMER)
                                }
                                shiftTimeoutHandler.postDelayed(shiftTimeout, SHIFTTIME.toLong())
                                state = State.STATE1
                            }
                        } else {
                            state = State.STATEC
                        }
                    }
                    Event.UP1 -> {
                        keyView.draw(keypat, null, null, candPage)
                    }
                    else -> {}
                }
            }
            State.STATEC -> {
                selectedCand = findCand(mousex.toInt(), mousey.toInt())
                when (e) {
                    Event.UP1 -> {
                        if (selectedCand >= 0) {
                            searchTask?.cancel(true)
                            fix(
                                KeyView.candButtons[selectedCand].text,
                                KeyView.candButtons[selectedCand].pat
                            )
                            searchTask = SearchTask(keyView, useGoogle, sqlDict, slime)
                            searchTask?.execute("", "")
                        } else {
                            if (mousex - downx > SWIPEWIDTH) {
                                slime.keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                                reset()
                            } else if (mousex - downx < -SWIPEWIDTH) {
                                slime.keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT)
                                reset()
                            }
                        }
                        LocalDict.exactMode = false
                        keypat = Keys.keypat0
                        if (selectedCand >= 0)
                            keyView.draw(keypat, null, null, 0)
                        else
                            keyView.draw(keypat, null, null, candPage)
                        state = State.STATE0
                    }
                    else -> {}
                }
            }
            State.STATE1 -> {
                when (e) {
                    Event.UP1 -> {
                        if (selectedKey != null) {
                            processKey(downKey)
                        }
                        keypat = Keys.keypat0
                        keyView.draw(keypat, null, null, candPage)
                        state = State.STATE0
                        shiftTimeoutHandler.removeCallbacks(shiftTimeout)
                    }
                    Event.MOVE -> {
                        if (Math.hypot((mousex - downx).toDouble(), (mousey - downy).toDouble()) >= 40.0) {
                            keypat = downKey?.shiftKeypat
                            downKey = findKey(keypat, downx.toInt(), downy.toInt())
                            keypat = downKey?.shiftKeypat
                            selectedKey = findKey(keypat, mousex.toInt(), mousey.toInt())
                            keyView.draw(keypat, selectedKey, null, 0)
                            state = State.STATE3
                            shiftTimeoutHandler.removeCallbacks(shiftTimeout)
                        }
                    }
                    Event.DOWN2 -> {
                        keypat = downKey?.shiftKeypat
                        downKey = findKey(keypat, downx.toInt(), downy.toInt())
                        selectedKey = findKey(keypat, mousex.toInt(), mousey.toInt())
                        secondKey = selectedKey
                        keyView.draw(keypat, downKey, selectedKey, 0)
                        state = State.STATE4
                        shiftTimeoutHandler.removeCallbacks(shiftTimeout)
                    }
                    Event.SHIFTTIMER -> {
                        keypat = downKey?.shiftKeypat
                        downKey = findKey(keypat, downx.toInt(), downy.toInt())
                        keyView.draw(keypat, downKey, null, 0)
                        state = State.STATE2
                        shiftTimeoutHandler.removeCallbacks(shiftTimeout)
                    }
                    else -> {}
                }
            }
            State.STATE2 -> {
                when (e) {
                    Event.UP1 -> {
                        if (selectedKey != null) {
                            processKey(selectedKey)
                        }
                        keypat = Keys.keypat0
                        keyView.draw(keypat, null, null, candPage)
                        state = State.STATE0
                    }
                    Event.DOWN2 -> {
                        keyView.draw(keypat, downKey, selectedKey, 0)
                        secondKey = selectedKey
                        if (secondKey != null && Pattern.matches("[a-zA-Z0-9]", secondKey?.str ?: "")) {
                            state = State.STATE6
                        } else {
                            state = State.STATE4
                        }
                    }
                    Event.MOVE -> {
                        if (Math.hypot((mousex - downx).toDouble(), (mousey - downy).toDouble()) >= 20.0) {
                            downKey = findKey(keypat, downx.toInt(), downy.toInt())
                            keypat = downKey?.shiftKeypat
                            selectedKey = findKey(keypat, mousex.toInt(), mousey.toInt())
                            keyView.draw(keypat, selectedKey, null, 0)
                            state = State.STATE3
                        }
                    }
                    else -> {}
                }
            }
            State.STATE3 -> {
                when (e) {
                    Event.UP1, Event.UP2 -> {
                        if (selectedKey != null) {
                            processKey(selectedKey)
                        }
                        if (selectedKey != null && Pattern.matches("[a-zA-Z0-9]", selectedKey?.str ?: "")) {
                            shiftLockTimeout = Runnable {
                                trans(Event.SHIFTLOCKTIMER)
                            }
                            shiftLockTimeoutHandler.postDelayed(shiftLockTimeout, SHIFTLOCKTIME.toLong())
                            keyView.draw(keypat, null, null, 0)
                            state = State.STATE7
                        } else {
                            keypat = Keys.keypat0
                            keyView.draw(keypat, null, null, candPage)
                            state = State.STATE0
                        }
                    }
                    Event.MOVE -> {
                        keyView.draw(keypat, selectedKey, null, 0)
                    }
                    else -> {}
                }
            }
            State.STATE4 -> {
                when (e) {
                    Event.UP1 -> {
                        if (secondKey != null) {
                            processKey(secondKey)
                        }
                        keypat = Keys.keypat0
                        keyView.draw(keypat, null, null, candPage)
                        state = State.STATE0
                    }
                    Event.UP2 -> {
                        if (selectedKey != null) {
                            processKey(selectedKey)
                        }
                        keyView.draw(keypat, downKey, null, 0)
                        state = State.STATE5
                    }
                    Event.MOVE -> {
                        keyView.draw(keypat, downKey, selectedKey, 0)
                    }
                    else -> {}
                }
            }
            State.STATE5 -> {
                when (e) {
                    Event.UP1 -> {
                        keypat = Keys.keypat0
                        keyView.draw(keypat, null, null, candPage)
                        state = State.STATE0
                    }
                    Event.DOWN2 -> {
                        keyView.draw(keypat, downKey, selectedKey, 0)
                        state = State.STATE6
                    }
                    else -> {}
                }
            }
            State.STATE6 -> {
                when (e) {
                    Event.UP1, Event.UP2 -> {
                        if (selectedKey != null) {
                            processKey(selectedKey)
                        }
                        shiftLockTimeout = Runnable {
                            trans(Event.SHIFTLOCKTIMER)
                        }
                        shiftLockTimeoutHandler.postDelayed(shiftLockTimeout, SHIFTLOCKTIME.toLong())
                        keyView.draw(keypat, downKey, null, 0)
                        state = State.STATE7
                    }
                    Event.MOVE -> {
                        keyView.draw(keypat, downKey, selectedKey, 0)
                        secondKey = selectedKey
                    }
                    else -> {}
                }
            }
            State.STATE7 -> {
                when (e) {
                    Event.DOWN1, Event.DOWN2 -> {
                        shiftLockTimeoutHandler.removeCallbacks(shiftLockTimeout)
                        keyView.draw(keypat, downKey, selectedKey, 0)
                        secondKey = selectedKey
                        state = State.STATE6
                    }
                    Event.SHIFTLOCKTIMER -> {
                        keypat = Keys.keypat0
                        keyView.draw(keypat, null, null, candPage)
                        state = State.STATE0
                    }
                    else -> {}
                }
            }
        }
    }

    fun resetInput() {
        inputPatArray = ArrayList()
        inputCharArray = ArrayList()
        for (i in KeyView.candButtons.indices) {
            KeyView.candButtons[i].text = ""
            KeyView.candButtons[i].visible = false
        }
        candPage = 0
    }

    fun reset() {
        Search.reset()
        shiftLockTimeoutHandler.removeCallbacks(shiftLockTimeout)
        shiftTimeoutHandler.removeCallbacks(shiftTimeout)
        state = State.STATE0
        resetInput()
        keypat = Keys.keypat0
        keyView.draw(keypat, null, null, 0)
    }

    // [Kotlinらしい改善] ループによる文字列結合を joinToString("") に置き換え、コードを簡潔化
    fun inputPat(): String {
        return inputPatArray.joinToString("")
    }

    fun inputWord(): String {
        return inputCharArray.joinToString("")
    }

    private fun searchAndDispCand() {
        searchTask = SearchTask(keyView, useGoogle, sqlDict, slime)
        searchTask?.execute(inputPat(), inputWord())
    }

    private fun processKey(key: Key?) {
        if (key == null) return
        searchTask?.cancel(true)

        val c = key.str
        val p = key.pat
        val inputlen = inputCharArray.size
        var toExact = false
        if (c == "←") {
            if (inputlen == 0) {
                slime.keyDownUp(KeyEvent.KEYCODE_DEL)
            } else {
                inputCharArray.removeAt(inputlen - 1)
                inputPatArray.removeAt(inputlen - 1)
                if (inputlen > 1) {
                    searchAndDispCand()
                } else {
                    resetInput()
                    keyView.draw(keypat, null, null, 0)
                }
                state = State.STATE0
                slime.showComposingText()
            }
        } else if (c == "↴") {
            if (inputlen == 0) {
                slime.keyDownUp(KeyEvent.KEYCODE_ENTER)
                resetInput()
            } else {
                if (LocalDict.exactMode) {
                    fix(inputWord(), inputPat())
                    resetInput()
                } else {
                    toExact = true
                    LocalDict.exactMode = true
                    searchAndDispCand()
                }
            }
        } else if (keypat === Keys.keypatbs || keypat === Keys.keypatsp || c.matches(Regex("[a-zA-Z0-9]")) || key.pat.isEmpty()) {
            if (inputlen != 0) {
                fix(inputWord(), inputPat())
            }
            fix(c, inputPat())
            resetInput()
        } else {
            if (LocalDict.exactMode) {
                LocalDict.exactMode = false
                fix(inputWord(), inputPat())
                resetInput()
            }
            inputPatArray.add(p)
            inputCharArray.add(c)
            searchAndDispCand()
            slime.showComposingText()
        }
        LocalDict.exactMode = toExact
    }

    fun fix(s: String, p: String) {
        sqlDict.add(s, p)
        sqlDict.limit(1000)
        slime.input(s)
        resetInput()
        slime.clearRegWord()
    }
}
