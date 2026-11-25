package heartbeat

import java.io.File

data class JcefJdkInfo(
    val runJavaHome: File,
    val packagingJavaHome: File,
    val hasJcef: Boolean,
    val hostArchitecture: String,
)
