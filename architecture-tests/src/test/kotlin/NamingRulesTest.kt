import org.junit.Assert
import org.junit.Test

/**
 * Unit tests of the naming standard: every rule has a passing and a failing case,
 * so the Konsist test in [ArchitectureTest] only has to feed the PSI into
 * [NamingRules].
 */
class NamingRulesTest {

    private val feature = "io.github.aequicor.heartbeat.features.editor"

    private val api = "$feature.api"
    private val domainModel = "$feature.domain.model"
    private val domainRepository = "$feature.domain.repository"
    private val domainUseCase = "$feature.domain.usecase"
    private val dataSource = "$feature.data.datasource"
    private val dataDto = "$feature.data.dto"
    private val dataMapper = "$feature.data.mapper"
    private val dataRepository = "$feature.data.repository"
    private val presentation = "$feature.presentation"
    private val di = "$feature.di"
    private val component = "$feature.component"
    private val ui = "$feature.ui"
    private val designSystemTheme = "io.github.aequicor.heartbeat.designsystem.theme"

    private fun violation(
        packageName: String,
        kind: DeclarationKind,
        name: String,
        annotations: List<String> = listOf(),
    ): String? = NamingRules.violation(packageName, kind, name, annotations = annotations)

    @Test
    fun stateHolderIsAlwaysStore() {
        Assert.assertEquals(
            "features must name the state holder <Feature>Store, not EditorController",
            violation(api, DeclarationKind.INTERFACE, "EditorController"),
        )
        Assert.assertEquals(
            "features must name the state holder <Feature>Store, not EditorViewModel",
            violation(presentation, DeclarationKind.CLASS, "EditorViewModel"),
        )
        Assert.assertNull(violation(dataRepository, DeclarationKind.CLASS, "DefaultEditorRepository"))
    }

    @Test
    fun apiDeclarationsFollowTheFeatureName() {
        Assert.assertNull(violation(api, DeclarationKind.INTERFACE, "EditorStore"))
        Assert.assertNull(violation(api, DeclarationKind.INTERFACE, "EditorStoreFactory"))
        Assert.assertNull(violation(api, DeclarationKind.CLASS, "EditorState"))
        Assert.assertNull(violation(api, DeclarationKind.INTERFACE, "EditorAction"))
        Assert.assertNull(violation(api, DeclarationKind.INTERFACE, "EditorEffect"))
        Assert.assertNull(violation(api, DeclarationKind.CLASS, "EditorNote"))
        Assert.assertNull(violation(api, DeclarationKind.INTERFACE, "EditorService"))
        Assert.assertEquals(
            "api declaration must start with Editor: NoteStore",
            violation(api, DeclarationKind.INTERFACE, "NoteStore"),
        )
    }

    @Test
    fun apiSubpackagesFollowTheFeatureName() {
        Assert.assertNull(violation("$api.model", DeclarationKind.CLASS, "EditorNote"))
        Assert.assertNull(violation("$api.service", DeclarationKind.INTERFACE, "EditorService"))
        Assert.assertEquals(
            "api declaration must start with Editor: Note",
            violation("$api.model", DeclarationKind.CLASS, "Note"),
        )
    }

    @Test
    fun useCasesEndWithUseCase() {
        Assert.assertNull(violation(domainUseCase, DeclarationKind.CLASS, "LoadNoteUseCase"))
        Assert.assertNull(violation(domainUseCase, DeclarationKind.CLASS, "SaveNoteUseCase"))
        Assert.assertEquals(
            "use case must end with UseCase: LoadNote",
            violation(domainUseCase, DeclarationKind.CLASS, "LoadNote"),
        )
    }

    @Test
    fun domainModelNamesAreFree() {
        Assert.assertNull(violation(domainModel, DeclarationKind.CLASS, "Note"))
        Assert.assertNull(violation(domainModel, DeclarationKind.CLASS, "NoteDraft"))
    }

    @Test
    fun repositoryContractsAndImplementationsAreSeparatedByPackage() {
        Assert.assertNull(violation(domainRepository, DeclarationKind.INTERFACE, "EditorRepository"))
        Assert.assertEquals(
            "repository contract must end with Repository: EditorRepo",
            violation(domainRepository, DeclarationKind.INTERFACE, "EditorRepo"),
        )
        Assert.assertEquals(
            "repository implementation belongs to data.repository: DefaultEditorRepository",
            violation(domainRepository, DeclarationKind.CLASS, "DefaultEditorRepository"),
        )
        Assert.assertNull(violation(dataRepository, DeclarationKind.CLASS, "DefaultEditorRepository"))
        Assert.assertNull(violation(dataRepository, DeclarationKind.CLASS, "SqlDelightNoteRepository"))
        Assert.assertEquals(
            "repository implementation must end with Repository: EditorRepositoryImpl",
            violation(dataRepository, DeclarationKind.CLASS, "EditorRepositoryImpl"),
        )
    }

