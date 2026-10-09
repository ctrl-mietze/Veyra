package ctrl.mietze.veyraroot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal data class VeyraApiConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val clientId: String,
    val accountUrl: String,
)

internal data class VeyraApiSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    fun expiresSoon(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        expiresAtEpochSeconds <= nowEpochSeconds + 90L
}

internal data class VeyraAccount(
    val id: String,
    val username: String,
    val displayName: String,
    val roles: Set<String>,
    val entitlements: Set<String>,
)

internal data class VeyraLicense(
    val id: String,
    val product: String,
    val status: String,
    val expiresAtEpochSeconds: Long?,
)

internal data class VeyraDeviceRegistration(
    val id: String,
    val trusted: Boolean,
)

internal object VeyraApiSessionStore {
    private const val PREFS = "veyra_api_session"
    private const val VALUE = "encrypted_session"
    private const val KEY_ALIAS = "veyra_api_session_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(context: Context, session: VeyraApiSession) {
        val plain = JSONObject()
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("expiresAt", session.expiresAtEpochSeconds)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain)
        val stored =
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(VALUE, stored)
            .apply()
    }

    fun load(context: Context): VeyraApiSession? = runCatching {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(VALUE, null)
            ?: return null
        val parts = stored.split(':', limit = 2)
        require(parts.size == 2) { "Invalid session envelope" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, iv),
        )
        val json = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        VeyraApiSession(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresAtEpochSeconds = json.getLong("expiresAt"),
        )
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(VALUE)
            .apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

internal object VeyraWebApi {
    private const val CONFIG_ASSET = "api/veyra-api-v1.json"
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000

    fun config(context: Context): VeyraApiConfig {
        val raw = context.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        require(json.optInt("schemaVersion") == 1) { "Unsupported Veyra API config" }
        val baseUrl = json.getString("baseUrl").trimEnd('/')
        require(baseUrl.startsWith("https://")) { "Veyra API must use HTTPS" }
        return VeyraApiConfig(
            enabled = json.optBoolean("enabled", false),
            baseUrl = baseUrl,
            clientId = json.optString("clientId", "veyra-root"),
            accountUrl = json.optString("accountUrl"),
        )
    }

    fun login(
        context: Context,
        username: String,
        password: String,
    ): VeyraAccount {
        val cfg = requireEnabled(context)
        require(username.isNotBlank()) { "Username is required" }
        require(password.isNotEmpty()) { "Password is required" }

        val response = request(
            cfg = cfg,
            method = "POST",
            path = "/auth/login",
            body = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put("clientId", cfg.clientId),
            bearer = null,
        )
        val session = parseSession(response.getJSONObject("session"))
        VeyraApiSessionStore.save(context, session)
        return parseAccount(response.getJSONObject("account"))
    }

    fun createAccount(
        context: Context,
        username: String,
        password: String,
        displayName: String,
    ): VeyraAccount {
        val cfg = requireEnabled(context)
        require(username.isNotBlank()) { "Username is required" }
        require(password.length >= 8) { "Password must be at least 8 characters" }

        val response = request(
            cfg = cfg,
            method = "POST",
            path = "/auth/register",
            body = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put("displayName", displayName.trim())
                .put("clientId", cfg.clientId),
            bearer = null,
        )
        val session = parseSession(response.getJSONObject("session"))
        VeyraApiSessionStore.save(context, session)
        return parseAccount(response.getJSONObject("account"))
    }

    fun currentAccount(context: Context): VeyraAccount {
        val response = authenticated(context, "GET", "/account/me", null)
        return parseAccount(response)
    }

    fun marketEntitlements(context: Context): Set<String> {
        val response = authenticated(context, "GET", "/market/entitlements", null)
        return response.optJSONArray("entitlements").strings().toSet()
    }

