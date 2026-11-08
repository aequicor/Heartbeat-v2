package io.github.aequicor.heartbeat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.retainedComponent
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.shell.ui.ShellScreen
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("Heartbeat", "Heartbeat started")
        enableEdgeToEdge()

        val graph = (application as HeartbeatApplication).graph
        val renderers = graph.renderers
        // The Decompose root is retained across configuration changes and process recreation, so
        // the navigation stack and the selected feature survive rotation instead of resetting to
        // the AI studio route on every `onCreate`.
        val shell = retainedComponent(key = "shell", handleBackButton = false) { context ->
            graph.shellComponentFactory.create(
                componentContext = context,
                initialRoute = AiStudioRoutes.main(),
                dispatcher = Dispatchers.Main,
            )
        }

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
}
