//
//	接続辞書を使った単語検索
//
//	iPhone用にJSで書いてあったものをAndroid用に書き直し
//	2010/9/20
//
package com.pitecan.slime2

import android.content.res.AssetManager
import java.io.IOException
import java.util.regex.Pattern

class DictEntry(val pat: String, val word: String, val inConnection: Int, val outConnection: Int) {
    var keyLink: Int = 0
    var connectionLink: Int = 0
}

class LocalDict(assetManager: AssetManager) {
    companion object {
        var dict: Array<DictEntry?> = arrayOf()
        val keyLink = IntArray(10)
        val connectionLink = IntArray(2000)

        val regexp = arrayOfNulls<Pattern>(50)
        val cslength = IntArray(50)

        val wordStack = arrayOfNulls<String>(20)
        val patStack = arrayOfNulls<String>(20)

        var exactMode = false

        private var fib1 = 1
        private var fib2 = 1

        private val patIndPatterns by lazy {
            arrayOf(
                Pattern.compile("\\[?[aiueoAIUEO].*"),
                Pattern.compile("\\[?[kg].*"),
                Pattern.compile("\\[?[sz].*"),
                Pattern.compile("\\[?[tdT].*"),
                Pattern.compile("\\[?[n].*"),
                Pattern.compile("\\[?[hbp].*"),
                Pattern.compile("\\[?[m].*"),
                Pattern.compile("\\[?[yY].*"),
                Pattern.compile("\\[?[r].*")
            )
        }

        fun initLink() {
            // [Kotlinらしい改善] 手動のforループによる配列の初期化を fill(-1) 拡張関数に置き換え、簡潔かつ効率的に記述
            keyLink.fill(-1)
            val cur = IntArray(10)
            for (i in dict.indices) {
                val entry = dict[i] ?: continue
                if (entry.word.startsWith("*")) continue
                val ind = patInd(entry.pat)
                if (keyLink[ind] < 0) {
                    cur[ind] = i
                    keyLink[ind] = i
                } else {
                    dict[cur[ind]]?.keyLink = i
                    cur[ind] = i
                }
                entry.keyLink = -1
            }

            connectionLink.fill(-1)
            val connCur = IntArray(2000)
            for (i in dict.indices) {
                val entry = dict[i] ?: continue
                val ind = entry.inConnection
                if (connectionLink[ind] < 0) {
                    connCur[ind] = i
                    connectionLink[ind] = i
                } else {
                    dict[connCur[ind]]?.connectionLink = i
                    connCur[ind] = i
                }
                entry.connectionLink = -1
            }
        }

        fun patInit(pat: String, level: Int): String {
            var p = ""
            var top = ""
            cslength[level] = 0
            if (pat.isNotEmpty()) {
                val re = Pattern.compile("^(\\[[^\\]]+\\])(.*)$")
                val m = re.matcher(pat)
                if (m.find()) {
                    top = m.group(1) ?: ""
                    p = patInit(m.group(2) ?: "", level + 1)
                } else {
                    val re2 = Pattern.compile("^(.)(.*)$")
                    val m2 = re2.matcher(pat)
                    if (m2.find()) {
                        top = m2.group(1) ?: ""
                        p = patInit(m2.group(2) ?: "", level + 1)
                    }
                }
                cslength[level] = cslength[level + 1] + 1
            }

            top += if (p.isNotEmpty()) "($p)?" else ""
            regexp[level] = Pattern.compile("^($top)")
            return top
        }

        fun search(pat: String, searchTask: SearchTask) {
            patInit(pat, 0)
            fib1 = 1
            fib2 = 1
            generateCand(0, patInd(pat), 0, "", "", 0, searchTask)
        }

        fun generateCand(
            connection: Int,
            keylink: Int,
            len: Int,
            word: String,
            pat: String,
            level: Int,
            searchTask: SearchTask
        ) {
            wordStack[level] = word
            patStack[level] = pat

            val patlen = cslength[len]
            var d = if (connection != 0) connectionLink[connection] else keyLink[keylink]
            while (d >= 0 && Search.ncands < Slime.MAXCANDS) {
                if (searchTask.isCancelled) break
                val entry = dict[d]
                if (entry != null) {
                    val regex = regexp[len]
                    if (regex != null) {
                        val m = regex.matcher(entry.pat)
                        if (m.find()) {
                            val matchlen = m.group(1)?.length ?: 0
                            if (matchlen == patlen && (!exactMode || exactMode && entry.pat.length == matchlen)) {
                                addConnectedCandidate(entry.word, entry.pat, entry.outConnection, level, matchlen)
                                if (Search.ncands >= fib1) {
                                    val tmp = fib1
                                    searchTask.progress(0)
                                    fib1 += fib2
                                    fib2 = tmp
                                }
                            } else if (matchlen == entry.pat.length && entry.outConnection != 0) {
                                generateCand(entry.outConnection, 0, len + matchlen, entry.word, entry.pat, level + 1, searchTask)
                            }
                        }
                    }
                }
                d = if (connection != 0) dict[d]?.connectionLink ?: -1 else dict[d]?.keyLink ?: -1
            }
        }

        fun addConnectedCandidate(word: String, pat: String, connection: Int, level: Int, matchlen: Int) {
            if (word.isEmpty()) return
            if (word.endsWith("*")) return

            val sbP = StringBuilder()
            for (i in 0..level) {
                sbP.append(patStack[i] ?: "")
            }
            sbP.append(pat)

            val sbW = StringBuilder()
            for (i in 0..level) {
                sbW.append(wordStack[i] ?: "")
            }
            sbW.append(word)

            val w = sbW.toString().replace(Regex("\\*"), "")
            Search.addCandidateWithLevel(w, sbP.toString(), level)
        }

        fun patInd(str: String): Int {
            // [Kotlinらしい改善] 手動の初期化フラグとforループによる検索を、lazyプロパティ delegate と indexOfFirst を用いた関数型スタイルに改善
            val index = patIndPatterns.indexOfFirst { it.matcher(str).matches() }
            return if (index != -1) index else 9
        }
    }

    init {
        try {
            // [Kotlinらしい改善] ファイルを2回開いて行数を数えていた処理を、useLines と filter を用いて1回のストリーム読み込みに効率化
            assetManager.open("dict.txt").bufferedReader().useLines { lines ->
                val validLines = lines.filter { l ->
                    l.isNotEmpty() && l[0] != '#' && l[0] != ' ' && l[0] != '\t'
                }.toList()

                dict = arrayOfNulls<DictEntry>(validLines.size) as Array<DictEntry?>
                for ((i, l) in validLines.withIndex()) {
                    val a = l.split("\t")
                    val outConn = if (a.size > 3 && a[3].isNotEmpty()) a[3].toInt() else 0
                    val inConn = if (a.size > 2 && a[2].isNotEmpty()) a[2].toInt() else 0
                    val pat = a.getOrNull(0) ?: ""
                    val word = a.getOrNull(1) ?: ""
                    dict[i] = DictEntry(pat, word, inConn, outConn)
                }
            }
        } catch (e: IOException) {
            // e.printStackTrace()
        }
        initLink()
    }
}
