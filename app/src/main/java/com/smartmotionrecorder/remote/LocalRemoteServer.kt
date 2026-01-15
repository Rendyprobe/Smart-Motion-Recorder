package com.smartmotionrecorder.remote

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.smartmotionrecorder.util.SnapshotRepository

class LocalRemoteServer(
    private val handler: Handler
) {
    interface Handler {
        fun onStatus(): RemoteStatus
        fun onStart(): RemoteResult
        fun onStop(): RemoteResult
        fun onGetSchedule(): ScheduleSettings
        fun onUpdateSchedule(schedule: ScheduleSettings): RemoteResult
        fun onToggleCamera(): RemoteResult
    }

    data class RemoteStatus(
        val monitoring: Boolean,
        val mode: String?,
        val coins: Int,
        val lastFile: String?,
        val useBackCamera: Boolean
    )

    data class ScheduleSettings(
        val enabled: Boolean,
        val startMinutes: Int,
        val endMinutes: Int,
        val backgroundOnly: Boolean
    )

    data class RemoteResult(
        val ok: Boolean,
        val message: String
    )

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var pin: String? = null

    fun start(port: Int, pin: String?) {
        if (running) return
        this.pin = pin?.takeIf { it.isNotBlank() }
        running = true
        executor = Executors.newCachedThreadPool()
        executor?.execute {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    executor?.execute { handleClient(socket) }
                }
            } catch (_: Exception) {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        executor?.shutdownNow()
        executor = null
        serverSocket = null
    }

    fun isRunning(): Boolean = running

    private fun handleClient(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val uri = parts[1]
            val path = uri.substringBefore("?")
            val query = parseQuery(uri.substringAfter("?", ""))
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(":")
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }
            val pinHeader = headers["x-pin"]
            val pinQuery = query["pin"]
            if (pin != null && pin != pinHeader && pin != pinQuery) {
                writeResponse(it, 403, "application/json", """{"ok":false,"message":"PIN salah"}""")
                return
            }
            when (path) {
                "/" -> writeResponse(it, 200, "text/html", buildHtmlPage())
                "/status" -> {
                    val status = handler.onStatus()
                    val camera = if (status.useBackCamera) "back" else "front"
                    val body = """{"monitoring":${status.monitoring},"mode":"${status.mode ?: ""}","coins":${status.coins},"lastFile":"${status.lastFile ?: ""}","camera":"$camera"}"""
                    writeResponse(it, 200, "application/json", body)
                }
                "/snapshot" -> {
                    val jpeg = SnapshotRepository.get()
                    if (jpeg == null) {
                        writeResponse(it, 204, "application/json", """{"ok":false}""")
                    } else {
                        writeBinaryResponse(it, 200, "image/jpeg", jpeg)
                    }
                }
                "/start" -> {
                    val result = handler.onStart()
                    writeResponse(it, 200, "application/json", """{"ok":${result.ok},"message":"${result.message}"}""")
                }
                "/stop" -> {
                    val result = handler.onStop()
                    writeResponse(it, 200, "application/json", """{"ok":${result.ok},"message":"${result.message}"}""")
                }
                "/switch" -> {
                    val result = handler.onToggleCamera()
                    writeResponse(it, 200, "application/json", """{"ok":${result.ok},"message":"${result.message}"}""")
                }
                "/schedule" -> {
                    if (method == "POST") {
                        val current = handler.onGetSchedule()
                        val enabled = parseBool(query["enabled"]) ?: current.enabled
                        val start = parseTimeToMinutes(query["start"])?.coerceIn(0, 1439) ?: current.startMinutes
                        val end = parseTimeToMinutes(query["end"])?.coerceIn(0, 1439) ?: current.endMinutes
                        val bg = parseBool(query["bg"]) ?: current.backgroundOnly
                        val result = handler.onUpdateSchedule(
                            ScheduleSettings(
                                enabled = enabled,
                                startMinutes = start,
                                endMinutes = end,
                                backgroundOnly = bg
                            )
                        )
                        writeResponse(it, 200, "application/json", """{"ok":${result.ok},"message":"${result.message}"}""")
                    } else {
                        val schedule = handler.onGetSchedule()
                        val body = """{"enabled":${schedule.enabled},"startMinutes":${schedule.startMinutes},"endMinutes":${schedule.endMinutes},"backgroundOnly":${schedule.backgroundOnly}}"""
                        writeResponse(it, 200, "application/json", body)
                    }
                }
                "/ping" -> writeResponse(it, 200, "application/json", """{"ok":true}""")
                else -> writeResponse(it, 404, "application/json", """{"ok":false,"message":"Not found"}""")
            }
        }
    }

    private fun writeResponse(socket: Socket, code: Int, contentType: String, body: String) {
        val out = PrintWriter(socket.getOutputStream())
        out.print("HTTP/1.1 $code OK\r\n")
        out.print("Content-Type: $contentType\r\n")
        out.print("Content-Length: ${body.toByteArray().size}\r\n")
        out.print("Access-Control-Allow-Origin: *\r\n")
        out.print("Cache-Control: no-store\r\n")
        out.print("Connection: close\r\n")
        out.print("\r\n")
        out.print(body)
        out.flush()
    }

    private fun writeBinaryResponse(socket: Socket, code: Int, contentType: String, body: ByteArray) {
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 $code OK\r\n".toByteArray())
        out.write("Content-Type: $contentType\r\n".toByteArray())
        out.write("Content-Length: ${body.size}\r\n".toByteArray())
        out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
        out.write("Cache-Control: no-store\r\n".toByteArray())
        out.write("Connection: close\r\n".toByteArray())
        out.write("\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { part ->
            val idx = part.indexOf("=")
            if (idx <= 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
    }

    private fun parseTimeToMinutes(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return if (value.contains(":")) {
            val parts = value.split(":")
            if (parts.size < 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            (hour.coerceIn(0, 23) * 60) + minute.coerceIn(0, 59)
        } else {
            value.filter { it.isDigit() }.toIntOrNull()
        }
    }

    private fun parseBool(value: String?): Boolean? {
        if (value.isNullOrBlank()) return null
        return when (value.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
    }

    private fun buildHtmlPage(): String {
        return """
            <html>
            <head>
                <meta charset="utf-8"/>
                <title>Motion Recorder</title>
                <style>
                    body { font-family: sans-serif; padding: 16px; background: #0d1b2a; color: #e0e1dd; }
                    button { padding: 10px 16px; margin: 6px; }
                    .card { background: #1b263b; padding: 12px; border-radius: 12px; }
                    .preview { margin-top: 10px; background: #0b1320; padding: 8px; border-radius: 12px; }
                    .section { margin-top: 12px; }
                    .row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
                    label { display: inline-flex; gap: 6px; align-items: center; }
                    input[type="time"] { padding: 6px 8px; border-radius: 6px; border: 1px solid #2b3a55; background: #0b1320; color: #e0e1dd; }
                    img { width: 100%; max-width: 640px; border-radius: 8px; }
                </style>
            </head>
            <body>
                <h2>Kontrol Remote Lokal</h2>
                <div class="card">
                    <div id="status">Status: -</div>
                    <button onclick="send('/start')">Start BG</button>
                    <button onclick="send('/stop')">Stop</button>
                    <button onclick="send('/switch')">Switch Camera</button>
                    <button onclick="loadStatus()">Refresh</button>
                    <div class="section">
                        <div><strong>Jadwal</strong></div>
                        <div class="row">
                            <label><input type="checkbox" id="scheduleEnabled"> Aktif</label>
                            <label>Mulai <input type="time" id="scheduleStart"></label>
                            <label>Selesai <input type="time" id="scheduleEnd"></label>
                            <label><input type="checkbox" id="scheduleBg"> BG saja</label>
                            <button onclick="saveSchedule()">Simpan jadwal</button>
                        </div>
                        <div style="font-size: 12px; opacity: 0.8; margin-top: 4px;">
                            Catatan: jadwal berjalan saat aplikasi aktif.
                        </div>
                    </div>
                    <div class="preview">
                        <div>Snapshot:</div>
                        <img id="snapshot" src="" alt="snapshot"/>
                    </div>
                </div>
                <script>
                    const params = new URLSearchParams(window.location.search);
                    const pin = params.get('pin');
                    function withPin(path) {
                        return pin ? (path + (path.includes('?') ? '&' : '?') + 'pin=' + encodeURIComponent(pin)) : path;
                    }
                    async function send(path) {
                        await fetch(withPin(path), {method: 'POST'});
                        loadStatus();
                    }
                    async function loadStatus() {
                        const res = await fetch(withPin('/status'));
                        const json = await res.json();
                        const camText = json.camera ? (' | Cam: ' + json.camera) : '';
                        document.getElementById('status').innerText =
                            'Status: ' + (json.monitoring ? ('ON (' + json.mode + ')') : 'OFF') + ' | Koin: ' + json.coins + camText;
                    }
                    function minutesToTime(mins) {
                        const m = ((mins % 1440) + 1440) % 1440;
                        const h = Math.floor(m / 60);
                        const mm = m % 60;
                        return String(h).padStart(2, '0') + ':' + String(mm).padStart(2, '0');
                    }
                    async function loadSchedule() {
                        const res = await fetch(withPin('/schedule'));
                        if (!res.ok) return;
                        const json = await res.json();
                        document.getElementById('scheduleEnabled').checked = !!json.enabled;
                        document.getElementById('scheduleStart').value = minutesToTime(json.startMinutes || 0);
                        document.getElementById('scheduleEnd').value = minutesToTime(json.endMinutes || 0);
                        document.getElementById('scheduleBg').checked = !!json.backgroundOnly;
                    }
                    async function saveSchedule() {
                        const enabled = document.getElementById('scheduleEnabled').checked ? 1 : 0;
                        const start = document.getElementById('scheduleStart').value;
                        const end = document.getElementById('scheduleEnd').value;
                        const bg = document.getElementById('scheduleBg').checked ? 1 : 0;
                        const url = withPin('/schedule?enabled=' + enabled + '&start=' + encodeURIComponent(start) + '&end=' + encodeURIComponent(end) + '&bg=' + bg);
                        await fetch(url, {method: 'POST'});
                        loadSchedule();
                    }
                    function refreshSnapshot() {
                        const img = document.getElementById('snapshot');
                        const base = withPin('/snapshot');
                        img.src = base + (base.includes('?') ? '&' : '?') + 't=' + Date.now();
                    }
                    loadStatus();
                    loadSchedule();
                    refreshSnapshot();
                    setInterval(loadStatus, 5000);
                    setInterval(refreshSnapshot, 1000);
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
