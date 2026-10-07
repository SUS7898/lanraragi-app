package com.sus7898.lrrviewer.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import com.sus7898.lrrviewer.BuildConfig
import com.sus7898.lrrviewer.update.Checksums.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Serializable
data class GhAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
    val digest: String? = null,
    val content_type: String? = null,
)

@Serializable
data class GhRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

data class ReleaseInfo(
    val tag: String,
    val version: Version,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val checksumUrl: String?,
    val digestSha256: String?,
    val publishedAt: String?,
)

/**
 * Self-update via GitHub Releases.
 *
 * Flow: check latest release -> download APK (streamed, SHA-256 computed on the fly)
 * -> verify checksum (release asset `SHA256SUMS.txt` or GitHub's asset digest)
 * -> verify the APK is signed with the *same certificate* as the running app and has the
 *    same package name and a higher versionCode -> hand over to PackageInstaller.
 *
 * Android itself also refuses to update an app with a differently-signed APK; the extra
 * check here just fails early with a clear message.
 */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val checkedAt: Long) : State
        data class Available(val release: ReleaseInfo) : State
        data class Downloading(val release: ReleaseInfo, val progress: Float) : State
        data class Verifying(val release: ReleaseInfo) : State
        data class ReadyToInstall(val release: ReleaseInfo, val file: File) : State
        data class Installing(val release: ReleaseInfo) : State
        data class Error(val message: String, val release: ReleaseInfo? = null) : State
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** True when the current [state] came from an automatic (startup) check and should be surfaced as a dialog. */
    @Volatile var autoPrompt: Boolean = false
        private set

    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersion: Version? = Version.parse(BuildConfig.VERSION_NAME)
    val repoOwner: String = BuildConfig.UPDATE_REPO_OWNER
    val repoName: String = BuildConfig.UPDATE_REPO_NAME
    val repoUrl: String = "https://github.com/$repoOwner/$repoName"
    val releasesUrl: String = "$repoUrl/releases"

    private val updateDir: File get() = File(context.cacheDir, "updates")
    private val maxApkBytes = 200L * 1024 * 1024

    fun dismiss() {
        autoPrompt = false
        _state.value = State.Idle
    }

    // ------------------------------------------------------------------ check

    suspend fun check(auto: Boolean = false): ReleaseInfo? = withContext(Dispatchers.IO) {
        autoPrompt = auto
        _state.value = State.Checking
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "LRRViewer/$currentVersionName")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    _state.value = State.UpToDate(System.currentTimeMillis())
                    return@withContext null
                }
                if (!response.isSuccessful) throw IOException("GitHub API 오류 (HTTP ${response.code})")
                response.body?.string().orEmpty()
            }
            val release = json.decodeFromString<GhRelease>(body)
            val info = release.toReleaseInfo()
            val current = currentVersion
            if (info != null && current != null && info.version > current) {
                _state.value = State.Available(info)
                info
            } else {
                autoPrompt = false
                _state.value = State.UpToDate(System.currentTimeMillis())
                null
            }
        } catch (e: Exception) {
            autoPrompt = false
            _state.value = State.Error("업데이트 확인 실패: ${e.message}")
            null
        }
    }

    private fun GhRelease.toReleaseInfo(): ReleaseInfo? {
        if (draft) return null
        val version = Version.parse(tag_name) ?: return null
        val apk = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        val checksum = assets.firstOrNull {
            it.name.equals("SHA256SUMS.txt", ignoreCase = true) ||
                it.name.equals("SHA256SUMS", ignoreCase = true) ||
                it.name.equals("${apk.name}.sha256", ignoreCase = true)
        }
        return ReleaseInfo(
            tag = tag_name,
            version = version,
            title = name.ifBlank { tag_name },
            notes = body.orEmpty(),
            htmlUrl = html_url,
            apkName = apk.name,
            apkUrl = apk.browser_download_url,
            apkSize = apk.size,
            checksumUrl = checksum?.browser_download_url,
            digestSha256 = Checksums.fromDigestField(apk.digest),
            publishedAt = published_at,
        )
    }

    // ------------------------------------------------------------------ download + verify

    fun download(release: ReleaseInfo) {
        scope.launch(Dispatchers.IO) {
            try {
                _state.value = State.Downloading(release, 0f)
                val expected = resolveExpectedSha256(release)
                    ?: throw IOException("릴리스에 SHA-256 체크섬이 없어 설치를 중단했습니다.")
                val (file, actual) = downloadApk(release) { p -> _state.value = State.Downloading(release, p) }
                _state.value = State.Verifying(release)
                if (!actual.equals(expected, ignoreCase = true)) {
                    file.delete()
                    throw IOException("SHA-256 체크섬 불일치 - 파일이 손상되었거나 변조되었습니다.")
                }
                verifyApk(file)
                _state.value = State.ReadyToInstall(release, file)
            } catch (e: Exception) {
                _state.value = State.Error("업데이트 다운로드/검증 실패: ${e.message}", release)
            }
        }
    }

    private fun resolveExpectedSha256(release: ReleaseInfo): String? {
        release.checksumUrl?.let { url ->
            val listing = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("체크섬 파일 다운로드 실패 (HTTP ${r.code})")
                val body = r.body ?: throw IOException("체크섬 파일이 비어 있습니다")
                if (body.contentLength() > 1L * 1024 * 1024) throw IOException("체크섬 파일이 비정상적으로 큽니다")
                body.string()
            }
            Checksums.findSha256(listing, release.apkName)?.let { return it }
        }
        return release.digestSha256
    }

    private fun downloadApk(release: ReleaseInfo, onProgress: (Float) -> Unit): Pair<File, String> {
        val dir = updateDir.apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update-${release.version}.apk")
        val request = Request.Builder().url(release.apkUrl).header("Accept", "application/octet-stream").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("APK 다운로드 실패 (HTTP ${response.code})")
            val body = response.body ?: throw IOException("빈 응답")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 } ?: -1L
            if (total > maxApkBytes) throw IOException("APK가 비정상적으로 큽니다 (${total / 1024 / 1024} MB)")
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done > maxApkBytes) throw IOException("APK가 허용 크기를 초과했습니다")
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            return file to digest.digest().toHex()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(file: File) {
        val pm = context.packageManager
        val archive: PackageInfo = (
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            ) ?: throw IOException("다운로드한 파일이 올바른 APK가 아닙니다.")

        val mine: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

        if (archive.packageName != context.packageName) {
            throw IOException("패키지 이름이 다릅니다 (${archive.packageName}). 디버그 빌드에서는 릴리스 업데이트를 설치할 수 없습니다.")
        }
        val archiveCode = archive.longVersionCode
        val myCode = mine.longVersionCode
        if (archiveCode <= myCode) {
            throw IOException("다운로드한 버전(${archive.versionName})이 현재 버전보다 새롭지 않습니다.")
        }
        val theirs = signerDigests(archive)
        val ours = signerDigests(mine)
        if (theirs.isEmpty() || ours.isEmpty() || theirs != ours) {
            throw IOException("APK 서명 인증서가 현재 앱과 다릅니다. 설치를 중단했습니다.")
        }
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        val signatures = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        return signatures.orEmpty().map { Checksums.sha256Hex(it.toByteArray()) }.toSet()
    }

    // ------------------------------------------------------------------ install

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(release: ReleaseInfo, file: File) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!file.exists()) throw IOException("업데이트 파일이 없습니다. 다시 다운로드하세요.")
                _state.value = State.Installing(release)
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(file.length())
                    if (Build.VERSION.SDK_INT >= 31) {
                        // Honoured without a prompt once this app is the installer of record (i.e. from the 2nd self-update on).
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(context, InstallResultReceiver::class.java)
                        .setAction(InstallResultReceiver.ACTION)
                        .setPackage(context.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                _state.value = State.Error("설치 시작 실패: ${e.message}", release)
            }
        }
    }

    /** Called by [InstallResultReceiver]. */
    fun onInstallResult(success: Boolean, message: String?) {
        val current = _state.value
        val release = (current as? State.Installing)?.release
        if (success) {
            updateDir.listFiles()?.forEach { it.delete() }
            _state.value = State.Idle
        } else {
            _state.value = State.Error("설치 실패: ${message ?: "알 수 없는 오류"}", release)
        }
    }
}
