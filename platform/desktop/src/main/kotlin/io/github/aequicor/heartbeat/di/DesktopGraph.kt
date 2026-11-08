package io.github.aequicor.heartbeat.di

import dev.zacsweers.metro.DependencyGraph
import io.github.aequicor.heartbeat.navigation.compose.ComposeFeatureRenderer
import io.github.aequicor.heartbeat.shell.ShellComponentFactory

/**
 * Composition root of the desktop application.
 *
 * The graph lives in the platform compilation (HB-ARCH-014), so Metro aggregates every feature
 * contribution that is on the desktop classpath: the `Set<DecomposeFeatureEntry>` is consumed by
 * [ShellComponentFactory], while the platform pulls the Compose renderers directly for the shell UI.
 */
@DependencyGraph(AppScope::class)
interface DesktopGraph {

    val shellComponentFactory: ShellComponentFactory

    val renderers: Set<ComposeFeatureRenderer>
}
