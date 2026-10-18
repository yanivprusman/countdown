package com.automatelinux.countdown.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One HTTP round trip. Android supplies it with the JDK client; iOS will supply
 *  NSURLSession. Kept small so neither platform grows a second place where a
 *  request can be built differently. */
expect suspend fun httpRequest(
    method: String,
    url: String,
    token: String,
    jsonBody: String?,
): HttpResult

data class HttpResult(val code: Int, val body: String, val transportError: String? = null)

@Serializable
data class CountdownsResponse(
    val ok: Boolean = false,
    val countdowns: List<Countdown> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class Ack(val ok: Boolean = false, val error: String? = null)

@Serializable
private data class TickBody(val id: String, val note: String, val at: Long)

@Serializable
private data class NoteBody(val note: String)

/**
 * The app's whole conversation with the backend.
 *
 * Failures are values, never exceptions thrown at the UI: this gets tapped walking
 * out of work, so "the VPN is not up" has to render as a sentence rather than as a
 * crash or a spinner that never ends.
 */
class CountdownApi(baseUrl: String, private val token: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    val configured: Boolean get() = token.isNotEmpty()

    suspend fun list(): CountdownsResponse {
        if (!configured) return CountdownsResponse(error = NO_TOKEN)
        val r = httpRequest("GET", "$base/api/countdowns", token, null)
        return runCatching { json.decodeFromString(CountdownsResponse.serializer(), r.body) }
            .getOrElse { CountdownsResponse(error = describe(r)) }
    }

    /** Create, or put back. With ticks it lands exactly as it was — original ids and
     *  times — which is what Undo and Duplicate both need. */
    suspend fun saveCountdown(c: Countdown): String? =
        call("POST", "/api/countdowns", json.encodeToString(Countdown.serializer(), c))

    /** Name, start, unit, colour and the once-a-day rule — never the ticks. */
    suspend fun updateCountdown(c: Countdown): String? =
        call("PATCH", "/api/countdowns/${c.id}", json.encodeToString(Countdown.serializer(), c.copy(ticks = emptyList())))

    suspend fun deleteCountdown(id: String): String? = call("DELETE", "/api/countdowns/$id", null)

    suspend fun resetCountdown(id: String): String? = call("POST", "/api/countdowns/$id/reset", "{}")

    suspend fun addTick(countdownId: String, t: Tick): String? =
        call("POST", "/api/countdowns/$countdownId/ticks", json.encodeToString(TickBody.serializer(), TickBody(t.id, t.note, t.at)))

    suspend fun updateNote(tickId: String, note: String): String? =
        call("PATCH", "/api/ticks/$tickId", json.encodeToString(NoteBody.serializer(), NoteBody(note)))

    suspend fun deleteTick(id: String): String? = call("DELETE", "/api/ticks/$id", null)

    /** Null means it worked; anything else is a sentence to put on screen. */
    private suspend fun call(method: String, path: String, body: String?): String? {
        if (!configured) return NO_TOKEN
        val r = httpRequest(method, "$base$path", token, body)
        val ack = runCatching { json.decodeFromString(Ack.serializer(), r.body) }.getOrNull()
        return if (ack?.ok == true) null else ack?.error ?: describe(r)
    }

    /** The status code alone is useless to someone holding a phone. */
    private fun describe(r: HttpResult): String = when (r.code) {
        401 -> "This build is not authorised by the server"
        0 -> "No connection — is the VPN up?"
        in 500..599 -> "The server could not reach its database"
        else -> "Unexpected reply from the server (${r.code})"
    }

    private companion object {
        const val NO_TOKEN = "Built without an API token — rebuild with mobile/.env"
    }
}
