package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeLine
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import java.awt.Point
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun verifyNativeCode(
    host: AuroraNativeBenchmarkWindow, input: AuroraNativeInput, measureGestures: Boolean = false,
) {
    val rows = nativeCodeRows()
    val raw = rows.joinToString("\n")
    val selected = rows[0].substring(FirstIndent) + "\n" + rows[1]
    val block = ReplyBlock.Code(0, "Kotlin", raw, rows.map { ReplyCodeLine(it, listOf()) }, closed = true)
    host.open(NativeReplyFixture(raw, rows[0], listOf(), ReplyDocument(listOf(block))))
    withNativeClipboard(setOf(raw, selected)) {
        verifyNativeCopy(host, input, rows.take(2), raw, selected, measureGestures)
        verifyHorizontalThumb(input)
        verifyVerticalCodeScroll(host, input, rows, measureGestures)
    }
}

internal fun verifyCompactCodeCopy(host: AuroraNativeBenchmarkWindow, input: AuroraNativeInput) {
    val rows = List(12) { index -> if (index == 6) "\tval next = 6" else "    val line$index = $index" }
    val raw = rows.joinToString("\n", postfix = "\n")
    host.open(NativeReplyFixture("```kotlin\n${raw}```", rows.first(), listOf(rows.last())))
    val expected = rows[5].drop(FirstIndent) + "\n" + rows[6]
    withNativeClipboard(setOf(raw, expected)) {
        verifyNativeCopy(host, input, rows.subList(5, 7), raw, expected, measureGestures = false,
            screenshotPrefix = "native-compact-code")
    }
}

private const val CodeRows = 2_050
private const val FirstIndent = 4

private fun nativeCodeRows() = List(CodeRows) { index ->
    when (index) {
        0 -> "    val proof = first"
        1 -> "\tval proof2 = second"
        2 -> "    val veryLong = " + "expression + ".repeat(30) + "end"
        else -> "    val nativeLine$index = checkStatus($index)"
    }
}

private fun verifyNativeCopy(
    host: AuroraNativeBenchmarkWindow, input: AuroraNativeInput, firstRows: List<String>,
    raw: String, selected: String, measureGestures: Boolean, screenshotPrefix: String = "native-code",
) {
    input.screenshot("$screenshotPrefix-before")
    input.click(input.control("Копировать исходник"))
    nativeUntil("Copy toolbar must preserve exact code including tabs and spaces") { clipText() == raw }

    val first = input.cursor(firstRows[0], FirstIndent)
    val zero = input.cursor(firstRows[0], 0)
    val end = input.cursor(firstRows[1], firstRows[1].length)
    assertTrue(first.x > zero.x && end.y > first.y, "Selection must cross two laid-out, indented source lines")
    val before = firstRows.map(input::selectionInk)
    val timing = host.measureCodeGestureIf(measureGestures, NativeCodeGesture.Drag) { input.drag(first, end) }
    nativeUntil("The drag must visibly select both source lines before Cmd+C") {
        firstRows.zip(before).all { (row, previousInk) -> input.selectionInk(row) > previousInk + 30 }
    }
    input.screenshot("$screenshotPrefix-selection")
    assertTrue(clipText() == raw, "Clipboard before Cmd+C must still contain the whole source")
    println("Native code drag from $first to $end, selection ink=${firstRows.map(input::selectionInk)}")
    input.copySelection()
    awaitExactSelectionCopy(raw, selected)
    timing?.report()
}

private fun awaitExactSelectionCopy(raw: String, expected: String) {
    try {
        nativeUntil("Cmd+C must copy exactly the selected range across source lines") { clipText() == expected }
    } catch (failure: AssertionError) {
        val actual = clipText()
        val description = when {
            actual == raw -> "clipboard still contains whole source"
            actual == null -> "clipboard no longer has text"
            else -> "clipboard changed to a text of length ${actual.length}, expected ${expected.length}"
        }
        throw AssertionError("Cmd+C selection mismatch ($description)", failure)
    }
}

