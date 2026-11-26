import heartbeat.InnoSetupInfo
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

// Resolve the Inno Setup compiler (ISCC.exe) from explicit environment paths,
// standard install directories, or finally PATH.
val isccCandidates = listOfNotNull(
    providers.environmentVariable("ISCC").orNull,
    providers.environmentVariable("INNO_SETUP_HOME").orNull?.let { "$it/ISCC.exe" },
    providers.environmentVariable("LOCALAPPDATA").orNull
        ?.let { "$it/Programs/Inno Setup 6/ISCC.exe" },
    providers.environmentVariable("ProgramFiles(x86)").orNull
        ?.let { "$it/Inno Setup 6/ISCC.exe" },
    providers.environmentVariable("ProgramFiles").orNull
        ?.let { "$it/Inno Setup 6/ISCC.exe" },
).filter { File(it).isFile }
val pathIscc = providers.environmentVariable("PATH").orNull.orEmpty().split(File.pathSeparator)
    .filter(String::isNotEmpty)
    .map { File(it, "ISCC.exe") }
    .firstOrNull(File::isFile)
    ?.absolutePath
val installedIscc = isccCandidates.firstOrNull() ?: pathIscc

val innoSetupVersion = "6.7.4"
val innoSetupArchiveName = "innosetup-$innoSetupVersion.exe"
val innoSetupArchiveSha256 = "a549929319f9120716677c685535ca4671e4b937b5fdc87e2585f32d8eb30e8c"
val innoSetupDownloadUrl =
    "https://github.com/jrsoftware/issrc/releases/download/is-6_7_4/$innoSetupArchiveName"
val innoSetupArchive = layout.buildDirectory.file("inno-setup/archives/$innoSetupArchiveName")
val innoSetupHome = layout.buildDirectory.dir("inno-setup/$innoSetupVersion")
val provisionedIscc = innoSetupHome.map { it.file("ISCC.exe") }
val shouldProvisionInnoSetup = isWindows && installedIscc == null

val downloadInnoSetup = if (shouldProvisionInnoSetup) {
    tasks.register("downloadInnoSetup") {
        group = "compose desktop"
        description = "Downloads the pinned Inno Setup compiler with SHA-256 verification"
        inputs.property("downloadUrl", innoSetupDownloadUrl)
        inputs.property("sha256", innoSetupArchiveSha256)
        outputs.file(innoSetupArchive)
        doLast {
            val target = innoSetupArchive.get().asFile
            if (target.isFile && sha256(target) == innoSetupArchiveSha256) return@doLast
            Files.createDirectories(target.parentFile.toPath())
            val partial = target.resolveSibling("${target.name}.part")
            try {
                logger.lifecycle("Downloading $innoSetupDownloadUrl")
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                val request = HttpRequest.newBuilder(URI(innoSetupDownloadUrl))
                    .timeout(Duration.ofMinutes(30))
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial.toPath()))
                check(response.statusCode() == 200) {
                    "Cannot download $innoSetupDownloadUrl: HTTP ${response.statusCode()}"
                }
                val actualSha256 = sha256(partial)
                check(actualSha256 == innoSetupArchiveSha256) {
                    "SHA-256 mismatch for $innoSetupDownloadUrl: " +
                        "expected $innoSetupArchiveSha256, got $actualSha256"
                }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(partial.toPath())
            }
        }
    }
} else {
    null
}

val provisionInnoSetup = if (shouldProvisionInnoSetup) {
    tasks.register<Exec>("provisionInnoSetup") {
        group = "compose desktop"
        description = "Extracts Inno Setup locally without modifying the system"
        dependsOn(checkNotNull(downloadInnoSetup))
        inputs.file(innoSetupArchive)
        outputs.file(provisionedIscc)
        executable = innoSetupArchive.get().asFile.absolutePath
        // The installer unpacks its bootstrap executable before it opens its setup log.
        val setupTemp = temporaryDir
        environment("TEMP", setupTemp.absolutePath)
        environment("TMP", setupTemp.absolutePath)
        args(
            "/PORTABLE=1",
            "/VERYSILENT",
            "/SUPPRESSMSGBOXES",
            "/NORESTART",
            "/CURRENTUSER",
            "/DIR=${innoSetupHome.get().asFile.absolutePath}",
        )
        doFirst {
            Files.createDirectories(setupTemp.toPath())
            innoSetupHome.get().asFile.deleteRecursively()
        }
    }
} else {
    null
}

extensions.add(
    "innoSetup",
    InnoSetupInfo(
        compiler = installedIscc?.let(::File) ?: provisionedIscc.get().asFile,
        provisionTask = provisionInnoSetup,
    ),
)
