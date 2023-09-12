package suwayomi.tachidesk.lite.impl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.lite.model.dataclass.*
import suwayomi.tachidesk.manga.impl.Search.buildFilterList
import suwayomi.tachidesk.manga.impl.Search.getFilterListOf
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.InputStream
import java.util.*

object Lite {
    private val applicationDirs by DI.global.instance<ApplicationDirs>()

    fun getSourceList(): List<LiteSourceDataClass> {
        return transaction {
            SourceTable
                .selectAll()
                .mapNotNull {
                    val sourceExtension = ExtensionTable.select { ExtensionTable.id eq it[SourceTable.extension] }.first()
                    LiteSourceDataClass(
                        it[SourceTable.id].value.toString(),
                        it[SourceTable.name],
                        it[SourceTable.lang],
                        getExtensionIconUrl(sourceExtension[ExtensionTable.apkName]),
                        it[SourceTable.isNsfw]
                    )
                }
        }
    }

    suspend fun getManga(sourceId: Long, mangaUrl: String): LiteMangaDataClass? {
        val source = getCatalogueSourceOrNull(sourceId) as? HttpSource ?: return null
        val sManga = SManga.create().apply {
            url = mangaUrl
            title = ""
        }

        val manga = source.fetchMangaDetails(sManga).awaitSingle()

        var thumbnail = manga.thumbnail_url
        if (thumbnail != null && source.client.interceptors.isNotEmpty()) {
            thumbnail = "/api/v1/lite/thumbnail/$sourceId/${Coder.encode(thumbnail)}"
        }

        sManga.copyFrom(manga)
        sManga.title = manga.title

        val webUrl = (source as? HttpSource)?.getMangaUrl(sManga)

        return LiteMangaDataClass(
            url = Coder.encode(sManga.url),
            sourceId = "$sourceId",
            title = sManga.title,
            artist = sManga.artist,
            author = sManga.author,
            description = sManga.description,
            genres = sManga.genre?.split(", "),
            thumbnail = thumbnail ?: "",
            status = sManga.status,
            webUrl = webUrl
        )
    }

    suspend fun getChapters(sourceId: Long, mangaUrl: String): List<LiteChapterDataClass> {
        val source = getCatalogueSourceOrStub(sourceId)

        val sManga = SManga.create().apply {
            url = mangaUrl
            title = ""
        }

        val chapterList = source.fetchChapterList(sManga).awaitSingle()

        val result = chapterList.mapIndexed { idx, it ->
            ChapterRecognition.parseChapterNumber(it, sManga)

            LiteChapterDataClass(
                index = idx,
                url = Coder.encode(it.url),
                title = it.name,
                dateUploaded = it.date_upload,
                chapterNumber = it.chapter_number,
                lang = source.lang,
                scanlator = it.scanlator
            )
        }

        return result
    }

    suspend fun getPages(sourceId: Long, mangaUrl: String, chapterUrl: String): LiteChapterInfoDataClass {
        val source = getCatalogueSourceOrStub(sourceId)

        val pages = source.fetchPageList(
            SChapter.create().apply {
                url = chapterUrl
                name = ""
            }
        )
            .awaitSingle()
            .map { "/api/v1/lite/source/$sourceId/page/?imageUrl=${it.imageUrl?.let { it1 -> Coder.encode(it1) }}&url=${Coder.encode(it.url)}" }

        return LiteChapterInfoDataClass(pages = pages)
    }

    suspend fun getPage(sourceId: Long, imageUrl: String?, tachiUrl: String): Pair<InputStream, String> {
        val source = getCatalogueSourceOrStub(sourceId)
        source as HttpSource
        val page = Page(0, tachiUrl, imageUrl)
        if (page.imageUrl == null) {
            page.imageUrl = source.fetchImageUrl(page).awaitSingle()
        }

        val url = page.imageUrl!!
        val fileName = Cache.get(url) ?: Cache.set(url, UUID.randomUUID().toString())
        return ImageResponse.getCachedImageResponse(applicationDirs.tempLiteCacheRoot, fileName) {
            source.fetchImage(page).awaitSingle()
        }
    }

    suspend fun getThumbnail(sourceId: Long, thumbnailUrl: String): Pair<InputStream, String> {
        val source = getCatalogueSourceOrStub(sourceId)

        if (source is StubSource) throw IllegalArgumentException("Unknown source")
        source as HttpSource

        val response = source.client.newCall(
            GET(thumbnailUrl, source.headers)
        ).await()
        val stream = response.body.byteStream()
        val contentType = response.headers["Content-Type"] ?: "image/jpeg"

        return Pair(stream, contentType)
    }

    suspend fun search(sourceId: Long, request: SearchRequestBody): LitePagedResultDataClass {
        val source = getCatalogueSourceOrStub(sourceId) as HttpSource
        val filterList = if (request.changes.isNotEmpty()) buildFilterList(sourceId, request.changes) else getFilterListOf(source)

        val response = source
            .fetchSearchManga(request.page, request.query, filterList)
            .awaitSingle()
        val useLiteThumbnail = source.client.networkInterceptors.isNotEmpty()
        val results = response
            .mangas
            .map {
                LiteMangaDataClass(
                    url = Coder.encode(it.url),
                    sourceId = "$sourceId",
                    title = it.title,
                    artist = null,
                    author = null,
                    description = null,
                    genres = null,
                    thumbnail = it.thumbnail_url?.let { thumb ->
                        if (useLiteThumbnail) "/api/v1/lite/thumbnail/$sourceId/${Coder.encode(thumb)}" else thumb
                    },
                    status = null,
                    webUrl = null
                )
            }

        return LitePagedResultDataClass(mangaList = results, hasNextPage = response.hasNextPage)
    }

    suspend fun popular(sourceId: Long, page: Int): LitePagedResultDataClass {
        require(page > 0) {
            "page = $page is not in valid range"
        }
        val source = getCatalogueSourceOrStub(sourceId) as HttpSource

        val useLiteThumbnail = source.client.networkInterceptors.isNotEmpty()

        val response = source.fetchPopularManga(page).awaitSingle()
        val results = response
            .mangas
            .map {
                LiteMangaDataClass(
                    url = Coder.encode(it.url),
                    sourceId = "$sourceId",
                    title = it.title,
                    artist = null,
                    author = null,
                    description = null,
                    genres = null,
                    thumbnail = it.thumbnail_url?.let { thumb ->
                        if (useLiteThumbnail) "/api/v1/lite/thumbnail/$sourceId/${Coder.encode(thumb)}" else thumb
                    },
                    status = null,
                    webUrl = null
                )
            }

        return LitePagedResultDataClass(mangaList = results, hasNextPage = response.hasNextPage)
    }
}
