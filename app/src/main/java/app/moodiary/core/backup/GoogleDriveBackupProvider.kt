package app.moodiary.core.backup

import android.content.Context
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class CloudBackup(val id: String, val name: String, val modifiedAt: String)
interface BackupProvider {
    suspend fun list(): List<CloudBackup>
    suspend fun upload(file: File)
    suspend fun download(id: String, destination: File)
}

/** Optional Drive appDataFolder integration. Requires a developer-configured Android OAuth client
 * matching applicationId/signing SHA-1 and Drive API consent; no journal upload before sign-in. */
@Singleton class GoogleDriveBackupProvider @Inject constructor(@ApplicationContext private val context: Context) : BackupProvider {
    private val uploads = Mutex()
    val authorizationClient get() = Identity.getAuthorizationClient(context)
    private val grant = context.getSharedPreferences("drive_opt_in", Context.MODE_PRIVATE)
    private val request get() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
    fun authorize() = authorizationClient.authorize(request)
    fun connected() = grant.getBoolean("enabled", false)
    fun accept(result: AuthorizationResult) {
        require(!result.hasResolution() && !result.accessToken.isNullOrBlank()) { context.localizedString(R.string.core_drive_permission_missing) }
        check(grant.edit().putBoolean("enabled", true).commit())
    }
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        uploads.withLock {
            // Google's revocation endpoint accepts this short-lived access token; no account
            // identity scope or stored refresh token is needed to revoke the Drive grant.
            val accessToken = token()
            val conn = URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"; conn.connectTimeout = 30_000; conn.readTimeout = 60_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(("token=" + java.net.URLEncoder.encode(accessToken, "UTF-8")).toByteArray(Charsets.UTF_8)) }
                require(conn.responseCode == 200) { context.localizedString(R.string.core_drive_revoke_failed) }
                check(grant.edit().clear().commit())
                // Prevent a just-revoked token from being reused from the platform cache.
                Tasks.await(authorizationClient.clearToken(com.google.android.gms.auth.api.identity.ClearTokenRequest.builder().setToken(accessToken).build()), 30, java.util.concurrent.TimeUnit.SECONDS)
            } finally { conn.disconnect() }
        }
    }
    private fun token(): String {
        require(connected()) { context.localizedString(R.string.core_drive_connection_required) }
        val result = Tasks.await(authorize(), 60, java.util.concurrent.TimeUnit.SECONDS)
        require(!result.hasResolution()) { context.localizedString(R.string.core_drive_reauthorization_required) }
        return requireNotNull(result.accessToken) { context.localizedString(R.string.core_drive_token_missing) }
    }
    private fun connection(path: String, method: String = "GET"): HttpURLConnection = (URL("https://www.googleapis.com/$path").openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 30_000; readTimeout = 60_000; setRequestProperty("Authorization", "Bearer ${token()}")
    }
    private fun response(connection: HttpURLConnection): String {
        val status = connection.responseCode
        require(status in 200..299) { context.localizedString(R.string.core_drive_request_failed, status) }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
    override suspend fun list(): List<CloudBackup> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CloudBackup>()
        val seenTokens = mutableSetOf<String>()
        var page = ""
        do {
            val suffix = if (page.isBlank()) "" else "&pageToken=${java.net.URLEncoder.encode(page, "UTF-8")}"
            val conn = connection("drive/v3/files?spaces=appDataFolder&fields=nextPageToken,files(id,name,modifiedTime)&pageSize=100&orderBy=modifiedTime%20desc$suffix")
            try {
                val response = JSONObject(response(conn))
                val array = response.getJSONArray("files")
                for (index in 0 until array.length()) {
                    val file = array.getJSONObject(index)
                    if (file.getString("name").startsWith("moodiary-") && file.getString("name").endsWith(".zip"))
                        results += CloudBackup(file.getString("id"), file.getString("name"), file.optString("modifiedTime"))
                }
                page = response.optString("nextPageToken")
                require(page.isBlank() || seenTokens.add(page)) { context.localizedString(R.string.core_drive_repeated_page) }
            } finally { conn.disconnect() }
        } while (page.isNotBlank())
        results.distinctBy { it.id }
    }
    override suspend fun upload(file: File) = withContext(Dispatchers.IO) {
        uploads.withLock {
        // Stable per-day name and update-by-id make background retry idempotent, retaining a week.
        val name = "moodiary-${java.time.LocalDate.now(java.time.ZoneOffset.UTC)}.zip"
        val backups = list()
        val prior = backups.find { it.name == name }
        val boundary = "moodiary-${java.util.UUID.randomUUID()}"
        val path = if (prior == null) "upload/drive/v3/files?uploadType=multipart" else "upload/drive/v3/files/${prior.id}?uploadType=media"
        val conn = connection(path, if (prior == null) "POST" else "PATCH")
        try {
            conn.doOutput = true
            conn.setChunkedStreamingMode(64 * 1024)
            conn.setRequestProperty("Content-Type", if (prior == null) "multipart/related; boundary=$boundary" else "application/zip")
            conn.outputStream.use { output ->
                if (prior == null) output.write(("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")) + "\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n").toByteArray())
                file.inputStream().use { it.copyTo(output) }
                if (prior == null) output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            response(conn)
        } finally { conn.disconnect() }
        // Delete only this app's known dated automatic archives, after a successful upload.
        list().filter { it.name.matches(Regex("moodiary-[0-9]{4}-[0-9]{2}-[0-9]{2}\\.zip")) }.sortedByDescending { it.name }.drop(7).forEach { old ->
            val removal = connection("drive/v3/files/${old.id}", "DELETE")
            try { response(removal) } finally { removal.disconnect() }
        }
        }
    }
    override suspend fun download(id: String, destination: File) = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { context.localizedString(R.string.core_drive_invalid_file_id) }
        val conn = connection("drive/v3/files/$id?alt=media")
        try {
            require(conn.responseCode in 200..299) { context.localizedString(R.string.core_drive_download_failed) }
            conn.inputStream.use { input -> destination.outputStream().use { output -> val buffer = ByteArray(8192); var total = 0L; while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 2L * 1024 * 1024 * 1024) { context.localizedString(R.string.core_drive_size_limit) }; output.write(buffer, 0, n) } } }
        } finally { conn.disconnect() }
    }
    companion object { const val SCOPE = "https://www.googleapis.com/auth/drive.appdata" }
}
