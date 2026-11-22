package com.pitecan.slime2

import android.content.res.AssetManager
import android.graphics.Rect
import org.json.JSONObject

class Key(val rect: Rect, val str: String, val pat: String, var shiftKeypat: Array<Key>?) {
    constructor(x: Int, y: Int, w: Int, h: Int, str: String, pat: String, shiftKeypat: Array<Key>?) :
            this(Rect(x, y, x + w, y + h), str, pat, shiftKeypat)
}

class Keys(assetManager: AssetManager, layoutFileName: String = "layouts.json") {
    val keypat0: Array<Key>
    val keypatbs: Array<Key>
    val keypatsp: Array<Key>
    private val keypats: Array<Array<Key>>

    init {
        instance = this
        val jsonString = assetManager.open(layoutFileName).bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)

        val layoutMap = mutableMapOf<String, Array<Key>>()
        val pendingShiftRefs = mutableMapOf<String, MutableList<Pair<Key, String>>>()

        val keysIterator = jsonObject.keys()
        while (keysIterator.hasNext()) {
            val layoutName = keysIterator.next()
            if (layoutName == "meta") continue

            val layoutObj = jsonObject.getJSONObject(layoutName)
            val rowsArray = layoutObj.getJSONArray("rows")

            val keyList = mutableListOf<Key>()

            val rowHeights = FloatArray(rowsArray.length())
            val rowY = FloatArray(rowsArray.length())
            var currentY = 0f
            for (r in 0 until rowsArray.length()) {
                val rowObj = rowsArray.getJSONObject(r)
                rowHeights[r] = rowObj.optDouble("height", 53.0).toFloat()
                rowY[r] = currentY
                currentY += rowHeights[r]
            }

            for (r in 0 until rowsArray.length()) {
                val rowObj = rowsArray.getJSONObject(r)
                val keysArray = rowObj.getJSONArray("keys")
                val rowHeight = rowHeights[r]
                val startYRow = rowY[r]

                var totalWeight = 0f
                for (i in 0 until keysArray.length()) {
                    val kObj = keysArray.getJSONObject(i)
                    totalWeight += kObj.optDouble("weight", 1.0).toFloat()
                }

                val targetWidth = 320f
                val unitWidth = if (totalWeight > 0f) targetWidth / totalWeight else targetWidth

                var currX = 0f

                for (i in 0 until keysArray.length()) {
                    val kObj = keysArray.getJSONObject(i)
                    val weight = kObj.optDouble("weight", 1.0).toFloat()
                    val w = weight * unitWidth
                    val type = kObj.optString("type", "")

                    if (type == "spacer") {
                        currX += w
                        continue
                    }

                    val str = kObj.optString("str", "")
                    val pat = kObj.optString("pat", "")
                    val h = rowHeight
                    val shiftRef = if (kObj.has("shiftKeypat") && !kObj.isNull("shiftKeypat")) kObj.getString("shiftKeypat") else null

                    val key = Key(currX.toInt(), startYRow.toInt(), w.toInt(), h.toInt(), str, pat, null)
                    keyList.add(key)

                    if (shiftRef != null) {
                        pendingShiftRefs.getOrPut(layoutName) { mutableListOf() }.add(Pair(key, shiftRef))
                    }

                    currX += w
                }
            }
            layoutMap[layoutName] = keyList.toTypedArray()
        }

        pendingShiftRefs.forEach { (_, refs) ->
            refs.forEach { (key, refName) ->
                if (layoutMap.containsKey(refName)) {
                    key.shiftKeypat = layoutMap[refName]
                }
            }
        }

        val meta = jsonObject.optJSONObject("meta")
        val initialName = meta?.optString("initial") ?: layoutMap.keys.firstOrNull() ?: ""
        val bsName = meta?.optString("backspace") ?: ""
        val spName = meta?.optString("space") ?: ""

        keypat0 = layoutMap[initialName] ?: arrayOf()
        keypatbs = layoutMap[bsName] ?: arrayOf()
        keypatsp = layoutMap[spName] ?: arrayOf()

        val consonantsArray = meta?.optJSONArray("consonants")
        if (consonantsArray != null) {
            val list = mutableListOf<Array<Key>>()
            for (i in 0 until consonantsArray.length()) {
                val name = consonantsArray.getString(i)
                layoutMap[name]?.let { list.add(it) }
            }
            keypats = list.toTypedArray()
        } else {
            keypats = arrayOf()
        }
    }

    companion object {
        lateinit var instance: Keys

        val keypat0: Array<Key>
            get() = instance.keypat0
        val keypatbs: Array<Key>
            get() = instance.keypatbs
        val keypatsp: Array<Key>
            get() = instance.keypatsp

        fun hira2pat(hira: String): String {
            if (!::instance.isInitialized) return ""
            return instance.hira2patInternal(hira)
        }
    }

    private fun hira2patInternal(hira: String): String {
        return hira.map { c ->
            keypats.firstNotNullOfOrNull { keypat ->
                keypat.firstOrNull { it.str.isNotEmpty() && it.str[0] == c }?.pat
            } ?: ""
        }.joinToString("")
    }
}
