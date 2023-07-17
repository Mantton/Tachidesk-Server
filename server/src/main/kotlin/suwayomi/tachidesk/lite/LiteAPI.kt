package suwayomi.tachidesk.lite

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import suwayomi.tachidesk.lite.controller.LiteController

object LiteAPI {

    fun defineEndpoints() {
        get("list", LiteController.listSources)
        // Searching
        get("{sourceId}/filters", LiteController.getFilters)
        post("{sourceId}/search", LiteController.search)

        // Core
        get("/manga/{sourceId}/{mangaUrl}", LiteController.getManga)
        get("/manga/{sourceId}/manga/{mangaUrl}/chapters", LiteController.getMangaChapters)
        get("/manga/{sourceId}/manga/{mangaUrl}/chapters/{chapterUrl}/pages", LiteController.getPages)

        // Images
        get("/source/{sourceId}/page", LiteController.getPage)
        get("/thumbnail/{sourceId}/{thumbnailUrl}", LiteController.getThumbnail)
    }
}
