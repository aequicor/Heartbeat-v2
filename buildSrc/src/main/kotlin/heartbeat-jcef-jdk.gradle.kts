import heartbeat.JcefJdkInfo
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.tasks.AbstractCheckNativeDistributionRuntime
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.tasks.AbstractSuggestModulesTask
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties

val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
val isMacos = System.getProperty("os.name").startsWith("Mac")
val desktopToolchain = extensions.getByType(JavaToolchainService::class.java).launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
    vendor = JvmVendorSpec.JETBRAINS
}

private fun sdkHome(directory: File): File =
    directory.resolve("Contents/Home").takeIf { it.isDirectory } ?: directory

private fun architecture(name: String): String = when (name.lowercase()) {
    "x86_64", "amd64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> name
}

private fun hasCompatibleJcef(home: File): Boolean {
    if (!home.resolve("jmods/jcef.jmod").isFile) return false
    val release = home.resolve("release")
    if (!release.isFile) return false
    val sdk = Properties()
    release.inputStream().use(sdk::load)
    val version = sdk.getProperty("JAVA_VERSION").orEmpty().trim('"').takeWhile(Char::isDigit)
    return version == "25" &&
        sdk.getProperty("IMPLEMENTOR").orEmpty().contains("JetBrains") &&
        architecture(sdk.getProperty("OS_ARCH").orEmpty().trim('"')) == architecture(System.getProperty("os.arch"))
}

