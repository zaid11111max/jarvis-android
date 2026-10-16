package com.technicalbase.jarvis.bridge

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

class LocalServer(
    private val context: Context,
    port: Int = 8766,
    private val token: String = "change-me-on-first-boot"
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "Jarvis.LocalServer"

        @Volatile
        private var instance: LocalServer? = null

        fun start(ctx: Context, port: Int = 8766, token: String = "change-me-on-first-boot"): LocalServer {
            instance?.stop()
            val server = LocalServer(ctx.applicationContext, port, token)
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            instance = server
            Log.i(TAG, "started on 127.0.0.1:$port")
            return server
        }

        fun stop() {
            instance?.stop()
            instance = null
            Log.i(TAG, "stopped")
        }

        fun isRunning(): Boolean = instance != null
    }

    // ---- helpers ----
    private fun json(status: Response.Status, obj: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", obj.toString())

    private fun ok(extra: JSONObject = JSONObject()): Response {
        val o = JSONObject()
        o.put("ok", true)
        extra.keys().forEach { k -> o.put(k, extra.get(k)) }
        return json(Response.Status.OK, o)
    }

    private fun err(msg: String, code: Response.Status = Response.Status.BAD_REQUEST): Response {
        val o = JSONObject()
        o.put("ok", false)
        o.put("error", msg)
        return json(code, o)
    }

    private fun auth(s: IHTTPSession): Boolean = s.headers["x-jarvis-token"] == token

    private fun body(s: IHTTPSession): JSONObject {
        return try {
            val map = HashMap<String, String>()
            s.parseBody(map)
            JSONObject(map["postData"] ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    // ---- router ----
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        if (uri == "/health" && method == Method.GET) {
            val o = JSONObject()
            o.put("status", "ok")
            o.put("version", "0.1.0")
            o.put("bridge", "kotlin")
            return json(Response.Status.OK, o)
        }

        if (!auth(session)) return err("forbidden", Response.Status.FORBIDDEN)

        return when {
            uri == "/apps/url_handlers" && method == Method.GET -> handleUrlHandlers()
            uri == "/app/open" && method == Method.POST -> handleAppOpen(session)
            uri == "/app/force_stop" && method == Method.POST -> handleForceStop(session)
            uri == "/url/open" && method == Method.POST -> handleUrlOpen(session)
            uri == "/tts/speak" && method == Method.POST -> handleTtsSpeak(session)
            uri == "/tts/stop" && method == Method.POST -> handleTtsStop()
            uri == "/lockdown/start" && method == Method.POST -> handleLockdownStart(session)
            uri == "/lockdown/stop" && method == Method.POST -> handleLockdownStop()
            uri == "/lockdown/state" && method == Method.GET -> handleLockdownState()
            uri == "/access/foreground" && method == Method.GET -> handleForeground()
            uri == "/access/screen_text" && method == Method.GET -> handleScreenText()
            uri == "/access/focused_text" && method == Method.GET -> handleFocusedText()
            uri == "/access/click" && method == Method.POST -> err("accessibility service not enabled yet")
            uri == "/access/type" && method == Method.POST -> err("accessibility service not enabled yet")
            uri == "/access/back" && method == Method.POST -> err("accessibility service not enabled yet")
            uri == "/access/home" && method == Method.POST -> err("accessibility service not enabled yet")
            uri == "/os/lock" && method == Method.POST -> err("device admin not configured")
            uri == "/os/power" && method == Method.POST -> err("device admin not configured")
            uri == "/alert" && method == Method.POST -> handleAlert(session)
            else -> err("not found: $uri", Response.Status.NOT_FOUND)
        }
    }

    // ---- handlers ----
    private fun handleUrlHandlers(): Response {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        intent.data = android.net.Uri.parse("https://example.com")
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        val arr = JSONArray()
        val seen = mutableSetOf<String>()
        for (ri in handlers) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (seen.add(pkg)) arr.put(pkg)
        }
        return json(Response.Status.OK, JSONObject().apply {
            put("packages", arr)
            put("count", arr.length())
        })
    }

    private fun handleAppOpen(session: IHTTPSession): Response {
        val pkg = body(session).optString("package", "")
        if (pkg.isBlank()) return err("package required")
        return try {
            val i = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return err("no launch intent for $pkg", Response.Status.NOT_FOUND)
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            ok(JSONObject().put("package", pkg).put("via", "intent"))
        } catch (e: Exception) {
            err("launch failed: ${e.message}", Response.Status.INTERNAL_ERROR)
        }
    }

    private fun handleForceStop(session: IHTTPSession): Response {
        val pkg = body(session).optString("package", "")
        if (pkg.isBlank()) return err("package required")
        return err("force-stop requires accessibility service (next step)")
    }

    private fun handleUrlOpen(session: IHTTPSession): Response {
        val url = body(session).optString("url", "")
        if (url.isBlank()) return err("url required")
        return try {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
            i.data = android.net.Uri.parse(url)
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            ok(JSONObject().put("url", url))
        } catch (e: Exception) {
            err("open failed: ${e.message}", Response.Status.INTERNAL_ERROR)
        }
    }

    private fun handleTtsSpeak(session: IHTTPSession): Response {
        val text = body(session).optString("text", "")
        val lang = body(session).optString("lang", "en-IN")
        if (text.isBlank()) return err("text required")
        return try {
            val tts = android.speech.tts.TextToSpeech(context) { }
            tts.setOnUtteranceProgressListener(object :
                android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { tts.shutdown() }
                override fun onError(id: String?) { tts.shutdown() }
            })
            tts.language = java.util.Locale.forLanguageTag(lang)
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null,
                "jarvis-${System.currentTimeMillis()}")
            ok(JSONObject().put("spoken", true))
        } catch (e: Exception) {
            err("tts failed: ${e.message}", Response.Status.INTERNAL_ERROR)
        }
    }

    private fun handleTtsStop(): Response =
        ok(JSONObject().put("note", "no persistent tts handle"))

    private fun handleLockdownStart(session: IHTTPSession): Response =
        err("lockdown requires VPN service (next step)")

    private fun handleLockdownStop(): Response = err("lockdown not active")

    private fun handleLockdownState(): Response =
        json(Response.Status.OK, JSONObject().apply {
            put("active", false)
            put("kind", "none")
            put("deadline_ms", 0)
        })

    private fun handleForeground(): Response =
        json(Response.Status.OK, JSONObject().put("package", ""))

    private fun handleScreenText(): Response =
        json(Response.Status.OK, JSONObject().put("text", ""))

    private fun handleFocusedText(): Response =
        json(Response.Status.OK, JSONObject().put("text", ""))

    private fun handleAlert(session: IHTTPSession): Response {
        val b = body(session)
        val title = b.optString("title", "Jarvis")
        val content = b.optString("body", "")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        val channelId = "jarvis_alert"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId, "Jarvis Alerts",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
        return ok(JSONObject().put("notified", true))
    }
}
