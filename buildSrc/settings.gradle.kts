pluginManagement {
    repositories {
        mavenCentral {
            content { includeGroup("org.jetbrains.kotlin") }
        }
        gradlePluginPortal()
    }
}

// buildSrc is a separate build, so it reuses the repository version catalog explicitly.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