    fun licenses(context: Context): List<VeyraLicense> {
        val response = authenticated(context, "GET", "/licenses", null)
        val array = response.optJSONArray("licenses") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    VeyraLicense(
                        id = item.optString("id"),
                        product = item.optString("product"),
                        status = item.optString("status"),
                        expiresAtEpochSeconds = item.optLong("expiresAt", 0L)
                            .takeIf { it > 0L },
                    ),
                )
            }
        }
    }

    fun registerDevice(
        context: Context,
        snapshot: DeviceSnapshot = DeviceSnapshot.current(),
    ): VeyraDeviceRegistration {
        val response = authenticated(
            context,
            "POST",
            "/devices",
            JSONObject()
                .put("clientId", config(context).clientId)
                .put("packageName", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("manufacturer", snapshot.manufacturer)
                .put("model", snapshot.model)
                .put("device", snapshot.device)
                .put("buildId", snapshot.buildId)
                .put("fingerprint", snapshot.fingerprint)
                .put("kernelRelease", snapshot.kernelRelease),
        )
        return VeyraDeviceRegistration(
            id = response.getString("id"),
            trusted = response.optBoolean("trusted", false),
        )
    }

    fun logout(context: Context) {
        val session = VeyraApiSessionStore.load(context)
        val cfg = runCatching { requireEnabled(context) }.getOrNull()
        if (session != null && cfg != null) {
            runCatching {
                request(
                    cfg,
                    "POST",
                    "/auth/logout",
                    JSONObject().put("refreshToken", session.refreshToken),
                    session.accessToken,
                )
            }
        }
        VeyraApiSessionStore.clear(context)
    }

    private fun authenticated(
        context: Context,
        method: String,
        path: String,
        body: JSONObject?,
    ): JSONObject {
        val cfg = requireEnabled(context)
        var session = VeyraApiSessionStore.load(context)
            ?: error("No Veyra account session")

        if (session.expiresSoon()) {
            session = refresh(cfg, session)
            VeyraApiSessionStore.save(context, session)
        }

        return try {
            request(cfg, method, path, body, session.accessToken)
        } catch (error: VeyraApiHttpException) {
            if (error.status != 401) throw error
            session = refresh(cfg, session)
            VeyraApiSessionStore.save(context, session)
            request(cfg, method, path, body, session.accessToken)
        }
    }

    private fun refresh(
        cfg: VeyraApiConfig,
        current: VeyraApiSession,
    ): VeyraApiSession {
        val response = request(
            cfg = cfg,
            method = "POST",
            path = "/auth/refresh",
            body = JSONObject()
                .put("refreshToken", current.refreshToken)
                .put("clientId", cfg.clientId),
            bearer = null,
        )
        return parseSession(response.getJSONObject("session"))
    }

    private fun request(
        cfg: VeyraApiConfig,
        method: String,
        path: String,
        body: JSONObject?,
        bearer: String?,
    ): JSONObject {
        require(path.startsWith("/")) { "API path must be absolute" }
        val connection = (URL(cfg.baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE}")
            setRequestProperty("X-Veyra-Client", cfg.clientId)
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = stream?.use { input ->
                readBounded(input, MAX_RESPONSE_BYTES)
            }.orEmpty()

            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(responseBody).optString("message")
                }.getOrNull().orEmpty()
                throw VeyraApiHttpException(
                    status,
                    message.ifBlank { "Veyra API HTTP $status" },
                )
            }
            if (responseBody.isBlank()) return JSONObject()
            return JSONObject(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun requireEnabled(context: Context): VeyraApiConfig =
        config(context).also {
            require(it.enabled) { "Veyra account API is not enabled on this build yet" }
        }

    private fun parseSession(json: JSONObject): VeyraApiSession =
        VeyraApiSession(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresAtEpochSeconds = json.getLong("expiresAt"),
        )

    private fun parseAccount(json: JSONObject): VeyraAccount =
        VeyraAccount(
            id = json.getString("id"),
            username = json.getString("username"),
            displayName = json.optString("displayName", json.getString("username")),
            roles = json.optJSONArray("roles").strings().toSet(),
            entitlements = json.optJSONArray("entitlements").strings().toSet(),
        )

    private fun readBounded(
        input: java.io.InputStream,
        maxBytes: Int,
    ): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Veyra API response is too large" }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
}

internal class VeyraApiHttpException(
    val status: Int,
    override val message: String,
) : IllegalStateException(message)