private fun sha512(file: File): String {
    val digest = MessageDigest.getInstance("SHA-512")
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

private fun repairWindowsJdk(staged: Path, installed: Path) {
    // Remove obsolete entries and type conflicts before restoring the archive contents.
    Files.walk(installed).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { target ->
            val source = staged.resolve(installed.relativize(target))
            if (Files.notExists(source) || Files.isSymbolicLink(target) ||
                Files.isDirectory(source) != Files.isDirectory(target, NOFOLLOW_LINKS)
            ) {
                Files.delete(target)
            }
        }
    }
    Files.walk(staged).use { paths ->
        paths.forEach { source ->
            val target = installed.resolve(staged.relativize(source))
            if (Files.isDirectory(source)) {
                Files.createDirectories(target)
            } else if (Files.notExists(target) || Files.mismatch(source, target) != -1L) {
                // Windows can keep identical files such as lib/jrt-fs.jar open across builds.
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

val explicitJcefHome = providers.environmentVariable("JBR_JCEF_HOME").orNull?.let { sdkHome(file(it)) }
if (explicitJcefHome != null) {
    check(hasCompatibleJcef(explicitJcefHome)) { "JBR_JCEF_HOME must name a JetBrains JDK 25 with JCEF for this host" }
}
val userDirectory = file(System.getProperty("user.home"))
val standardSdkDirectories = when {
    isMacos -> listOf(
        file("/Library/Java/JavaVirtualMachines"),
        userDirectory.resolve("Library/Java/JavaVirtualMachines"),
    )
    isWindows -> listOfNotNull(providers.environmentVariable("ProgramFiles").orNull?.let { file(it).resolve("Java") })
    else -> listOf(file("/usr/lib/jvm"))
}
val jcefCandidates = sequence {
    providers.environmentVariable("JAVA_HOME").orNull?.let { yield(file(it)) }
    yield(file(System.getProperty("java.home")))
    providers.gradleProperty("org.gradle.java.installations.paths").orNull.orEmpty().split(',')
        .filter(String::isNotBlank).forEach { yield(file(it.trim())) }
    val sdkCache = gradle.gradleUserHomeDir.resolve("jdks")
    for (root in standardSdkDirectories + userDirectory.resolve(".jdks") + sdkCache) {
        for (entry in root.listFiles(File::isDirectory).orEmpty().sortedBy(File::getName)) {
            yield(entry)
            if (root == sdkCache) yieldAll(entry.listFiles(File::isDirectory).orEmpty().asSequence())
        }
    }
}
val installedJcefHome = explicitJcefHome ?: jcefCandidates.map(::sdkHome).firstOrNull(::hasCompatibleJcef)
val toolchainHome = desktopToolchain.get().metadata.installationPath.asFile
val hostArchitecture = architecture(System.getProperty("os.arch"))
val (jcefArchiveName, jcefArchiveSha512) = when {
    isWindows && hostArchitecture == "x64" ->
        "jbrsdk_jcef-25.0.4-windows-x64-b508.27.zip" to
            "30ccf003e6b0815cc8d3abab7dc2cd6642cd5add225745c0ae56986d67ad79fe" +
                "cae191d6220781c66686add7bb5c5772616128e748c9798e05d2869aa161d4be"
    !isWindows && !isMacos && hostArchitecture == "x64" ->
        "jbrsdk_jcef-25.0.4-linux-x64-b508.27.tar.gz" to
            "04a3ecf31fe669c6583aa34660252dc26d75623de65820b5c24b59112c8803d5" +
                "d7d6bc92adaccf4d6f376971cf8cb0147502018c078f344d1549bdc758f33f85"
    isMacos && hostArchitecture == "arm64" ->
        "jbrsdk_jcef-25.0.4-osx-aarch64-b508.27.tar.gz" to
            "43b7ff1f9544a3dc1c9d8f4209bc44b77a91fa10fad2696fef3e880a91f862f8" +
                "09e962b681c07165c556dc6dc93651ae847f50691aa7d7bba9163be7a6041c05"
    else -> null to null
}
val shouldProvisionJcef = installedJcefHome == null && jcefArchiveName != null
val provisionedJcefRoot = layout.buildDirectory.dir("jdks/jbrsdk-jcef-25.0.4").get().asFile
val provisionedJcefHome = if (isMacos) provisionedJcefRoot.resolve("Contents/Home") else provisionedJcefRoot
val runJavaHome = installedJcefHome ?: toolchainHome
val packagingJavaHome = if (shouldProvisionJcef) provisionedJcefHome else runJavaHome
val hasJcef = installedJcefHome != null || shouldProvisionJcef
val hasRunJcef = hasCompatibleJcef(runJavaHome)

val downloadJcefJdk = if (shouldProvisionJcef) {
    tasks.register("downloadJcefJdk") {
        group = "compose desktop"
        description = "Downloads the JetBrains JDK 25 with JCEF for desktop packaging"
        val archiveName = checkNotNull(jcefArchiveName)
        val expectedSha512 = checkNotNull(jcefArchiveSha512)
        val downloadUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$archiveName"
        val archive = layout.buildDirectory.file("jdks/archives/$archiveName")
        inputs.property("downloadUrl", downloadUrl)
        inputs.property("sha512", expectedSha512)
        outputs.file(archive)
        doLast {
            val target = archive.get().asFile
            if (target.isFile && sha512(target) == expectedSha512) return@doLast
            Files.createDirectories(target.parentFile.toPath())
            val partial = target.resolveSibling("${target.name}.part")
            try {
                logger.lifecycle("Downloading $downloadUrl")
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                val request = HttpRequest.newBuilder(URI(downloadUrl))
                    .timeout(Duration.ofMinutes(30))
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial.toPath()))
                check(response.statusCode() == 200) { "Cannot download $downloadUrl: HTTP ${response.statusCode()}" }
                val actualSha512 = sha512(partial)
                check(actualSha512 == expectedSha512) {
                    "SHA-512 mismatch for $downloadUrl: expected $expectedSha512, got $actualSha512"
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

val provisionJcefJdk = if (shouldProvisionJcef) {
    tasks.register("provisionJcefJdk") {
        group = "compose desktop"
        description = "Unpacks and verifies the JetBrains JDK 25 with JCEF for desktop packaging"
        val archiveName = checkNotNull(jcefArchiveName)
        val archive = layout.buildDirectory.file("jdks/archives/$archiveName")
        val staging = layout.buildDirectory.dir("jdks/provision-staging").get().asFile
        dependsOn(checkNotNull(downloadJcefJdk))
        inputs.file(archive)
        outputs.dir(provisionedJcefRoot)
        doLast {
            project.delete(staging)
            Files.createDirectories(staging.toPath())
            val archiveFile = archive.get().asFile
            if (isWindows) {
                project.copy {
                    from(zipTree(archiveFile))
                    into(staging)
                }
            } else {
                providers.exec {
                    commandLine("tar", "-xzf", archiveFile.absolutePath, "-C", staging.absolutePath)
                }.result.get()
            }
            val extractedRoot = sequenceOf(staging)
                .plus(staging.listFiles(File::isDirectory).orEmpty().asSequence())
                .firstOrNull { hasCompatibleJcef(sdkHome(it)) }
            val sdkRoot = checkNotNull(extractedRoot) {
                "Downloaded archive does not contain JetBrains JDK 25 with JCEF"
            }
            if (isWindows && provisionedJcefRoot.exists()) {
                repairWindowsJdk(sdkRoot.toPath(), provisionedJcefRoot.toPath())
            } else {
                project.delete(provisionedJcefRoot)
                Files.move(sdkRoot.toPath(), provisionedJcefRoot.toPath())
            }
            check(hasCompatibleJcef(provisionedJcefHome)) { "Provisioned JDK is not usable for desktop packaging" }
            project.delete(staging)
        }
    }
} else {
    null
}

extensions.add(
    "jcefJdk",
    JcefJdkInfo(
        runJavaHome = runJavaHome,
        packagingJavaHome = packagingJavaHome,
        hasJcef = hasJcef,
        hostArchitecture = hostArchitecture,
    ),
)

// Compose configures these tasks in its own afterEvaluate callback, so their properties must be set later.
afterEvaluate {
    tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
        if (hasRunJcef) jvmArgs("--enable-native-access=jcef")
    }
    tasks.withType<AbstractCheckNativeDistributionRuntime>().configureEach {
        jdkHome.set(packagingJavaHome.absolutePath)
        provisionJcefJdk?.let { dependsOn(it) }
    }
    tasks.withType<AbstractSuggestModulesTask>().configureEach {
        javaHome.set(packagingJavaHome.absolutePath)
        provisionJcefJdk?.let { dependsOn(it) }
    }
    tasks.withType<AbstractJLinkTask>().configureEach {
        javaHome.set(packagingJavaHome.absolutePath)
        provisionJcefJdk?.let { dependsOn(it) }
    }
    tasks.withType<AbstractJPackageTask>().configureEach {
        // JBR 25 can report writable directories as non-writable in sandboxed Windows builds.
        // jpackage uses the Gradle JDK; the JCEF SDK is still required for jlink and the runtime image.
        javaHome.set(System.getProperty("java.home"))
        provisionJcefJdk?.let { dependsOn(it) }
        if (hasJcef) launcherJvmArgs.add("--enable-native-access=jcef")
    }
}
