package heartbeat

import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File

data class InnoSetupInfo(
    val compiler: File,
    val provisionTask: TaskProvider<out Task>?,
)
