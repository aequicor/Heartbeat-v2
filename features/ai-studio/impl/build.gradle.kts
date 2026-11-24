import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
    alias(libs.plugins.kotlin.serialization)
    id("heartbeat-sqldelight")
}

configure<SqlDelightExtension> {
    databases {
        create("HeartbeatDatabase") {
            packageName.set("io.github.aequicor.heartbeat.features.aistudio.db")
        }
    }
}

val jvmTestFixtures by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}

kotlin {
    jvm {
        val main by compilations.getting
        val testFixtures by compilations.creating {
            associateWith(main)
        }
        val jvmTestFixturesJar by tasks.registering(Jar::class) {
            archiveClassifier.set("jvm-test-fixtures")
            from(testFixtures.output.allOutputs)
        }
        artifacts.add(jvmTestFixtures.name, jvmTestFixturesJar)
    }

    sourceSets.commonMain.dependencies {
        implementation(libs.jetbrains.markdown)
        implementation(libs.okio)
        implementation(libs.kotlinx.serialization.json)
        api(libs.kotlinx.coroutines.core)
        implementation(project(":di:api"))
        implementation(project(":features:ai-engine-facade:api"))
        implementation(project(":core:preferences:api"))
        implementation(project(":core:database"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
    sourceSets.jvmMain.dependencies {
        implementation(libs.skiko.awt)
    }
    sourceSets.jvmTest.dependencies {
        implementation(libs.sqldelight.sqlite.driver)
    }
}