    @Test
    fun dataSourcesAndDtosAreNamedByRole() {
        Assert.assertNull(violation(dataSource, DeclarationKind.CLASS, "EditorDataSource"))
        Assert.assertNull(violation(dataSource, DeclarationKind.CLASS, "NoteDto"))
        Assert.assertEquals(
            "data source must end with DataSource and its DTO with Dto: EditorStorage",
            violation(dataSource, DeclarationKind.CLASS, "EditorStorage"),
        )
        Assert.assertNull(violation(dataDto, DeclarationKind.CLASS, "NoteDto"))
        Assert.assertEquals(
            "DTO must end with Dto: NoteData",
            violation(dataDto, DeclarationKind.CLASS, "NoteData"),
        )
    }

    @Test
    fun mappersAreClassesOrToFunctions() {
        Assert.assertNull(violation(dataMapper, DeclarationKind.CLASS, "NoteMapper"))
        Assert.assertEquals(
            "mapper class must end with Mapper: NoteConverter",
            violation(dataMapper, DeclarationKind.CLASS, "NoteConverter"),
        )
        Assert.assertNull(violation(dataMapper, DeclarationKind.FUNCTION, "toNote"))
        Assert.assertNull(violation(dataMapper, DeclarationKind.FUNCTION, "toDto"))
        Assert.assertEquals(
            "mapper function must be named to<Type>: mapNote",
            violation(dataMapper, DeclarationKind.FUNCTION, "mapNote"),
        )
    }

    @Test
    fun storeImplementationIsTheDefaultStore() {
        Assert.assertNull(violation(presentation, DeclarationKind.CLASS, "DefaultEditorStore"))
        Assert.assertEquals(
            "store implementation must be named DefaultEditorStore: EditorStore",
            violation(presentation, DeclarationKind.CLASS, "EditorStore"),
        )
        Assert.assertNull(violation(presentation, DeclarationKind.FUNCTION, "toEditorState"))
        Assert.assertEquals(
            "mapper function must be named to<Type>: mapState",
            violation(presentation, DeclarationKind.FUNCTION, "mapState"),
        )
    }

    @Test
    fun metroBindingsStartWithDefault() {
        Assert.assertNull(violation(di, DeclarationKind.CLASS, "DefaultEditorStoreFactory"))
        Assert.assertEquals(
            "Metro binding must start with Default: EditorStoreFactory",
            violation(di, DeclarationKind.CLASS, "EditorStoreFactory"),
        )
    }

    @Test
    fun componentsAreFeatureComponents() {
        Assert.assertNull(violation(component, DeclarationKind.INTERFACE, "EditorComponent"))
        Assert.assertNull(violation(component, DeclarationKind.INTERFACE, "EditorComponentFactory"))
        Assert.assertNull(violation(component, DeclarationKind.CLASS, "DefaultEditorComponent"))
        Assert.assertNull(violation(component, DeclarationKind.CLASS, "DefaultEditorComponentFactory"))
        Assert.assertEquals(
            "component must be <Feature>Component or Default<Feature>Component: EditorAdapter",
            violation(component, DeclarationKind.CLASS, "EditorAdapter"),
        )
    }

    @Test
    fun screensAreNamedScreen() {
        Assert.assertNull(violation(ui, DeclarationKind.FUNCTION, "EditorScreen", listOf("Composable")))
        Assert.assertEquals(
            "screen composable must end with Screen: Editor",
            violation(ui, DeclarationKind.FUNCTION, "Editor", listOf("Composable")),
        )
        Assert.assertNull(violation(ui, DeclarationKind.FUNCTION, "formatTitle"))
    }

    @Test
    fun designSystemApiUsesHbPrefix() {
        Assert.assertNull(violation(designSystemTheme, DeclarationKind.CLASS, "HbColors"))
        Assert.assertNull(violation(designSystemTheme, DeclarationKind.OBJECT, "HbTypography"))
        Assert.assertEquals(
            "design system public API must start with Hb: Shapes",
            violation(designSystemTheme, DeclarationKind.OBJECT, "Shapes"),
        )
        Assert.assertNull(NamingRules.violation(designSystemTheme, DeclarationKind.OBJECT, "helper", isPublic = false))
    }

    @Test
    fun declarationsOutsideArchitectureRolesHaveNoRule() {
        Assert.assertNull(
            violation("io.github.aequicor.heartbeat.core.platformapi", DeclarationKind.CLASS, "PlatformName"),
        )
        Assert.assertNull(
            violation("io.github.aequicor.heartbeat.di", DeclarationKind.OBJECT, "Scopes"),
        )
        Assert.assertNull(violation(feature, DeclarationKind.CLASS, "EditorSomething"))
        Assert.assertNull(violation("$feature.unknown", DeclarationKind.CLASS, "EditorSomething"))
    }
}
