package suwayomi.tachidesk.lite.controller

import io.javalin.http.HttpCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.lite.impl.Lite
import suwayomi.tachidesk.lite.model.dataclass.LiteChapterDataClass
import suwayomi.tachidesk.lite.model.dataclass.LiteChapterInfoDataClass
import suwayomi.tachidesk.lite.model.dataclass.LiteMangaDataClass
import suwayomi.tachidesk.lite.model.dataclass.LiteSourceDataClass
import suwayomi.tachidesk.manga.impl.Search
import suwayomi.tachidesk.manga.model.dataclass.PagedMangaListDataClass
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.util.*
import kotlin.time.Duration.Companion.days

object LiteController {

    /** list available sources */
    val listSources = handler(
        documentWith = {
            withOperation {
                summary("Source List")
                description("List of sources")
            }
        },
        behaviorOf = { ctx ->
            ctx.json(Lite.getSourceList())
        },
        withResults = {
            json<Array<LiteSourceDataClass>>(HttpCode.OK)
        }
    )

    // Content
    val getManga = handler(
        pathParam<Long>("sourceId"),
        pathParam<String>("mangaUrl"),
        documentWith = {
            withOperation {
                summary("Get Manga Info")
                description("Get a manga from a specified source")
            }
        },
        behaviorOf = { ctx, sourceId, mangaUrl ->
            ctx.future(
                JavalinSetup.future {
                    Lite.getManga(sourceId, mangaUrl)
                }
            )
        },
        withResults = {
            json<LiteMangaDataClass>(HttpCode.OK)
            httpCode(HttpCode.NOT_FOUND)
        }
    )

    val getMangaChapters = handler(
        pathParam<Long>("sourceId"),
        pathParam<String>("mangaUrl"),
        documentWith = {
            withOperation {
                summary("Get Manga Chapter List")
                description("Gets chapters for a specified manga")
            }
        },
        behaviorOf = { ctx, sourceId, mangaUrl ->
            ctx.future(
                JavalinSetup.future {
                    Lite.getChapters(sourceId, mangaUrl)
                }
            )
        },
        withResults = {
            json<List<LiteChapterDataClass>>(HttpCode.OK)
            httpCode(HttpCode.NOT_FOUND)
        }
    )

    val getPages = handler(
        pathParam<Long>("sourceId"),
        pathParam<String>("mangaUrl"),
        pathParam<String>("chapterUrl"),
        documentWith = {
            withOperation {
                summary("Get Manga Chapter Pages")
                description("Gets chapters for a specified manga")
            }
        },
        behaviorOf = { ctx, sourceId, mangaUrl, chapterUrl ->
            ctx.future(
                JavalinSetup.future {
                    Lite.getPages(sourceId, mangaUrl, chapterUrl)
                }
            )
        },
        withResults = {
            json<LiteChapterInfoDataClass>(HttpCode.OK)
            httpCode(HttpCode.NOT_FOUND)
        }
    )

    // -- Images
    val getPage = handler(
        pathParam<Long>("sourceId"),
        queryParam<String?>("imageUrl"),
        queryParam<String>("url"),
        documentWith = {
            withOperation {
                summary("Get a chapter page")
                description("Get a chapter page for a given index. Cache use can be disabled so it only retrieves it directly from the source.")
            }
        },
        behaviorOf = { ctx, sourceId, imageUrl, url ->
            ctx.future(
                JavalinSetup.future { Lite.getPage(sourceId, imageUrl, url) }
                    .thenApply {
                        ctx.header("content-type", it.second)
                        val httpCacheSeconds = 1.days.inWholeSeconds
                        ctx.header("cache-control", "max-age=$httpCacheSeconds")
                        it.first
                    }
            )
        },
        withResults = {
            image(HttpCode.OK)
            httpCode(HttpCode.NOT_FOUND)
        }
    )

    val getThumbnail = handler(
        pathParam<Long>("sourceId"),
        pathParam<String>("thumbnailUrl"),
        documentWith = {
            withOperation {
                summary("Get a manga thumbnail")
                description("Get a manga thumbnail from the source or the cache.")
            }
        },
        behaviorOf = { ctx, sourceId, thumbUrl ->
            ctx.future(
                JavalinSetup.future { Lite.getThumbnail(sourceId, thumbUrl) }
                    .thenApply {
                        ctx.header("content-type", it.second)
                        val httpCacheSeconds = 1.days.inWholeSeconds
                        ctx.header("cache-control", "max-age=$httpCacheSeconds")
                        it.first
                    }
            )
        },
        withResults = {
            image(HttpCode.OK)
            httpCode(HttpCode.NOT_FOUND)
        }
    )

    // - Searching
    val getFilters = handler(
        pathParam<Long>("sourceId"),
        documentWith = {
            withOperation {
                summary("Source filters")
                description("Fetch filters of source with id `sourceId`")
            }
        },
        behaviorOf = { ctx, sourceId ->
            ctx.json(Search.getFilterList(sourceId, false))
        },
        withResults = {
            json<Array<Search.FilterObject>>(HttpCode.OK)
        }
    )

    private val json by DI.global.instance<Json>()
    val search = handler(
        pathParam<Long>("sourceId"),
        queryParam("query", ""),
        queryParam("page", 1),
        documentWith = {
            withOperation {
                summary("[Lite] Search Source")
                description("Search Source")
            }
            body<Search.FilterChange>()
            body<Array<Search.FilterChange>>()
        },
        behaviorOf = { ctx, sourceId, query, page ->
            val filterChange = try {
                json.decodeFromString<List<Search.FilterChange>>(ctx.body())
            } catch (e: Exception) {
                listOf(json.decodeFromString<Search.FilterChange>(ctx.body()))
            }
            ctx.future(JavalinSetup.future { Lite.search(sourceId, query, page, filterChange) })
        },
        withResults = {
            json<PagedMangaListDataClass>(HttpCode.OK)
        }
    )
}
