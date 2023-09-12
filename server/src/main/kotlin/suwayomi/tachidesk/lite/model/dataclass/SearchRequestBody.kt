package suwayomi.tachidesk.lite.model.dataclass

import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.Search

@Serializable
data class SearchRequestBody(
    val changes: List<Search.FilterChange>,
    var query: String,
    var page: Int
)