private fun verifyHorizontalThumb(input: AuroraNativeInput) {
    val bar = input.control("Горизонтальная прокрутка")
    val axis = SemanticsProperties.HorizontalScrollAxisRange
    val original = input.scrollPosition(axis)
    val y = bar.y + bar.height / 2
    val middle = Point(bar.x + bar.width / 2, y)
    input.drag(Point(bar.x + 8, y), middle)
    nativeUntil("Native horizontal thumb drag must move the code") { input.scrollPosition(axis) > original }
    input.screenshot("native-code-horizontal")
    input.drag(middle, Point(bar.x + 4, y))
    nativeUntil("The horizontal scrollbar must return to the short code lines") {
        input.scrollPosition(axis) <= original + 1f
    }
}

private fun verifyVerticalCodeScroll(
    host: AuroraNativeBenchmarkWindow, input: AuroraNativeInput, rows: List<String>, measureGestures: Boolean,
) {
    val bar = input.control("Прокрутка кода")
    val originalHistory = host.historyPosition()
    val firstCount = host.window.nativeCodeNodeCount()
    val axis = host.window.codeScrollAxis(rows[0])
    val original = nativeEdt { axis.value() }
    val initiallyVisible = host.window.visibleNativeCodeRows()
    input.moveTo(Point(bar.x - 80, bar.y + bar.height / 2))
    val timing = host.measureCodeGestureIf(measureGestures, NativeCodeGesture.Wheel) {
        repeat(14) {
            input.wheel(-2)
            Thread.sleep(26)
        }
    }
    nativeUntil("Native wheel must advance the local code viewport and reveal a later line") {
        nativeEdt { axis.value() } > original && (host.window.visibleNativeCodeRows() - initiallyVisible).isNotEmpty()
    }
    val later = (host.window.visibleNativeCodeRows() - initiallyVisible).toList()
    input.requireTextInk(later[later.size / 2].trimStart())
    assertEquals(originalHistory, host.historyPosition(), "Local wheel should preserve the history anchor")
    val current = nativeEdt { axis.value() }
    println("Native long code scroll position $original -> $current; history anchor $originalHistory")
    timing?.report()
    input.screenshot("native-code-local-wheel")

    val thumbX = bar.x + bar.width / 2
    input.drag(Point(thumbX, bar.y + 10), Point(thumbX, bar.y + bar.height - 4))
    nativeUntil("The vertical thumb must make row #$CodeRows visibly reachable") {
        host.window.textVisible(rows.last())
    }
    val lastCount = host.window.nativeCodeNodeCount()
    assertTrue(firstCount in 8..42 && lastCount in 8..42, "Code nodes near first=$firstCount and last=$lastCount")
    assertEquals(originalHistory, host.historyPosition())
    input.screenshot("native-code-scrolled")

    input.drag(Point(thumbX, bar.y + bar.height - 12), Point(thumbX, bar.y + 5))
    nativeUntil("Dragging the vertical thumb back should show the first indented line") {
        host.window.textVisible(rows[0])
    }
    println("Native code fixture ($CodeRows rows), text nodes first=$firstCount, last=$lastCount")
}

internal fun withNativeClipboard(owned: Set<String>, verify: () -> Unit) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val saved = clipboard.getContents(null)
    try {
        verify()
    } finally {
        if (clipText() in owned) clipboard.setContents(saved ?: StringSelection(""), null)
    }
}

internal fun clipText(): String? =
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()

private fun ComposeWindow.nativeCodeNodeCount() = nativeEdt { nativeNodes().count { it.hasText("    val nativeLine") } }

private fun ComposeWindow.textVisible(text: String) = nativeEdt { (findText(text)?.boundsInWindow?.height ?: 0f) > 0f }

private fun ComposeWindow.visibleNativeCodeRows(): Set<String> = nativeEdt {
    nativeNodes().filter { it.boundsInWindow.height >= 20f && intersectsContent(it.boundsInWindow) }
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().asSequence() }
        .map { it.text }.filter { it.startsWith("    val nativeLine") }.toSet()
}

private fun ComposeWindow.codeScrollAxis(row: String) = nativeEdt {
    generateSequence(requireNotNull(findText(row))) { it.parent }
        .firstNotNullOf { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) }
}

private fun AuroraNativeBenchmarkWindow.measureCodeGestureIf(
    enabled: Boolean, gesture: NativeCodeGesture, action: () -> Unit,
): NativeCodeGestureTiming? {
    if (enabled) return measureCodeGesture(gesture, action)
    action()
    return null
}
