package com.pitecan.slime2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class LearnDictActivity : AppCompatActivity() {
    private lateinit var sqlDict: SQLDict
    private lateinit var listView: ListView
    private lateinit var adapter: DictAdapter
    private var entries: List<DictEntryRecord> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learn_dict)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.learn_dict_title)

        sqlDict = SQLDict(this)
        listView = findViewById(R.id.listView)

        loadEntries()

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = entries[position]
            showEditDialog(entry)
        }

        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)
        fab.setOnClickListener {
            showAddDialog()
        }
    }

    private fun loadEntries() {
        entries = sqlDict.getAllEntries()
        adapter = DictAdapter(entries)
        listView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_learn_dict, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete_all -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_all)
                    .setMessage(R.string.confirm_delete_all)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        sqlDict.deleteAll()
                        loadEntries()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            R.id.action_add -> {
                showAddDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_dict, null)
        val editWord = dialogView.findViewById<EditText>(R.id.editWord)
        val editPat = dialogView.findViewById<EditText>(R.id.editPat)

        AlertDialog.Builder(this)
            .setTitle(R.string.add)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val word = editWord.text.toString().trim()
                val pat = editPat.text.toString().trim()
                if (word.isNotEmpty() && pat.isNotEmpty()) {
                    sqlDict.add(word, pat)
                    loadEntries()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(entry: DictEntryRecord) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_dict, null)
        val editWord = dialogView.findViewById<EditText>(R.id.editWord)
        val editPat = dialogView.findViewById<EditText>(R.id.editPat)
        editWord.setText(entry.word)
        editPat.setText(entry.pat)

        AlertDialog.Builder(this)
            .setTitle(R.string.edit)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newWord = editWord.text.toString().trim()
                val newPat = editPat.text.toString().trim()
                if (newWord.isNotEmpty() && newPat.isNotEmpty()) {
                    sqlDict.update(entry.word, entry.pat, newWord, newPat)
                    loadEntries()
                }
            }
            .setNeutralButton(R.string.delete) { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete)
                    .setMessage(R.string.confirm_delete)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        sqlDict.delete(entry.word, entry.pat)
                        loadEntries()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    inner class DictAdapter(private val items: List<DictEntryRecord>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@LearnDictActivity)
                .inflate(R.layout.item_learn_dict, parent, false)
            val entry = items[position]
            val textWord = view.findViewById<TextView>(R.id.textWord)
            val textPat = view.findViewById<TextView>(R.id.textPat)
            val textDate = view.findViewById<TextView>(R.id.textDate)

            textWord.text = entry.word
            textPat.text = entry.pat
            textDate.text = entry.date

            return view
        }
    }
}
