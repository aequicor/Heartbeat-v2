package io.github.aequicor.heartbeat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.di.DesktopGraph
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.shell.ui.ShellScreen
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.styling.TitleBarStyle

fun main() {
    println("Heartbeat started")

    val dataDirectory = desktopDataDirectory()
    ensureDataDirectory(dataDirectory)

    val graph = createGraphFactory<DesktopGraph.Factory>().create(dataDirectory)
    initializeStorage(graph)

    val lifecycle = LifecycleRegistry()
    lifecycle.resume()

    val renderers = graph.renderers

    application {
        val isDark = isSystemInDarkTheme()
        val titleBarStyle = if (isDark) TitleBarStyle.dark() else TitleBarStyle.lightWithLightHeader()
        val appearance = if (isDark) HbAppearance.Dark else HbAppearance.Light

        val shell = remember(graph) {
            graph.shellComponentFactory.create(
                componentContext = DefaultComponentContext(lifecycle),
                initialRoute = AiStudioRoutes.main(),
                dispatcher = Dispatchers.Main,
            )
        }

        IntUiTheme(
            theme = if (isDark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
            styling = ComponentStyling.default().decoratedWindow(titleBarStyle = titleBarStyle),
        ) {
            DecoratedWindow(
                onCloseRequest = {
                    lifecycle.destroy()
                    graph.databaseInitializer.close()
                    graph.appCoroutineScope.cancel()
                    exitApplication()
                },
                state = rememberWindowState(width = 1440.dp, height = 900.dp),
                title = "Heartbeat",
            ) {
                TitleBar {
                    Text(
                        "Heartbeat",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                HbTheme(appearance = appearance) {
                    ShellScreen(
                        component = shell,
                        renderers = renderers,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Application data directory: `%LOCALAPPDATA%\Heartbeat` on Windows and `~/.heartbeat` elsewhere.
 * The entry point owns the path; the database and preferences modules receive it as a string.
 *
 * `HEARTBEAT_DATA_DIR` overrides the location. The Gradle dev-run task uses it to keep the
 * sandboxed desktop process inside the writable build directory; a normal installation does not
 * set it.
 */
private fun desktopDataDirectory(): String {
    val override = System.getenv("HEARTBEAT_DATA_DIR")
    if (!override.isNullOrBlank()) {
        return File(override).absolutePath
    }

    val localAppData = System.getenv("LOCALAPPDATA")
    return if (!localAppData.isNullOrBlank()) {
        File(localAppData, "Heartbeat").absolutePath
    } else {
        File(System.getProperty("user.home"), ".heartbeat").absolutePath
    }
}

/**
 * Ensures the data directory exists before the database is opened. Without this check a read-only
 * location surfaces as an opaque `SQLITE_CANTOPEN`; report the real cause instead.
 */
private fun ensureDataDirectory(directory: String) {
    val dataDirectory = File(directory)
    if (!dataDirectory.isDirectory && !dataDirectory.mkdirs()) {
        System.err.println(
            "Heartbeat storage initialization failed: cannot create data directory $directory",
        )
        exitProcess(1)
    }
}

/**
 * Opens the database before any shell binding can request a repository. A failure is reported and
 * terminates the process: the entry point must not start features on a database whose migration
 * failed, and it must not delete user data to recover.
 */
@Suppress("TooGenericExceptionCaught") // Any driver or migration failure must stop startup.
private fun initializeStorage(graph: DesktopGraph) {
    try {
        graph.databaseInitializer.initialize()
    } catch (error: Exception) {
        System.err.println("Heartbeat storage initialization failed: ${error.message}")
        graph.databaseInitializer.close()
        graph.appCoroutineScope.cancel()
        exitProcess(1)
    }
}
