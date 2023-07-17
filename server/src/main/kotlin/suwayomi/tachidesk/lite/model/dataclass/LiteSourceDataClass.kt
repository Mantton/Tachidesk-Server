package suwayomi.tachidesk.lite.model.dataclass

data class LiteSourceDataClass(
    val id: String,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val isNsfw: Boolean
)
