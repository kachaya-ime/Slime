//
// いろんな辞書を検索するものをまとめる
// その後でまとめてAsyncTaskにする。
//

package com.pitecan.slime2

import android.util.Log

class Candidate(var pat: String, var word: String, var weight: Int)

class Search(val localDict: LocalDict, val sqlDict: SQLDict, val slime: Slime) {
    companion object {
        var candidates: Array<Candidate> = Array(Slime.MAXCANDS) { Candidate("", "", 0) }
        var ncands = 0

        fun reset() {
            ncands = 0
        }

        //
        // いろんな辞書を使った検索!
        //
        fun search(pat: String, word: String, useGoogle: Boolean, searchTask: SearchTask, sqlDict: SQLDict, slime: Slime): Array<Candidate> {
            Log.v("Slime", "Search - pat=$pat, word=$word")
            ncands = 0
            KeyController.candPage = 1

            // ひらがな/カタカナ
            if (LocalDict.exactMode) {
                val hira = word
                val p = Keys.hira2pat(hira) // 無理矢理ひらがなをローマ字パタンに変換
                addCandidateWithLevel(hira, p, -100)
                addCandidateWithLevel(h2k(hira), p, -99)
            }

            // コピーした単語を候補に出す (新規登録用)
            if (!LocalDict.exactMode) {
                val s = slime.getRegWord()
                if (s.isNotEmpty() && s.length < 10) { // コピー文字列が短い場合だけ候補にする
                    addCandidate(s, Keys.hira2pat(word))
                }
            }

            // 学習辞書を検索
            val s = sqlDict.match(pat, LocalDict.exactMode)
            for (k in s.indices) {
                addCandidateWithLevel(s[k][0], s[k][1], -50 + k)
            }

            // 通常のローカル辞書を検索
            // 時間がかかることがあるのでSearchTaskでバックグラウンド動作させている。
            // 何かキー入力があれば isCancelled() がtrueになる。
            //
            LocalDict.search(pat, searchTask)

            if (!searchTask.isCancelled) {
                // Google Suggest または Google日本語入力を利用
                if (useGoogle && LocalDict.exactMode) {
                    if (slime.isConnected()) {
                        val suggestions = GoogleIME.ime(word)
                        Log.v("Slime", "length=" + suggestions.size)
                        // [Kotlinらしい改善] whileループによるインデックス走査を、takeWhile と forEach を用いた関数型スタイルに改善
                        suggestions.takeWhile { it.isNotEmpty() }.forEach { suggestion ->
                            addCandidateWithLevel(suggestion, Keys.hira2pat(word), 50)
                        }
                    }
                }

                // 優先度に従って候補を並べなおし
                // ソートをやめてみたが全く違いがわからない... 要るのだろうか?? (2012/12/11 08:58:42)

                if (ncands == 0) {
                    val hira = word
                    val p = Keys.hira2pat(hira) // 無理矢理ひらがなをローマ字パタンに変換
                    addCandidateWithLevel(hira, p, -100)
                    addCandidateWithLevel(h2k(hira), p, -99)
                }
            }

            // 最終的に確定した候補一覧をデバッグ出力
            Log.d("Slime", "=== Final Candidates for pat='$pat', word='$word' (Total: $ncands) ===")
            for (i in 0 until ncands) {
                val c = candidates[i]
                Log.d("Slime", "  [$i] word='${c.word}', pat='${c.pat}', weight=${c.weight}")
            }

            return candidates
        }

        fun addCandidate(word: String, pat: String) {
            addCandidateWithLevel(word, pat, 0)
        }

        fun addCandidateWithLevel(word: String, pat: String, level: Int) {
            if (ncands >= Slime.MAXCANDS) return
            var i = 0
            while (i < ncands) {
                val cand = candidates[i]
                if (cand.word.isEmpty()) break
                if (cand.word == word) break
                i++
            }
            if (i >= ncands) {
                candidates[ncands].pat = pat
                candidates[ncands].word = word
                candidates[ncands].weight = level
                ncands++
            }
        }

        // ひらがな ⇒カタカナ
        // http://www7a.biglobe.ne.jp/~java-master/samples/string/ZenkakuHiraganaToZenkakuKatakana.html
        // [Kotlinらしい改善] StringBuilderを直接操作するループを map と joinToString に置き換え、関数型スタイルに改善
        private fun h2k(s: String): String {
            return s.map { c ->
                if (c in 'ぁ'..'ん') (c.code - 'ぁ'.code + 'ァ'.code).toChar() else c
            }.joinToString("")
        }
    }

    init {
        for (i in 0 until Slime.MAXCANDS) {
            candidates[i] = Candidate("", "", 0)
        }
    }
}
