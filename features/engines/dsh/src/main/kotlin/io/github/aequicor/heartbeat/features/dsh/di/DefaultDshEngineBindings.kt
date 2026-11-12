package io.github.aequicor.heartbeat.features.dsh.di

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.dsh.domain.model.DshEngineSpec

@ContributesTo(AppScope::class)
public interface DefaultDshEngineBindings {
    @Provides
    public fun provideDshEngineSpec(): DshEngineSpec {
        val profile = listOf("dsh", "--profile", "acp")
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val command = if (isWindows) listOf("cmd.exe", "/d", "/c") + profile else profile
        return DshEngineSpec(command)
    }
}
