package heartbeat

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

// Values shared by the heartbeat-kmp-* convention plugins. Modules never pin
// these values themselves: the plugin derives the Android namespace from the
// Gradle path and sets the same JDK toolchain as the desktop entry point.

object KmpDefaults {

    const val COMPILE_SDK = 37
    const val MIN_SDK = 23
    const val NAMESPACE_PREFIX = "io.github.aequicor.heartbeat"

    val JAVA_LANGUAGE_VERSION = JavaLanguageVersion.of(25)
    val JVM_VENDOR = JvmVendorSpec.JETBRAINS

    fun namespace(projectPath: String): String =
        NAMESPACE_PREFIX + projectPath.replace(":", ".").replace("-", "")
}
