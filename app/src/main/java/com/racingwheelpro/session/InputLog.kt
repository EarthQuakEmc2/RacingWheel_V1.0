package com.racingwheelpro.session

import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phone-side input log. One gzipped JSON file per time the app is open.
 * Samples are about 20 Hz: time, steer (-1..1), throttle, brake.
 */
class InputLog(private val dir: File) {
    data class Summary(val id: String, val label: String, val samples: Int)
    data class Point(val t: Long, val steer: Float, val gas: Float, val brake: Float)
    data class Detail(val id: String, val label: String, val points: List<Point>)

    private val points = ArrayList<Point>()
    private var started = 0L
    private var lastNote = 0L
    private var file: File? = null

    fun start() {
        dir.mkdirs()
        started = System.currentTimeMillis()
        lastNote = 0L
        points.clear()
        file = File(dir, "input-$started.json.gz")
    }

    fun note(now: Long, steer: Float, gas: Float, brake: Float) {
        if (file == null) return
        if (now - lastNote < 50) return
        lastNote = now
        points.add(Point(now - started, steer, gas, brake))
        if (points.size % 40 == 0) write()
    }

    fun stop() {
        if (file == null) return
        write()
        file = null
    }

    fun list(): List<Summary> {
        val files = dir.listFiles { item -> item.name.endsWith(".json.gz") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.take(30).map { file ->
            val detail = read(file)
            Summary(file.name, detail.label, detail.points.size)
        }
    }

    fun load(id: String): Detail? {
        val file = File(dir, id)
        if (!file.isFile) return null
        return read(file)
    }

    private fun write() {
        val target = file ?: return
        if (points.isEmpty()) return
        val samples = JSONArray()
        for (point in points) {
            samples.put(JSONArray().put(point.t).put(point.steer.toDouble()).put(point.gas.toDouble()).put(point.brake.toDouble()))
        }
        val json = JSONObject()
            .put("schema", "racing-wheel-pro.input/v1")
            .put("startedAt", started)
            .put("samples", samples)
            .toString()
        target.parentFile?.mkdirs()
        GZIPOutputStream(target.outputStream()).use { gzip -> gzip.write(json.toByteArray()) }
    }

    private fun read(file: File): Detail {
        val text = try {
            GZIPInputStream(file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            return Detail(file.name, file.name, emptyList())
        }
        return try {
            val json = JSONObject(text)
            val startedAt = json.optLong("startedAt", file.lastModified())
            val array = json.optJSONArray("samples") ?: JSONArray()
            val parsed = ArrayList<Point>(array.length())
            for (index in 0 until array.length()) {
                val row = array.optJSONArray(index) ?: continue
                parsed.add(Point(row.optLong(0), row.optDouble(1).toFloat(), row.optDouble(2).toFloat(), row.optDouble(3).toFloat()))
            }
            val label = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(startedAt))
            Detail(file.name, label, parsed)
        } catch (_: Exception) {
            Detail(file.name, file.name, emptyList())
        }
    }
}
