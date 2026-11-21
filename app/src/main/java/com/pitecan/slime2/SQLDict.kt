//
// 学習辞書をSQLiteで実装する
//
// そもそも学習辞書をSQLiteで実装すべきなのかよくわからないし、
// 仕様が色々面倒なのだがとりあえず...
// (2011/12/10)
//
// 以下のようなページを参考にした。
// AndroidのSQLiteの使い方
// http://android.roof-balcony.com/shori/strage/select/
// サンプル
// http://codezine.jp/article/detail/4814?p=2
// サンプル
// http://ichitcltk.hustle.ne.jp/gudon/modules/pico_rd/index.php?content_id=74
// Helper
// http://android.roof-balcony.com/shori/strage/sqlite/
// http://www.ipentec.com/document/document.aspx?page=android-use-sqlite-simple-app
// Android SQLiteマニュアル
// http://developer.android.com/intl/ja/reference/android/database/sqlite/SQLiteDatabase.html
// http://developer.android.com/intl/ja/reference/android/database/Cursor.html

package com.pitecan.slime2

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.regex.Pattern
import java.util.ArrayList

class DBHelper(context: Context) : SQLiteOpenHelper(context, "learndict", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        // DBが存在する場合は呼ばれないらしい
        db.execSQL(
            "create table history(" +
                    "   word text not null," +
                    "   pat text not null," +
                    "   patind text not null," +
                    "   date text not null" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }
}

class SQLDict(context: Context) {
    private val db: SQLiteDatabase = DBHelper(context).writableDatabase

    fun add(word: String, pat: String) { // エントリ追加
        // 最初に全部消す
        db.delete("history", "word = '$word' AND pat = '$pat'", null)
        val patind = LocalDict.patInd(pat)
        // SQLite3の日付処理
        db.execSQL("insert into history(word,pat,patind,date) values ('$word', '$pat', $patind, datetime('now', 'localtime'));")
        Log.d("Slime", "SQLDict add: word='$word', pat='$pat', patind=$patind")
    }

    fun limit(max: Int) { // max個までにDBを制限する
        // [Kotlinらしい改善] Cursorの走査とクローズを useブロックで安全に処理し、リソースリークを防止
        db.query(
            "history",
            arrayOf("word", "pat", "patind", "date"),
            null, null, null, null, "date desc"
        ).use { cursor ->
            val count = cursor.count
            var currentCount = count
            while (currentCount > max && cursor.moveToPosition(currentCount - 1)) {
                val word = cursor.getString(0)
                val pat = cursor.getString(1)
                db.delete("history", "word = '$word' AND pat = '$pat'", null)
                Log.d("Slime", "SQLDict limit: pruned old entry word='$word', pat='$pat' (total count was $currentCount, max=$max)")
                currentCount--
            }
        }
    }

    fun match(pat: String, exactMode: Boolean): Array<Array<String>> { // 新しいものから検索
        val words = ArrayList<String>()
        val wordpats = ArrayList<String>()
        val pattern = if (exactMode) Pattern.compile("^$pat") else Pattern.compile("^$pat.*")
        val patind = LocalDict.patInd(pat)

        // [Kotlinらしい改善] 手動の moveToFirst() / moveToNext() ループを useブロックと while(moveToNext()) に置き換え、Cursorの自動クローズと可読性を向上
        db.query(
            "history",
            arrayOf("word", "pat", "date"),
            "patind = $patind",
            null, null, null, "date desc"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val wordpat = cursor.getString(1)
                if (pattern.matcher(wordpat).matches()) {
                    words.add(word)
                    wordpats.add(wordpat)
                }
            }
        }

        Log.d("Slime", "SQLDict match: pat='$pat', patind=$patind, exactMode=$exactMode, matched ${words.size} entries -> words=$words, pats=$wordpats")

        val res = Array(words.size) { Array(2) { "" } }
        for (i in words.indices) {
            res[i][0] = words[i]
            res[i][1] = wordpats[i]
        }
        return res
    }
}
