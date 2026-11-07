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
        kind: DeclarationKind,
        name: String,
        isPublic: Boolean = true,
        annotations: List<String> = listOf(),
    ): String? = VisibilityRules.violation(packageName, kind, name, isPublic, annotations)

    @Test
    fun implementationDetailsMustBeInternal() {
        Assert.assertEquals(
            "implementation detail must be internal: NoteDto",
            violation(dataSource, DeclarationKind.CLASS, "NoteDto"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: NoteDto",
            violation(dataDto, DeclarationKind.CLASS, "NoteDto"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: toNote",
            violation(dataMapper, DeclarationKind.FUNCTION, "toNote"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: toEditorState",
            violation(presentationMapper, DeclarationKind.FUNCTION, "toEditorState"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: DefaultEditorStore",
            violation(presentation, DeclarationKind.CLASS, "DefaultEditorStore"),
        )
    }

    @Test
    fun internalDeclarationsAreNotFlagged() {
        Assert.assertNull(violation(dataSource, DeclarationKind.CLASS, "NoteDto", isPublic = false))
        Assert.assertNull(violation(dataMapper, DeclarationKind.FUNCTION, "toNote", isPublic = false))
        Assert.assertNull(
            violation(presentation, DeclarationKind.CLASS, "DefaultEditorStore", isPublic = false),
        )
    }

    @Test
    fun metroBoundTypesStayPublic() {
        Assert.assertNull(
            violation(dataSource, DeclarationKind.CLASS, "EditorDataSource", annotations = listOf("SingleIn")),
        )
        Assert.assertNull(
            violation(domainUseCase, DeclarationKind.CLASS, "LoadNoteUseCase", annotations = listOf("Inject")),
        )
        Assert.assertNull(
            violation(
                presentation,
                DeclarationKind.CLASS,
                "DefaultEditorStore",
                annotations = listOf("ContributesBinding"),
            ),
        )
    }

    @Test
    fun contractsStayPublic() {
        Assert.assertNull(violation(api, DeclarationKind.INTERFACE, "EditorStore"))
        Assert.assertNull(violation(domainModel, DeclarationKind.CLASS, "Note"))
        Assert.assertNull(violation(domainRepository, DeclarationKind.INTERFACE, "EditorRepository"))
        Assert.assertNull(violation(di, DeclarationKind.CLASS, "DefaultEditorStoreFactory"))
        Assert.assertNull(violation(ui, DeclarationKind.FUNCTION, "EditorScreen"))
    }

    @Test
    fun componentContractsArePublicButImplementationsAreInternal() {
        Assert.assertNull(violation(component, DeclarationKind.INTERFACE, "EditorComponent"))
        Assert.assertNull(violation(component, DeclarationKind.INTERFACE, "EditorComponentFactory"))
        Assert.assertEquals(
            "implementation detail must be internal: DefaultEditorComponent",
            violation(component, DeclarationKind.CLASS, "DefaultEditorComponent"),
        )
        Assert.assertNull(
            violation(component, DeclarationKind.CLASS, "DefaultEditorComponent", isPublic = false),
        )
    }

    @Test
    fun declarationsOutsideFeaturesHaveNoVisibilityRule() {
        Assert.assertNull(
            violation(
                "io.github.aequicor.heartbeat.core.platformapi",
                DeclarationKind.CLASS,
                "PlatformName",
            ),
        )
        Assert.assertNull(
            violation("io.github.aequicor.heartbeat.designsystem.theme", DeclarationKind.CLASS, "HbColors"),
        )
    }
}
