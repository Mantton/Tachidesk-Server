package suwayomi.tachidesk.lite.impl

import java.net.URLDecoder
import java.net.URLEncoder

object Coder {

    fun encode(value: String): String {
        return URLEncoder
            .encode(value, "UTF-8")
            .replace("+", "%20")
    }
}
