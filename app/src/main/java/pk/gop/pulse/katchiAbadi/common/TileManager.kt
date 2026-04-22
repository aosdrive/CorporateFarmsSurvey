package pk.gop.pulse.katchiAbadi.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class TileManager(private val context: Context) {

    // ✅ Single shared OkHttpClient with a large connection pool
    // Reusing connections is the biggest single perf win for tile downloads
    private val okHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES)) // 20 idle connections
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ✅ Single shared Retrofit + TileService instance — never recreate per tile
    private val tileService: TileService by lazy {
        Retrofit.Builder()
            .baseUrl("https://mt1.google.com/")
            .client(okHttpClient)
            .build()
            .create(TileService::class.java)
    }

    // ─── Used by the map viewer ───────────────────────────────────────────────

    fun viewCacheTile(
        level: Int,
        row: Int,
        column: Int,
        areaId: String,
        sourceFolder: String,
        minZoomLevel: Int,
        maxZoomLevel: Int,
    ): File? {
        if (level < minZoomLevel || level > maxZoomLevel) return null

        val cacheDir = resolveTileDir(areaId, sourceFolder)
        val tileFile = File(cacheDir, "tile_${level}_${row}_${column}.png")
        return if (tileFile.exists()) tileFile else null
    }

    // ─── Legacy download (used by downloadMapTiles / old flow) ───────────────

    suspend fun downloadAndCacheTile(
        level: Int,
        row: Int,
        column: Int,
        areaId: Long,
        sourceFolder: String,
        minZoomLevel: Int,
        maxZoomLevel: Int,
    ): File? = downloadTileToDir(
        level, row, column,
        dir = resolveTileDir(areaId.toString(), sourceFolder),
        minZoomLevel, maxZoomLevel, sourceFolder
    )

    suspend fun downloadAndCacheTileNew(
        level: Int,
        row: Int,
        column: Int,
        areaId: String,
        sourceFolder: String,
        minZoomLevel: Int,
        maxZoomLevel: Int,
    ): File? = downloadTileToDir(
        level, row, column,
        dir = resolveTileDir(areaId, sourceFolder),
        minZoomLevel, maxZoomLevel, sourceFolder
    )

    // ─── New fast path — writes directly to filesDir, no copy step ───────────

    suspend fun downloadAndSaveTileDirect(
        level: Int,
        row: Int,
        column: Int,
        outputFolder: File,
        minZoomLevel: Int,
        maxZoomLevel: Int,
    ): File? {
        if (level < minZoomLevel || level > maxZoomLevel) return null

        val tileFile = File(outputFolder, "tile_${level}_${row}_${column}.png")
        if (tileFile.exists()) return tileFile  // ✅ Skip already-cached tiles on retry

        return fetchAndWrite(tileFile, level, row, column)
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun resolveTileDir(areaId: String, sourceFolder: String): File {
        val base = if (sourceFolder == "files") context.filesDir else context.cacheDir
        return File(base, "MapTiles/$areaId").apply { if (!exists()) mkdirs() }
    }

    private suspend fun downloadTileToDir(
        level: Int, row: Int, column: Int,
        dir: File, minZoomLevel: Int, maxZoomLevel: Int,
        sourceFolder: String,
    ): File? {
        if (sourceFolder != "files" && (level < minZoomLevel || level > maxZoomLevel)) return null

        val tileFile = File(dir, "tile_${level}_${row}_${column}.png")
        if (tileFile.exists()) return tileFile

        return fetchAndWrite(tileFile, level, row, column)
    }

    private suspend fun fetchAndWrite(tileFile: File, level: Int, row: Int, column: Int): File? {
        val url = "vt/lyrs=s&x=$column&y=$row&z=$level"
        return withContext(Dispatchers.IO) {
            try {
                val bytes: ByteArray = tileService.getTile(url).bytes()
                tileFile.parentFile?.mkdirs()
                FileOutputStream(tileFile).use { it.write(bytes) }
                tileFile
            } catch (e: Exception) {
                Log.w("TileManager", "Tile failed z=$level x=$column y=$row: ${e.message}")
                null  // ✅ Never throws — one bad tile doesn't abort the batch
            }
        }
    }
}

interface TileService {
    @GET
    suspend fun getTile(@Url url: String): ResponseBody
}