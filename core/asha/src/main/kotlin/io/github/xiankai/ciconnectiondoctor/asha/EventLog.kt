package io.github.xiankai.ciconnectiondoctor.asha

import java.util.concurrent.CopyOnWriteArrayList

class EventLog(private val maxEntries: Int = 500) {

    data class Entry(val timestampMillis: Long, val message: String)

    private val entries = CopyOnWriteArrayList<Entry>()

    fun record(message: String, timestampMillis: Long = System.currentTimeMillis()): Entry {
        val entry = Entry(timestampMillis, message)
        entries.add(entry)
        while (entries.size > maxEntries) entries.removeAt(0)
        return entry
    }

    fun snapshot(): List<Entry> = entries.toList()

    fun clear() = entries.clear()
}
