import org.gradle.api.artifacts.VersionCatalogsExtension

// Metro compile-time DI: applies the Metro compiler plugin and adds the runtime
// to commonMain. Apply together with a heartbeat-kmp-* plugin, which must come
// first in the module `plugins` block. The Metro plugin is applied from the
// script body because buildSrc applies every precompiled script `plugins` block
// to its own project, which has no `kotlin` extension that Metro requires at
// apply time; for the same reason the script has no Kotlin DSL accessors and
// writes the runtime dependency into the KMP `commonMainImplementation`
// configuration directly. The final @DependencyGraph stays in the platform
// module that owns the composition root.

project.pluginManager.apply("dev.zacsweers.metro")

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    add("commonMainImplementation", libs.findLibrary("metro-runtime").get())
}
