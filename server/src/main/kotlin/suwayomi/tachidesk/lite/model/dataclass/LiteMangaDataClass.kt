package suwayomi.tachidesk.lite.model.dataclass

data class LiteMangaDataClass(
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genres: List<String>?,
    val thumbnail: String?
)

data class LitePagedResultDataClass(
    val mangaList: List<LiteMangaDataClass>,
    val hasNextPage: Boolean
)
