//
//	Slime for Android
//
//	Toshiyuki Masui 2010/9/22 (Lexierra.Android)
//      Slimeとして頑張る 2011/11/20
//

package com.pitecan.slime2

import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.inputmethodservice.InputMethodService
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Slime : InputMethodService() {
    private var keys: Keys? = null
    private var keyView: KeyView? = null
    private var keyController: KeyController? = null
    private var dict: LocalDict? = null
    private var sqlDict: SQLDict? = null
    private var search: Search? = null

    // [修正] API 11 で非推奨となった android.text.ClipboardManager の代わりに、
    // android.content.ClipboardManager と primaryClip を使用するように修正
    private var cm: ClipboardManager? = null
    private var clipboardText: String = ""

    private fun getClipboardTextString(): String {
        val clipData = cm?.primaryClip
        val seq = if (clipData != null && clipData.itemCount > 0) clipData.getItemAt(0).text else null
        return seq?.toString() ?: ""
    }

    companion object {
        const val MAXCANDS = 50
    }

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    override fun onCreate() {
        super.onCreate()
        val asMgr: AssetManager = resources.assets
        Log.v("Slime", "as")

        dict = LocalDict(asMgr)
        sqlDict = SQLDict(this)
        search = Search(dict!!, sqlDict!!, this)
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardText = getClipboardTextString()

        //
        // IMEでBackボタンを扱えるようにするためのもの。
        //
        @Suppress("DEPRECATION")
        setBackDisposition(BACK_DISPOSITION_WILL_NOT_DISMISS)
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    override fun onInitializeInterface() {
        super.onInitializeInterface() // 必要??
        keys = Keys()
        keyController = KeyController()
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    override fun onCreateInputView(): View {
        super.onCreateInputView() // 必要??
        /*
          KeyControllerクラスから他にアクセスできるようにセットする
        */
        keyView = layoutInflater.inflate(R.layout.input, null) as KeyView
        keyView?.keys = keys
        keyView?.keyController = keyController
        keyController?.search = search!!
        keyController?.keyView = keyView!!
        keyController?.keys = keys!!
        keyController?.dict = dict!!
        keyController?.sqlDict = sqlDict!!
        keyController?.slime = this
        keyController?.reset()

        ViewCompat.setOnApplyWindowInsetsListener(keyView!!) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v as KeyView).bottomInset = insets.bottom
            v.requestLayout()
            WindowInsetsCompat.CONSUMED
        }

        return keyView!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting) // 必要??
        //
        // 別アプリに切り替わったときなど初期化する
        // InputConnection ic = getCurrentInputConnection();
        // if(ic != null) ic.commitText("",1);
        // !!!! このコードがあるせいでブラウザのURL枠をタップしたときURLが消えてしまっていた
        // 何故こういうコードを書いたのか全く覚えていない。 (2012/12/13 21:40:11)
        //
        onFinishInput()
        keyController?.resetInput()
    }

    override fun onFinishInput() {
        super.onFinishInput()
    }

    fun input(s: String) {
        val ic = currentInputConnection
        ic?.commitText(s, 1) // 入力貼り付け
    }

    fun keyDownUp(keyEventCode: Int) { // キー入力
        val ic = currentInputConnection
        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode))
        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEventCode))
    }

    fun showComposingText() {
        val kc = keyController ?: return
        // [Kotlinらしい改善] インデックスベースのループと手動の文字列結合を joinToString に置き換え、宣言的で読みやすいコードに改善
        val composingText = kc.inputPatArray.indices.joinToString("") { i ->
            //Log.v("Slime","pat=" + keyController.inputPatArray.get(i));
            // 曖昧キーの場合 "[" と "]" で囲む
            val pat = kc.inputPatArray[i]
            val char = kc.inputCharArray[i]
            if (pat.matches(Regex(".*\\[..*"))) "($char)" else char
        }
        val ic = currentInputConnection
        ic?.setComposingText(composingText, 1)
    }

    fun hide() {
        requestHideSelf(0) // IMEのViewを隠す
    }

    //
    // 新規登録用にクリップボードの単語を返す
    //
    fun clearRegWord() {
        clipboardText = getClipboardTextString()
    }

    fun getRegWord(): String {
        val s = getClipboardTextString()
        return if (s == clipboardText) {
            ""
        } else {
            s
        }
    }

    //
    // キーボードが表示されているときBackボタンが押されたら対応する
    //
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        //Log.v("Slime","onKeyDown - keyCode = "+keyCode);
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown()) {
            keyController?.backKey()
            return true
        } else {
            return super.onKeyDown(keyCode, event)
        }
    }

    //
    // ConnectivityManagerというのを使って、ネットが使えるかどうかを判断し、
    // ネットがあるときは常にGoogleIMEを使うようにしてみる
    // http://yife.hateblo.jp/entry/2012/10/29/203330
    // http://wada811.blog.fc2.com/?tag=ConnectivityManager
    // AndroidManifest.xmlに以下の追加が必要である
    // <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    //
    // メソッドがActivityの中でしか使えないのでここで定義する。
    //
    // [修正] API 29 (Android 10) で非推奨となった activeNetworkInfo の代わりに、
    // API 29以降では activeNetwork と getNetworkCapabilities を使用し、
    // それ以前のバージョンでは互換性のために activeNetworkInfo を使用するように修正
    //
    fun isConnected(): Boolean {
        val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val connected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connManager.activeNetwork
            if (network == null) {
                Log.d("Slime", "isConnected: activeNetwork is null")
                false
            } else {
                val capabilities = connManager.getNetworkCapabilities(network)
                if (capabilities == null) {
                    Log.d("Slime", "isConnected: network capabilities is null")
                    false
                } else {
                    val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val hasValidated = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    Log.d("Slime", "isConnected: hasInternet=$hasInternet, hasValidated=$hasValidated")
                    hasInternet
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connManager.activeNetworkInfo
            val isConn = networkInfo != null && networkInfo.isConnected
            Log.d("Slime", "isConnected (legacy): isConnected=$isConn")
            isConn
        }
        Log.d("Slime", "isConnected result: $connected")
        return connected
    }
}
