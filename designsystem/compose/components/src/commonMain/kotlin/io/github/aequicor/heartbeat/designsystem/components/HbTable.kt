@file:Suppress("NoNullableLambdaWithDefaultNull")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Compact two-dimensional table (HB-DS-019).
 *
 * The header names the columns and a row is one action only when [onRowClick] is provided; without
 * a handler a row is plain content, so the table does not announce actions without a result. The
 * table exposes collection and item semantics, so a screen reader can describe the position of a
 * cell.
 *
 * The row action is attached without merging the descendants: a clickable row would hide the cell
 * nodes and their collection coordinates, so the row receives a pointer tap, a keyboard activation
 * and the `onClick` semantics action of a `Button`, while each cell keeps its own
 * `CollectionItemInfo` and the header keeps the column headings. Columns keep a bounded width and
 * the table scrolls horizontally instead of squeezing its cells; ordinary key/value records should
 * use a stacked list instead of this table.
 *
 * Every row is composed at once, so the table is intended for a bounded, screen-sized number of
 * rows; a large or unbounded dataset belongs to a lazy or paginated list, not this component.
 */
@Composable
fun HbTable(
    columns: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    onRowClick: ((Int) -> Unit)? = null,
) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .semantics {
                collectionInfo = CollectionInfo(rowCount = rows.size + 1, columnCount = columns.size)
            },
    ) {
        TableHeader(columns = columns, colors = colors)
        HbDivider()
        rows.forEachIndexed { index, row ->
            TableRow(
                cells = row,
                rowIndex = index,
                colors = colors,
                onClick = onRowClick?.let { click -> { click(index) } },
            )
            if (index < rows.lastIndex) {
                HbDivider()
            }
        }
    }
}

@Composable
private fun TableHeader(columns: List<String>, colors: HbColors) {
    Row(
        modifier = Modifier
            .background(colors.surface.subtle)
            .padding(vertical = HbSpacing.Xs),
    ) {
        columns.forEachIndexed { index, column ->
            BasicText(
                text = column,
                style = HbTypography.Metadata,
                color = { colors.text.secondary },
                modifier = Modifier
                    .width(COLUMN_WIDTH)
                    .padding(horizontal = HbSpacing.S)
                    .semantics {
                        heading()
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = 0,
                            rowSpan = 1,
                            columnIndex = index,
                            columnSpan = 1,
                        )
                    },
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    rowIndex: Int,
    colors: HbColors,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .focusable()
                        .onKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.Spacebar)
                            ) {
                                onClick()
                                true
                            } else {
                                false
                            }
                        }
                        .pointerInput(onClick) {
                            detectTapGestures { onClick() }
                        }
                        .semantics {
                            role = Role.Button
                            onClick(label = null) {
                                onClick()
                                true
                            }
                        }
                } else {
                    Modifier
                },
            )
            .padding(vertical = HbSpacing.Xs),
    ) {
        cells.forEachIndexed { index, cell ->
            BasicText(
                text = cell,
                style = HbTypography.Label,
                color = { colors.text.primary },
                modifier = Modifier
                    .width(COLUMN_WIDTH)
                    .padding(horizontal = HbSpacing.S)
                    .semantics {
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = rowIndex + 1,
                            rowSpan = 1,
                            columnIndex = index,
                            columnSpan = 1,
                        )
                    },
            )
        }
    }
}

private val COLUMN_WIDTH = 200.dp
