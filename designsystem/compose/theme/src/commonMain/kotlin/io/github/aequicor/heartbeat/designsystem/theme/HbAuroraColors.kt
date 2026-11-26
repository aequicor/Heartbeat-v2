package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes

/** Light Aurora roles from the Aurora catalog, including its trusted request surface. */
object HbAuroraColors {
    private val text = HbColorPalettes.Light.toHbColors().text
    private val floatingPanelTint = Color(0xFFE0F6D9)
    val SyntaxKeyword = Color(0xFF643B83)
    val SyntaxString = Color(0xFF315E39)
    val SyntaxComment = Color(0xFF62665F)
    val SyntaxNumber = Color(0xFF884A17)
    val DiffAddedInk = Color(0xFF255D2D)
    val DiffAddedSurface = Color(0xFFEAF4E9)
    val DiffDeletedInk = Color(0xFF91362C)
    val DiffDeletedSurface = Color(0xFFFBEEEC)
    val Canvas = Color(0xFFF7F8FA)
    val DesktopCanvas = Color.White

    @Suppress("MagicNumber")
    fun windowFrame(start: Offset, end: Offset): Brush = Brush.linearGradient(
        0f to Color(0xFFCBB6F6),
        0.27f to Color(0xFFE6DBF6),
        0.56f to Color(0xFFC2E3CC),
        0.76f to Color(0xFFC6E8EB),
        1f to Color(0xFFF5D3E0),
        start = start,
        end = end,
    )

    val Pearl = Color(0xFFFEFDFC)
    val AgentMessage = Color.White
    val MessageOutline = Color(0xFFDFE2D8)
    val MessageDivider = Color(0xFFCDD7CC)
    val HeaderOutline = Color(0xFFE4E3DE)
    val HeaderAccessSurface = Color.White
    val HeaderAccessOutline = Color(0xFFCDD7CC)
    val HeaderShadow = Color(0x14222B27)
    val HeaderContactShadow = Color(0x0A222B27)
    val ViewerSurface = Color.White
    val ViewerDivider = MessageDivider
    val ViewerCloseInk = Color(0xFF5A5663)
    val FileBackInk = Color(0xFF302E39)
    val FileTypeSurface = Color(0xFFEAE9E6)
    val HeaderMetricTrack = Color(0xFFE2E2E5)
    val HeaderMetricAccent = Color(0xFF965900)
    val TouchHeaderMetricAccent = Color(0xFFA26B13)
    val ContentMask = Color.Black
    val ComposerSurface = floatingPanelTint.copy(alpha = 0.90f)
    val ComposerSheen = Brush.horizontalGradient(
        0f to Color(0x50F4FFF0), 0.38f to Color.Transparent, 1f to Color(0x60D5F0DD),
    )
    val ComposerRim = Color(0xD9FFFFFF)
    val ComposerPlaceholder = Color(0xFF56566B)
    val ComposerAttachment = Color(0xFFEDE4F6)
    val ComposerSend = Color(0xFF8062A8)
    val ComposerShadow = Color(0x33203D30)
    val ComposerContactShadow = Color(0x1A203D30)
    val User = Color(0xFFF1FEE1)
    val DesktopInk = text.primary
    val DesktopPrimaryAction = Color(0xFF3F1D62)
    val DesktopOnPrimaryAction = Color.White
    val DesktopWorkMuted = text.muted
    val MessageInk = text.primary
    val MessageActionSurface = Color(0xFFC6DDBE)
    val MessageActionBadge = MessageActionSurface
    val MessageActionOutline = Color(0x40506E4B)
    val MessageActionHover = Color(0x1A506E4B)
    val MessageActionError = Color(0xFF962218)
    val MessageMuted = text.muted
    val Ink = text.primary
    val Muted = text.secondary
    val Primary = Color(0xFF75629F)
    val Lavender = Color(0xFFE8DFF7)
    val Line = Color(0xFFE6E1E9)
    val Green = Color(0xFF4D6B48)
    val Current = Color(0xFFEEF4EA)
    val Error = Color(0xFFB42318)
    val Warning = Color(0xFF805500)
    val Request = Color(0xFF30251D)
    val RequestField = Color(0xFF382D24)
    val Amber = Color(0xFFE9BA72)
    val RequestText = Color(0xFFFFF2DF)
    val RequestSelected = Color(0xFF493924)
    val RequestSecondary = Color(0xFFCEC1AF)
    val RequestOnAccent = Color(0xFF21180F)
    val RequestBorder = Color(0xFF9F8A70)
    val RequestTrack = Color(0xFF5C4832)
    val RequestError = Color(0xFFFFB4AB)
    val ChatPanelShadow = Color(0x1F1F2624)
    val ChatPanelContactShadow = Color(0x0F1F2624)
    val ChatBackdrop = Color(0xFFEDF1EB)
    val WorkspaceRail = Color(0xFFEDEEF0)
    val WorkspaceDisabled = Color(0xFF969BA1)
    val WorkspaceFooterInk = Color(0xFF555D68)
    val NavigationPanelOutline = Color(0xFFC9CDD6)
    val NavigationPanelShadow = Color(0x2120232A)
    val FloatingHeaderSurface = floatingPanelTint.copy(alpha = 0.86f)
    val FloatingHeaderRim = Color(0x6EFFFFFF)
    val WorkspaceDivider = Color(0xFFD9DBE0)
    val WorkspaceActive = Color(0xFF20232A)
    val WorkspaceInactive = Color(0xFF5F6368)
    val NavigationHover = Color(0xFFF7F6F9)
    val NavigationPrimaryAction = DesktopPrimaryAction
    val NavigationOnPrimaryAction = DesktopOnPrimaryAction
    val NavigationSearchSurface = Color.White
    val NavigationSearchOutline = Color(0xFFDFE2D8)
    val NavigationInk = text.primary
    val NavigationIcon = Color(0xFF555D67)
    val NavigationFolderActive = Color(0xFF8062A8)
    val NavigationMuted = text.secondary
    val NavigationSelected = Color(0xFFF2EFF5)
    val NavigationSelectedHover = Color(0xFFE9E4EF)
    val NavigationBranch = Color(0xFF918B99)
    val SessionWorking = Color(0xFFC64157)
    val SessionAttention = Color(0xFF965900)
    val SessionUnread = Color(0xFF2A8150)
    val StageComplete = Color(0xFF8AA38D)
    val StageLine = Color(0xFFD2DDD0)
    val WorkDivider = Color(0xFFD8DDD2)
    val WorkInk = text.primary
    val WorkMuted = text.secondary
    val WorkRunning = Color(0xFF76747C)
    val WorkHistory = Color(0xFFF0F2EB)
    val WorkAccess = Color(0xFFF4F4F1)
    val WorkAccessHover = Color(0xFFE8E7E6)
    val WorkHover = Color(0xFFF4F3F2)
}
