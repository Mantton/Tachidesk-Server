package suwayomi.tachidesk.lite.impl

import java.util.concurrent.ConcurrentHashMap

object Cache {
    private val store = ConcurrentHashMap<String, String>()

    fun get(url: String): String? {
        return store[url]
    }

    fun set(url: String, fileName: String): String {
        store[url] = fileName
        return fileName
    }
}
