package io.github.aequicor.heartbeat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import dev.zacsweers.metro.createGraph
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.di.AndroidGraph
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.shell.ShellComponent
import io.github.aequicor.heartbeat.shell.ui.ShellScreen
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private val decomposeLifecycle = LifecycleRegistry()

    private lateinit var shell: ShellComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("Heartbeat", "Heartbeat started")
        enableEdgeToEdge()

        decomposeLifecycle.resume()

        val graph = createGraph<AndroidGraph>()
        val renderers = graph.renderers
        shell = graph.shellComponentFactory.create(
            componentContext = DefaultComponentContext(decomposeLifecycle),
            initialRoute = AiStudioRoutes.main(),
            dispatcher = Dispatchers.Main,
        )

        setContent {
            HbTheme(density = HbDensity.Touch) {
                BackHandler {
                    if (!shell.back()) {
                        finish()
                    }
                }
                ShellScreen(
                    component = shell,
                    renderers = renderers,
                    modifier = Modifier.systemBarsPadding(),
                )
            }
        }
    }

    override fun onDestroy() {
        decomposeLifecycle.destroy()
        super.onDestroy()
    }
}
