import heartbeat.JcefJdkInfo
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    id("heartbeat-desktop-wheel")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("heartbeat-desktop-distribution")
    id("heartbeat-desktop-windows")
    id("org.jetbrains.kotlin.plugin.compose")
    // The composition root lives in the platform compilation (HB-ARCH-014), so the Metro
    // compiler plugin runs here and the integration test graph proves that the feature
    // contributions of every module assemble into one graph.
    id("dev.zacsweers.metro")
}

val jcefJdk = extensions.getByType(JcefJdkInfo::class.java)

kotlin {
    // Jewel 0.42.0 is built for the IntelliJ Platform 2026.3 runtime and ships
    // class files for Java 25, so the application must run on JBR 25.
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

dependencies {
    implementation(compose.desktop.currentOs) {
        // Jewel provides the Int UI controls; keep Material off the desktop classpath.
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.jewel.int.ui.standalone)
    implementation(libs.jewel.decorated.window)
    implementation(libs.jetbrains.runtime.api)
    implementation(project(":designsystem:compose:theme"))
    implementation(project(":designsystem:compose:components"))
    implementation(project(":navigation:api"))
    implementation(project(":navigation:decompose"))
    implementation(project(":navigation:decompose-compose"))
    implementation(project(":shell:presentation:decompose"))
    implementation(project(":shell:ui:compose"))
    implementation(project(":features:ai-engine-facade:api"))
    implementation(project(":features:ai-engine-facade:impl"))
    implementation(project(":features:ai-engine-facade:settings:impl"))
    implementation(project(":features:ai-engine-facade:settings:presentation:decompose"))
    implementation(project(":features:ai-engine-facade:settings:ui:compose"))
    implementation(project(":features:engines:acp:impl"))
    implementation(project(":features:ai-studio:api"))
    implementation(project(":features:ai-studio:impl"))
    implementation(project(":features:ai-studio:presentation:decompose"))
    implementation(project(":features:ai-studio:ui:compose"))
    implementation(project(":features:settings:api"))
    implementation(project(":features:settings:impl"))
    implementation(project(":features:settings:presentation:decompose"))
    implementation(project(":features:settings:ui:compose"))
    implementation(project(":di:api"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences:api"))
    implementation(project(":core:preferences:datastore"))
    implementation(project(":storage:database"))
    implementation(libs.metro.runtime)
    runtimeOnly(libs.kotlinx.coroutines.swing)

    // Test-only: the composition root graph must see the feature bindings the platform
    // wires together. The application itself composes the AI studio screen only.
    testImplementation(project(":features:editor:api"))
    testImplementation(project(":features:editor:impl"))
    testImplementation(project(":features:editor:presentation:decompose"))
    testImplementation(project(":features:ai-studio:api"))
    testImplementation(project(":features:ai-studio:impl"))
    testImplementation(project(":features:ai-studio:presentation:decompose"))
    testImplementation(project(":features:ai-studio:ui:compose"))
    testImplementation(project(":features:settings:api"))
    testImplementation(project(":features:settings:impl"))
    testImplementation(project(":features:settings:presentation:decompose"))
    testImplementation(project(":features:settings:ui:compose"))
    testImplementation(project(":shell:presentation:decompose"))
    testImplementation(project(":navigation:decompose"))
    testImplementation(project(":navigation:decompose-compose"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "io.github.aequicor.heartbeat.MainKt"

        // DecoratedWindow needs JBR; jlink and runtime checks override this with the JCEF SDK.
        javaHome = jcefJdk.runJavaHome.absolutePath

        nativeDistributions {
            // SQLite JDBC loads java.sql; DataStore's protobuf adapter uses sun.misc.Unsafe from
            // jdk.unsupported. jpackage includes neither automatically.
            modules("java.sql")
            modules("jdk.unsupported")
            if (jcefJdk.hasJcef) modules("jcef")
            // Windows is packaged by Inno Setup (packageInnoSetup), not by the
            // built-in Compose formats (Msi/Exe), so no Windows format is listed.
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg)
            // Keep the Windows launcher path used by existing installations and pinned shortcuts.
            packageName = if (System.getProperty("os.name").startsWith("Windows")) "Heartbeat" else "Heartbeat Lab"
            description = "Heartbeat Lab"
            windows {
                iconFile.set(project.file("icons/heartbeat-lab.ico"))
            }
            macOS {
                bundleID = "io.github.aequicor.heartbeat"
                iconFile.set(project.file("icons/heartbeat-lab.icns"))
            }
            linux {
                packageName = "heartbeat"
                iconFile.set(project.file("icons/heartbeat-lab.png"))
            }
            packageVersion = "1.0.0"
        }
    }
}
