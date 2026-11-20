import heartbeat.PatchComposeWheel
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage

val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
val composeVersion = catalog.findVersion("compose-multiplatform").get().requiredVersion
val foundation = "org.jetbrains.compose.foundation:foundation-desktop:$composeVersion"
val sources = configurations.detachedConfiguration(dependencies.create(foundation)).apply {
    isTransitive = false
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
}
val foundationClasspath = configurations.detachedConfiguration(dependencies.create(foundation)).apply {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}
val compiler = configurations.detachedConfiguration(
    dependencies.create(catalog.findLibrary("kotlin-compiler-embeddable").get().get()),
)
val patchedWheel = Attribute.of("io.github.aequicor.heartbeat.desktop-wheel", Boolean::class.javaObjectType)

dependencies {
    artifactTypes.configureEach {
        if (name == JAR_TYPE) attributes.attribute(patchedWheel, false)
    }
    registerTransform(PatchComposeWheel::class) {
        from.attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE).attribute(patchedWheel, false)
        to.attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE).attribute(patchedWheel, true)
        parameters.sources.from(sources)
        parameters.foundationClasspath.from(foundationClasspath)
        parameters.compilerClasspath.from(compiler)
    }
}

// Android and all compile classpaths continue to use the upstream artifact.
configurations.matching {
    it.name in setOf("runtimeClasspath", "testRuntimeClasspath", "jvmTestRuntimeClasspath")
}.configureEach {
    attributes.attribute(patchedWheel, true)
}
