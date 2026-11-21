//
// Google CGI API for Japanese Input
// http://www.google.co.jp/ime/cgiapi.html
// Google日本語入力のURLは "http://google.co.jp/transliterate?langpair=ja-Hira|ja&text=かんじ" のような形式だが
// "|" を "%7c" にしておかないと new HttpGet() が失敗する
//

package com.pitecan.slime2

import android.util.Log
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GoogleIME {
    companion object {
        fun ime(q: String): Array<String> {
            val urlstr = "https://google.co.jp/transliterate?langpair=ja-Hira%7cja&text=$q"
            val maxSuggestions = 20
            val suggestions = Array(maxSuggestions + 1) { "" }
            var nsuggest = 0

            // デバッグ用: 送信内容（リクエストURL）のログ出力
            Log.d("Slime", "GoogleIME Request -> URL: $urlstr, query text: $q")

            try {
                val url = URL(urlstr)
                val con = url.openConnection() as HttpsURLConnection
                con.requestMethod = "GET"
                con.connectTimeout = 3000
                con.readTimeout = 3000

                val responseCode = con.responseCode
                Log.d("Slime", "GoogleIME Response Code: $responseCode, Message: ${con.responseMessage}")

                val body = if (responseCode == HttpsURLConnection.HTTP_OK) {
                    // [Kotlinらしい改善] Javaの明示的なストリームクローズやボイラープレートを避け、bufferedReader().use { } でリソースを安全かつ簡潔に管理
                    con.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorBody = con.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e("Slime", "GoogleIME Error Body: $errorBody")
                    ""
                }

                // デバッグ用: 受信内容（レスポンスボディ）のログ出力
                Log.d("Slime", "GoogleIME Received Body: $body")
                con.disconnect()

                if (body.isNotEmpty()) {
                    val ja1 = JSONArray(body)
                    val len1 = ja1.length()
                    if (len1 > 0) {
                        val ja2 = ja1.getJSONArray(0)
                        val ja3 = ja2.getJSONArray(1) // 第2要素 = 変換候補
                        val len3 = ja3.length()
                        while (nsuggest < len3 && nsuggest < maxSuggestions) {
                            suggestions[nsuggest] = ja3.getString(nsuggest)
                            nsuggest++
                        }
                        suggestions[nsuggest] = ""
                        for (idx in 1 until len1) {
                            val jArray2 = ja1.getJSONArray(idx)
                            // val s = jArray2.getString(0) // 第1要素 = 元の文字列
                            val jArray3 = jArray2.getJSONArray(1) // 第2要素 = 変換候補
                            for (j in 0 until nsuggest) {
                                suggestions[j] += jArray3.getString(0) // ふたつめ以降は最初の候補を連結する
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Slime", "GoogleIME Exception during request/parsing", e)
                e.printStackTrace()
            }
            return suggestions
        }
    }
}
