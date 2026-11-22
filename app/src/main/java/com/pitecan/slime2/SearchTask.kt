package com.pitecan.slime2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchTask(
    private val keyView: KeyView,
    private val useGoogle: Boolean,
    private val sqlDict: SQLDict,
    private val slime: Slime
) {
    private var job: Job? = null
    var isCancelled: Boolean = false
        private set

    // 検索ルーチンで候補が見つかったとき progress() を呼ぶことにより候補表示を速くする。
    fun progress(i: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            if (!isCancelled) {
                updateView()
            }
        }
    }

    fun execute(vararg searchParams: String) {
        job?.cancel()
        isCancelled = false
        val pat = searchParams.getOrNull(0) ?: ""
        val word = searchParams.getOrNull(1) ?: ""

        job = CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                if (pat.isNotEmpty()) {
                    // cancel() が呼ばれると isCancelled が true になる
                    Search.search(pat, word, useGoogle, this@SearchTask, sqlDict, slime)
                } else {
                    Search.ncands = 0
                    Search.addCandidateWithLevel("が", "ga", 0)
                    Search.addCandidateWithLevel("は", "ha", 0)
                    Search.addCandidateWithLevel("の", "no", 0)
                    Search.addCandidateWithLevel("に", "ni", 0)
                    Search.addCandidateWithLevel("を", "wo", 0)
                    /*
                      Search.addCandidateWithLevel("と","to",0);
                      Search.addCandidateWithLevel("も","mo",0);
                      Search.addCandidateWithLevel("へ","he",0);
                      Search.addCandidateWithLevel("で","de",0);
                      Search.addCandidateWithLevel("ね","ne",0);
                      Search.addCandidateWithLevel("か","ka",0);
                      Search.addCandidateWithLevel("で","de",0);
                      Search.addCandidateWithLevel("だ","da",0);
                      Search.addCandidateWithLevel("です","desu",0);
                      Search.addCandidateWithLevel("でした","deshita",0);
                    */
                    Search.addCandidateWithLevel("。", ".", 0)
                    Search.addCandidateWithLevel("、", ",", 0)
                }
            }

            if (!isCancelled) {
                // Log.v("Slime", "onPostExecute");
                // ここで候補を表示する
                updateView()
            }
        }
    }

    fun cancel() {
        isCancelled = true
        job?.cancel()
    }

    private fun updateView() {
        // [Kotlinらしい改善] 複数のループ（while + for）によるボタン更新処理を indices.forEach を用いた統合ループに整理し、コードを簡潔化
        val nbuttons = if (Search.ncands > 0) minOf(KeyView.candButtons.size, Search.ncands) else 0

        KeyView.candButtons.indices.forEach { i ->
            if (i < nbuttons) {
                KeyView.candButtons[i].text = Search.candidates[i].word
                KeyView.candButtons[i].pat = Search.candidates[i].pat
            } else {
                KeyView.candButtons[i].text = ""
                KeyView.candButtons[i].pat = ""
            }
        }
        keyView.drawDefault()
    }
}
