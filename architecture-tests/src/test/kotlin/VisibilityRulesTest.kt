import org.junit.Assert
import org.junit.Test

/**
 * Unit tests of the visibility audit: implementation details are internal, while the Metro
 * boundary and the public contracts stay public (HB-ARCH-040).
 */
class VisibilityRulesTest {

    private val feature = "io.github.aequicor.heartbeat.features.editor"

    private val api = "$feature.api"
    private val dataSource = "$feature.data.datasource"
    private val dataDto = "$feature.data.dto"
    private val dataMapper = "$feature.data.mapper"
    private val domainModel = "$feature.domain.model"
    private val domainRepository = "$feature.domain.repository"
    private val domainUseCase = "$feature.domain.usecase"
    private val presentation = "$feature.presentation"
    private val presentationMapper = "$feature.presentation.mapper"
    private val component = "$feature.component"
    private val di = "$feature.di"
    private val ui = "$feature.ui"

    private fun violation(
        packageName: String,
        name: String,
        isPublic: Boolean = true,
        annotations: List<String> = listOf(),
    ): String? = VisibilityRules.violation(packageName, name, isPublic, annotations)

    @Test
    fun implementationDetailsMustBeInternal() {
        Assert.assertEquals(
            "implementation detail must be internal: NoteDto",
            violation(dataSource, "NoteDto"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: NoteDto",
            violation(dataDto, "NoteDto"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: toNote",
            violation(dataMapper, "toNote"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: toEditorState",
            violation(presentationMapper, "toEditorState"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: DefaultEditorStore",
            violation(presentation, "DefaultEditorStore"),
        )
    }

    @Test
    fun internalDeclarationsAreNotFlagged() {
        Assert.assertNull(violation(dataSource, "NoteDto", isPublic = false))
        Assert.assertNull(violation(dataMapper, "toNote", isPublic = false))
        Assert.assertNull(violation(presentation, "DefaultEditorStore", isPublic = false))
    }

    @Test
    fun metroBoundTypesStayPublic() {
        Assert.assertNull(
            violation(dataSource, "EditorDataSource", annotations = listOf("SingleIn")),
        )
        Assert.assertNull(
            violation(domainUseCase, "LoadNoteUseCase", annotations = listOf("Inject")),
        )
        Assert.assertNull(
            violation(presentation, "DefaultEditorStore", annotations = listOf("ContributesBinding")),
        )
    }

    @Test
    fun contractsStayPublic() {
        Assert.assertNull(violation(api, "EditorStore"))
        Assert.assertNull(violation(domainModel, "Note"))
        Assert.assertNull(violation(domainRepository, "EditorRepository"))
        Assert.assertNull(violation(di, "DefaultEditorStoreFactory"))
        Assert.assertNull(violation(ui, "EditorScreen"))
    }

    @Test
    fun componentContractsArePublicButImplementationsAreInternal() {
        Assert.assertNull(violation(component, "EditorComponent"))
        Assert.assertNull(violation(component, "EditorComponentFactory"))
        Assert.assertEquals(
            "implementation detail must be internal: DefaultEditorComponent",
            violation(component, "DefaultEditorComponent"),
        )
        Assert.assertNull(violation(component, "DefaultEditorComponent", isPublic = false))
    }

    @Test
    fun declarationsOutsideFeaturesHaveNoVisibilityRule() {
        Assert.assertNull(
            violation("io.github.aequicor.heartbeat.core.platformapi", "PlatformName"),
        )
        Assert.assertNull(
            violation("io.github.aequicor.heartbeat.designsystem.theme", "HbColors"),
        )
    }
}
