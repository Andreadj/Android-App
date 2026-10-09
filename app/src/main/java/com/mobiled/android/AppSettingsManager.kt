package com.mobiled.android.base

import android.content.Context
import com.mobiled.android.base.comman.KeyStorage
import com.mobiled.android.base.database.AppDatabase
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Portable MobileD configuration shared with the PC/macOS application.
 *
 * The top-level file format deliberately follows the PC App .mobiled format:
 * format + formatVersion + devices + groups + savedColors + legacyPresets + matrixPresets.
 * Android-specific runtime/Music data is carried in extension fields that the
 * PC App can ignore without rejecting the file.
 */
object AppSettingsManager {
    const val FORMAT = "MobileD Configuration"
    const val FORMAT_VERSION = 1

    private const val SDK_PREFS = "sdk-data"
    private const val MATRIX_RUNTIME_PREFS = "matrix_runtime"

    private val matrixIds = intArrayOf(101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112)
    private val musicKinds = arrayOf("bpm", "spectrum")

    fun exportConfiguration(context: Context): JSONObject {
        val db = AppDatabase.getDatabase(context)
        val devices = db.getHardwareTable().getHardwareDevices()
        val groups = db.getGroupTable().getHardwareGroups()

        val deviceArray = JSONArray()
        devices.forEachIndexed { index, d ->
            val id = d.ApName?.takeIf { it.isNotBlank() } ?: d.rowId?.toString() ?: "device-$index"
            deviceArray.put(JSONObject().apply {
                put("id", id)
                put("APName", d.ApName ?: id)
                put("DevName", d.devName ?: "")
                put("IP", d.ip ?: "")
                put("Port", d.port)
                put("DMXAddress", d.dMXAddress)
                put("NProt", d.nProt ?: "")
                put("createdAt", d.createdAt)
            })
        }

        val groupArray = JSONArray()
        groups.forEachIndexed { index, group ->
            val members = JSONArray()
            val items = group.groupItems.orEmpty()
            items.forEach { item ->
                val deviceId = item.hardwareDevice?.ApName?.takeIf { it.isNotBlank() }
                    ?: item.hardwareDevice?.rowId?.toString()
                if (!deviceId.isNullOrBlank()) {
                    members.put(JSONObject().apply {
                        put("deviceId", deviceId)
                        put("pixelId", item.PixelID)
                        put("isMaster", item.GState.equals("M", ignoreCase = true))
                    })
                }
            }
            val groupId = if (group.allDevices) "all-devices" else "android-${group.rowId ?: index}"
            val gPort = if (group.allDevices) 8890 else items.firstOrNull()?.Gport?.toIntOrNull() ?: 10000 + index
            val gUniverse = group.GUniverse.takeIf { it in 32000..32500 } ?: items.asSequence()
                .mapNotNull { item ->
                    val raw = item.hardwareDevice?.deviceFrame.orEmpty()
                    if (raw.isBlank()) null else runCatching { JSONObject(raw).optInt("GUniverse", 32000) }.getOrNull()
                }
                .firstOrNull { it in 32000..32500 } ?: 32000

            groupArray.put(JSONObject().apply {
                put("id", groupId)
                put("name", group.groupTitle)
                put("members", members)
                put("gPort", gPort)
                put("gUniverse", gUniverse)
            })
        }

        val savedColors = exportSavedColors(context)
        val matrixPresets = exportMatrixPresets(context)
        val musicExtension = exportMusicExtension(context)

        return JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("devices", deviceArray)
            put("groups", groupArray)
            put("savedColors", savedColors)
            put("legacyPresets", JSONObject())
            put("matrixPresets", matrixPresets)
            put("androidMusic", musicExtension)
        }
    }

    fun validateConfiguration(configuration: JSONObject): Boolean {
        return configuration.optInt("formatVersion", -1) == FORMAT_VERSION &&
            configuration.optJSONArray("devices") != null &&
            configuration.optJSONArray("groups") != null
    }

    fun importConfiguration(context: Context, configuration: JSONObject): Boolean {
        if (!validateConfiguration(configuration)) return false

        val db = AppDatabase.getDatabase(context)
        val hardwareDao = db.getHardwareTable()
        val groupDao = db.getGroupTable()

        val devicesJson = configuration.optJSONArray("devices") ?: JSONArray()
        val groupsJson = configuration.optJSONArray("groups") ?: JSONArray()
        val usedGroupPorts = HashSet<Int>()

        groupDao.deleteHardwareGroupItems()
        groupDao.getHardwareGroupList().forEach { groupDao.deleteItem(it) }
        hardwareDao.deleteDevices()

        val deviceMap = LinkedHashMap<String, HardwareDevice>()
        for (i in 0 until devicesJson.length()) {
            val o = devicesJson.optJSONObject(i) ?: continue
            val id = o.optString("id", o.optString("APName", "")).trim()
            if (id.isBlank()) continue
            val d = HardwareDevice().apply {
                ApName = o.optString("APName", id).ifBlank { id }
                devName = o.optString("DevName", "")
                ip = o.optString("IP", "").ifBlank { null }
                port = o.optLong("Port", 0)
                dMXAddress = o.optLong("DMXAddress", 0)
                nProt = o.optString("NProt", "")
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            }
            val row = hardwareDao.insert(d)
            if (row > 0) d.rowId = row
            else {
                val existing = hardwareDao.getHardwareDevice(d.ApName ?: id)
                if (existing != null) d.rowId = existing.rowId
            }
            if (d.rowId != null) deviceMap[id] = d
        }

        var allDevicesCreated = false
        for (i in 0 until groupsJson.length()) {
            val g = groupsJson.optJSONObject(i) ?: continue
            val name = g.optString("name", "Group ${i + 1}").ifBlank { "Group ${i + 1}" }
            val allDevices = g.optString("id", "").equals("all-devices", true) ||
                name.equals("All Devices", true)
            val group = HardwareGroup().apply {
                groupTitle = if (allDevices) "All Devices" else name
                this.allDevices = allDevices
                GUniverse = g.optInt("gUniverse", 32000).takeIf { it in 32000..32500 } ?: 32000
            }
            group.rowId = groupDao.addItem(group)
            if (group.rowId == null || group.rowId == -1L) continue
            if (allDevices) allDevicesCreated = true

            val members = g.optJSONArray("members") ?: JSONArray()
            val importedPort = g.optInt("gPort", 0)
            val gPort = if (allDevices) {
                "8890"
            } else {
                var selectedPort = if (importedPort in 10000..65535 && !usedGroupPorts.contains(importedPort)) importedPort else 0
                if (selectedPort == 0) {
                    selectedPort = 10000
                    while (usedGroupPorts.contains(selectedPort) || selectedPort == 8889 || selectedPort == 8890) selectedPort++
                }
                usedGroupPorts.add(selectedPort)
                selectedPort.toString()
            }
            val groupItems = ArrayList<HardwareGroupItem>()
            for (m in 0 until members.length()) {
                val member = members.optJSONObject(m) ?: continue
                val deviceId = member.optString("deviceId", "").trim()
                val device = deviceMap[deviceId] ?: continue
                groupItems.add(HardwareGroupItem().apply {
                    groupId = group.rowId
                    hardwareDevice = device
                    Gport = gPort
                    GState = if (allDevices) "X" else if (member.optBoolean("isMaster", false)) "M" else "X"
                    PixelID = member.optInt("pixelId", m)
                    selected = true
                })
            }
            if (groupItems.isNotEmpty()) {
                if (!allDevices && groupItems.count { it.GState == "M" } == 1) {
                    groupItems.forEach { if (it.GState != "M") it.GState = "S" }
                } else if (!allDevices) {
                    groupItems.forEach { it.GState = "X" }
                }
                groupDao.insertGroupDevices(groupItems)
            }
        }

        if (!allDevicesCreated) {
            val all = HardwareGroup().apply {
                groupTitle = "All Devices"
                allDevices = true
                GUniverse = 32000
            }
            all.rowId = groupDao.addItem(all)
            val allItems = deviceMap.values.mapIndexed { index, d ->
                HardwareGroupItem().apply {
                    groupId = all.rowId
                    hardwareDevice = d
                    Gport = "8890"
                    GState = "X"
                    PixelID = index
                    selected = true
                }
            }
            if (allItems.isNotEmpty()) groupDao.insertGroupDevices(allItems)
        }

        importSavedColors(context, configuration.optJSONArray("savedColors"))
        importMatrixPresets(context, configuration.optJSONObject("matrixPresets"))
        importMusicExtension(context, configuration.optJSONObject("androidMusic"))
        return true
    }

    fun resetToDefaults(context: Context) {
        // Reset controller/effect settings only. Devices, groups, GPorts, Pixel IDs
        // and network configuration are intentionally preserved.
        KeyStorage.getKeyStorage(context).clear()

        // Legacy Effects use one shared Speed store plus one parameter store per effect.
        // Clear both so Reset to Default restores the same defaults as a fresh PC App.
        context.getSharedPreferences("mobiled_legacy_effect_settings", Context.MODE_PRIVATE)
            .edit().clear().apply()
        (1..12).forEach { id ->
            context.getSharedPreferences("mobiled_legacy_effect_params_$id", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }

        context.getSharedPreferences(MATRIX_RUNTIME_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        matrixIds.forEach { id ->
            context.getSharedPreferences("matrix_colors_$id", Context.MODE_PRIVATE).edit().clear().apply()
        }
        context.getSharedPreferences("matrix_presets", Context.MODE_PRIVATE).edit().clear().apply()

        musicKinds.forEach { kind ->
            context.getSharedPreferences("music_runtime_$kind", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("music_colors_$kind", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("music_presets_$kind", Context.MODE_PRIVATE).edit().clear().apply()
        }
        context.getSharedPreferences("music_spectrum_colors", Context.MODE_PRIVATE).edit().clear().apply()

        // Reset Levels to Default from the PC/Mac Music reference:
        // three active colors at hue 0°, 36°, 72° and Fade transition;
        // Spectrum uses the first three bands Low/Mid/High.
        musicKinds.forEach { kind -> writeMusicDefaults(context, kind) }
    }

    private fun exportSavedColors(context: Context): JSONArray {
        val result = JSONArray()
        val prefs = context.getSharedPreferences(SDK_PREFS, Context.MODE_PRIVATE)
        for (i in 1..10) {
            val raw = prefs.getString("viewColor$i", "") ?: ""
            if (raw.isBlank()) {
                result.put(JSONObject.NULL)
                continue
            }
            val p = raw.split(",").mapNotNull { it.toIntOrNull() }
            if (p.size >= 6) {
                result.put(JSONObject().apply {
                    put("r", p[1].coerceIn(0,255))
                    put("g", p[2].coerceIn(0,255))
                    put("b", p[3].coerceIn(0,255))
                    put("brightness", p[4].coerceIn(0,100))
                    put("w", (p[5].coerceIn(0,100) * 255f / 100f).roundToInt())
                })
            } else result.put(JSONObject.NULL)
        }
        return result
    }

    private fun importSavedColors(context: Context, colors: JSONArray?) {
        val storage = KeyStorage.getKeyStorage(context)
        for (i in 1..10) {
            val c = colors?.optJSONObject(i - 1)
            if (c == null) {
                storage.setStringValue("viewColor$i", "")
                continue
            }
            val r = c.optInt("r", 0); val g = c.optInt("g", 0); val b = c.optInt("b", 0)
            val brightness = c.optInt("brightness", 100).coerceIn(0,100)
            val white = if (c.has("whiteBrightness")) c.optInt("whiteBrightness", 0).coerceIn(0,100)
            else ((c.optInt("w", 0).coerceIn(0,255) * 100f) / 255f).roundToInt()
            storage.setStringValue("viewColor$i", "255,$r,$g,$b,$brightness,$white")
        }
    }

    private fun exportMatrixPresets(context: Context): JSONObject {
        val out = JSONObject()
        val prefs = context.getSharedPreferences("matrix_presets", Context.MODE_PRIVATE)
        matrixIds.forEach { id ->
            val array = JSONArray()
            for (slot in 0 until 5) {
                val raw = prefs.getString("$id.$slot", null)
                val data = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
                array.put(JSONObject().apply {
                    put("name", "Preset ${slot + 1}")
                    put("description", "")
                    put("data", data ?: JSONObject.NULL)
                })
            }
            out.put("mobiled.matrixPresets.effect.$id", array)
        }
        return out
    }

    private fun importMatrixPresets(context: Context, matrix: JSONObject?) {
        val prefs = context.getSharedPreferences("matrix_presets", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        if (matrix == null) return
        matrix.keys().forEach { key ->
            val id = Regex("mobiled\\.matrixPresets\\.effect\\.(\\d+)").matchEntire(key)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("mobiled\\.matrixPresets\\.(\\d+)").matchEntire(key)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@forEach
            val array = matrix.optJSONArray(key) ?: return@forEach
            for (slot in 0 until minOf(5, array.length())) {
                val entry = array.optJSONObject(slot) ?: continue
                val data = entry.optJSONObject("data") ?: entry
                if (data.length() > 0) prefs.edit().putString("$id.$slot", data.toString()).apply()
            }
        }
    }

    private fun exportMusicExtension(context: Context): JSONObject {
        val out = JSONObject()
        musicKinds.forEach { kind ->
            val cfg = JSONObject()
            cfg.put("runtime", context.getSharedPreferences("music_runtime_$kind", Context.MODE_PRIVATE).getString("state", ""))
            cfg.put("colors", JSONObject(context.getSharedPreferences("music_colors_$kind", Context.MODE_PRIVATE).all))
            cfg.put("presets", JSONObject(context.getSharedPreferences("music_presets_$kind", Context.MODE_PRIVATE).all))
            if (kind == "spectrum") cfg.put("spectrumColors", JSONObject(context.getSharedPreferences("music_spectrum_colors", Context.MODE_PRIVATE).all))
            out.put(kind, cfg)
        }
        return out
    }

    private fun importMusicExtension(context: Context, extension: JSONObject?) {
        if (extension == null) return
        musicKinds.forEach { kind ->
            val cfg = extension.optJSONObject(kind) ?: return@forEach
            val runtime = cfg.optString("runtime", "")
            if (runtime.isNotBlank()) context.getSharedPreferences("music_runtime_$kind", Context.MODE_PRIVATE).edit().putString("state", runtime).apply()
            restorePreferenceMap(context.getSharedPreferences("music_colors_$kind", Context.MODE_PRIVATE), cfg.optJSONObject("colors"))
            restorePreferenceMap(context.getSharedPreferences("music_presets_$kind", Context.MODE_PRIVATE), cfg.optJSONObject("presets"))
            if (kind == "spectrum") restorePreferenceMap(context.getSharedPreferences("music_spectrum_colors", Context.MODE_PRIVATE), cfg.optJSONObject("spectrumColors"))
        }
    }

    private fun restorePreferenceMap(prefs: android.content.SharedPreferences, source: JSONObject?) {
        if (source == null) return
        val edit = prefs.edit().clear()
        source.keys().forEach { key ->
            when (val v = source.opt(key)) {
                is Boolean -> edit.putBoolean(key, v)
                is Int -> edit.putInt(key, v)
                is Long -> edit.putLong(key, v)
                is Double -> edit.putFloat(key, v.toFloat())
                is String -> edit.putString(key, v)
            }
        }
        edit.apply()
    }

    private fun writeMusicDefaults(context: Context, kind: String) {
        val prefs = context.getSharedPreferences("music_colors_$kind", Context.MODE_PRIVATE)
        val edit = prefs.edit().clear()
        val hues = intArrayOf(0, 36, 72)
        hues.forEachIndexed { i, hue ->
            val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 1f, 1f))
            edit.putInt("$i.r", android.graphics.Color.red(rgb))
                .putInt("$i.g", android.graphics.Color.green(rgb))
                .putInt("$i.b", android.graphics.Color.blue(rgb))
                .putInt("$i.w", 0)
                .putBoolean("$i.saved", true)
        }
        edit.apply()
        if (kind == "spectrum") {
            context.getSharedPreferences("music_spectrum_colors", Context.MODE_PRIVATE).edit().clear().apply()
            val spectrum = context.getSharedPreferences("music_spectrum_colors", Context.MODE_PRIVATE).edit()
            hues.forEachIndexed { i, hue ->
                val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 1f, 1f))
                spectrum.putInt("$i.r", android.graphics.Color.red(rgb))
                    .putInt("$i.g", android.graphics.Color.green(rgb))
                    .putInt("$i.b", android.graphics.Color.blue(rgb))
                    .putInt("$i.w", 0)
                    .putBoolean("$i.saved", true)
                    .putInt("$i.band", i)
            }
            spectrum.apply()
        }
        context.getSharedPreferences("music_runtime_$kind", Context.MODE_PRIVATE).edit()
            .putString("state", JSONObject().put("brightness",100).put("white",0).put("transition","Fade").toString())
            .apply()
    }
}
