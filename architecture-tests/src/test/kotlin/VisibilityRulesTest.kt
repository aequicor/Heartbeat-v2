import org.junit.Assert
import org.junit.Test

/**
 * Unit tests of the visibility audit: implementation details are internal, while the Metro
 * boundary and the feature contracts stay public.
 */
class VisibilityRulesTest {

    private val feature = "io.github.aequicor.heartbeat.features.editor"

    private val dataSource = "$feature.data.datasource"
    private val dataDto = "$feature.data.dto"
    private val dataMapper = "$feature.data.mapper"
    private val domainModel = "$feature.domain.model"
    private val domainRepository = "$feature.domain.repository"
    private val domainUseCase = "$feature.domain.usecase"
    private val presentation = "$feature.presentation"
    private val component = "$feature.component"

    private fun violation(
        packageName: String,
        kind: DeclarationKind,
        name: String,
        isPublic: Boolean = true,
        annotations: List<String> = listOf(),
    ): String? = VisibilityRules.violation(packageName, kind, name, isPublic, annotations)

    @Test
    fun dtosAreInternal() {
        Assert.assertEquals(
            "DTO must be internal: NoteDto",
            violation(dataSource, DeclarationKind.CLASS, "NoteDto"),
        )
        Assert.assertEquals(
            "DTO must be internal: NoteDto",
            violation(dataDto, DeclarationKind.CLASS, "NoteDto"),
        )
        Assert.assertNull(violation(dataSource, DeclarationKind.CLASS, "NoteDto", isPublic = false))
    }

    @Test
    fun mapperFunctionsAreInternal() {
        Assert.assertEquals(
            "mapper function must be internal: toNote",
            violation(dataMapper, DeclarationKind.FUNCTION, "toNote"),
        )
        Assert.assertEquals(
            "mapper function must be internal: toEditorState",
            violation(presentation, DeclarationKind.FUNCTION, "toEditorState"),
        )
        Assert.assertNull(
            violation(dataMapper, DeclarationKind.FUNCTION, "toNote", isPublic = false),
        )
        Assert.assertNull(
            violation(dataMapper, DeclarationKind.CLASS, "NoteMapper"),
        )
    }

    @Test
    fun storeImplementationIsInternal() {
        Assert.assertEquals(
            "store implementation must be internal: DefaultEditorStore",
            violation(presentation, DeclarationKind.CLASS, "DefaultEditorStore"),
        )
        Assert.assertNull(
            violation(presentation, DeclarationKind.CLASS, "DefaultEditorStore", isPublic = false),
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
    fun componentImplementationIsInternal() {
        Assert.assertEquals(
            "component implementation must be internal: DefaultEditorComponent",
            violation(component, DeclarationKind.CLASS, "DefaultEditorComponent"),
        )
        Assert.assertNull(
            violation(component, DeclarationKind.CLASS, "DefaultEditorComponent", isPublic = false),
        )
        Assert.assertNull(
            violation(component, DeclarationKind.INTERFACE, "EditorComponent"),
        )
    }

    @Test
    fun metroAndDomainContractsStayPublic() {
        Assert.assertNull(
            violation(dataSource, DeclarationKind.CLASS, "EditorDataSource", annotations = listOf("SingleIn")),
        )
        Assert.assertNull(
            violation(dataSource, DeclarationKind.CLASS, "NoteDto", annotations = listOf("Inject")),
        )
        Assert.assertNull(violation(domainModel, DeclarationKind.CLASS, "Note"))
        Assert.assertNull(violation(domainRepository, DeclarationKind.INTERFACE, "EditorRepository"))
        Assert.assertNull(
            violation(
                domainUseCase,
                DeclarationKind.CLASS,
                "LoadNoteUseCase",
                annotations = listOf("Inject"),
            ),
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
