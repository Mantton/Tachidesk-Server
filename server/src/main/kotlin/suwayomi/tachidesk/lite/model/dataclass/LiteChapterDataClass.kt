package suwayomi.tachidesk.lite.model.dataclass

data class LiteChapterDataClass(
    val index: Int,
    val url: String,
    val title: String,
    val dateUploaded: Long,
    val chapterNumber: Float,
    val lang: String,
    val scanlator: String?
)
