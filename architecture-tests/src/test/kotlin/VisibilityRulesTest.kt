import org.junit.Assert
import org.junit.Test

/**
 * Unit tests of the role-based visibility audit (HB-ARCH-040): only contract roles may stay
 * public, Metro annotations are the single exception and only in the roles the graph sees, and
 * every other implementation detail is `internal`.
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
        declarationKind: DeclarationKind = DeclarationKind.CLASS,
        annotations: List<String> = listOf(),
        modifiers: List<String> = listOf(),
    ): String? = VisibilityRules.violation(
        packageName = packageName,
        declarationName = name,
        isPublic = isPublic,
        declarationKind = declarationKind,
        annotations = annotations,
        modifiers = modifiers,
    )

    @Test
    fun implementationRolesRequireInternal() {
        Assert.assertEquals(
            "implementation detail must be internal: NoteDto",
            violation(dataDto, "NoteDto"),
        )
        Assert.assertEquals(
            "data source contract must be an interface ending with DataSource: NoteDto",
            violation(dataSource, "NoteDto"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: toNote",
            violation(dataMapper, "toNote", declarationKind = DeclarationKind.FUNCTION),
        )
        Assert.assertEquals(
            "implementation detail must be internal: toEditorState",
            violation(presentationMapper, "toEditorState", declarationKind = DeclarationKind.FUNCTION),
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
    fun metroBindingsStayPublicOnlyInGraphRoles() {
        Assert.assertNull(
            violation(dataSource, "EditorDataSource", annotations = listOf("SingleIn")),
        )
        Assert.assertNull(
            violation(domainUseCase, "LoadNoteUseCase", annotations = listOf("Inject")),
        )
        Assert.assertNull(
            violation(presentation, "DefaultEditorStore", annotations = listOf("ContributesBinding")),
        )
        Assert.assertNull(
            violation(di, "DefaultEditorStoreFactory", annotations = listOf("ContributesBinding")),
        )
        // A Metro annotation does not make a model or a screen helper public.
        Assert.assertEquals(
            "domain model must be a data, sealed, enum or value declaration: NoteCache",
            violation(domainModel, "NoteCache", annotations = listOf("Inject")),
        )
        Assert.assertEquals(
            "screen must be a @Composable function ending with Screen: EditorHeader",
            violation(
                ui,
                "EditorHeader",
                declarationKind = DeclarationKind.FUNCTION,
                annotations = listOf("Composable"),
            ),
        )
    }

    @Test
    fun contractsStayPublicByRole() {
        Assert.assertNull(violation(api, "EditorStore", declarationKind = DeclarationKind.INTERFACE))
        Assert.assertNull(violation(domainModel, "Note", modifiers = listOf("DATA")))
        Assert.assertNull(
            violation(domainModel, "NoteKind", modifiers = listOf("ENUM")),
        )
        Assert.assertNull(
            violation(domainRepository, "EditorRepository", declarationKind = DeclarationKind.INTERFACE),
        )
        Assert.assertNull(
            violation(dataSource, "EditorDataSource", declarationKind = DeclarationKind.INTERFACE),
        )
        Assert.assertEquals(
            "data source contract must be an interface ending with DataSource: NoteCache",
            violation(dataSource, "NoteCache"),
        )
        Assert.assertNull(
            violation(
                ui,
                "EditorScreen",
                declarationKind = DeclarationKind.FUNCTION,
                annotations = listOf("Composable"),
            ),
        )
    }

    @Test
    fun helperDeclarationsInContractPackagesAreRejected() {
        Assert.assertEquals(
            "domain model must be a data, sealed, enum or value declaration: NoteHelper",
            violation(domainModel, "NoteHelper"),
        )
        Assert.assertEquals(
            "repository contract must be an interface ending with Repository: EditorRepositoryImpl",
            violation(domainRepository, "EditorRepositoryImpl"),
        )
        Assert.assertEquals(
            "implementation detail must be internal: EditorStoreFactory",
            violation(di, "EditorStoreFactory"),
        )
        Assert.assertEquals(
            "screen must be a @Composable function ending with Screen: EditorHeader",
            violation(
                ui,
                "EditorHeader",
                declarationKind = DeclarationKind.FUNCTION,
                annotations = listOf("Composable"),
            ),
        )
        // A screen must be a composable function: a class with the same name is a detail.
        Assert.assertEquals(
            "screen must be a @Composable function ending with Screen: EditorScreen",
            violation(ui, "EditorScreen"),
        )
    }

    @Test
    fun componentContractsArePublicButImplementationsAreInternal() {
        Assert.assertNull(
            violation(component, "EditorComponent", declarationKind = DeclarationKind.INTERFACE),
        )
        Assert.assertNull(
            violation(component, "EditorComponentFactory", declarationKind = DeclarationKind.INTERFACE),
        )
        Assert.assertEquals(
            "component contract must be EditorComponent or EditorComponentFactory: DefaultEditorComponent",
            violation(component, "DefaultEditorComponent"),
        )
        Assert.assertEquals(
            "component contract must be EditorComponent or EditorComponentFactory: EditorAdapter",
            violation(component, "EditorAdapter", declarationKind = DeclarationKind.INTERFACE),
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
