package app.delve.data

import android.content.Context
import app.delve.domain.GameState
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Saves the game as JSON in the app's files, atomically: write aside, sync, rename, keeping the previous
 * save as a spare. Also keeps the district (vector tiles, or the demo's Overpass dump).
 */
class Store(context: Context) {
    private val dir = context.filesDir
    private val stateFile = File(dir, "state.json")
    private val districtFile = File(dir, "district.json")
    private val tilesFile = File(dir, "district.bin")

    fun load(): GameState? = sequenceOf(stateFile, File(dir, "state.json.tmp"), spare(stateFile))
        .firstNotNullOfOrNull { f -> runCatching { GameState.fromJson(JSONObject(f.readText())) }.getOrNull() }

    @Synchronized
    fun save(s: GameState) = write(stateFile, s.toJson().toString().toByteArray())

    fun district(): String? = districtFile.takeIf { it.exists() }?.readText()
    fun saveDistrict(json: String) { write(districtFile, json.toByteArray()); tilesFile.delete() }
    fun tiles(): ByteArray? = tilesFile.takeIf { it.exists() }?.readBytes()
    fun saveTiles(b: ByteArray) { write(tilesFile, b); districtFile.delete() }

    private fun write(f: File, bytes: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
        if (f.exists()) f.renameTo(spare(f))
        tmp.renameTo(f)
    }

    private fun spare(f: File) = File(f.parentFile, f.name + ".bak")
}
