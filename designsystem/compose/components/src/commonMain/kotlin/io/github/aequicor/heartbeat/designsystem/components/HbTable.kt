package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Compact two-dimensional table (HB-DS-019).
 *
 * The header names the columns and a row is one action, so selection and opening a record stay
 * separate concerns of the owner. Columns keep a bounded width and the table scrolls horizontally
 * instead of squeezing its cells; ordinary key/value records should use a stacked list instead of
 * this table.
 */
@Composable
fun HbTable(
    columns: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    onRowClick: (Int) -> Unit = {},
) {
    val colors = HbTheme.colors

    Column(modifier = modifier.horizontalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .background(colors.surface.subtle)
                .padding(vertical = HbSpacing.Xs),
        ) {
            columns.forEach { column ->
                BasicText(
                    text = column,
                    style = HbTypography.Metadata,
                    color = { colors.text.secondary },
                    modifier = Modifier
                        .width(COLUMN_WIDTH)
                        .padding(horizontal = HbSpacing.S),
                )
            }
        }
        HbDivider()
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .clickable(role = Role.Button) { onRowClick(index) }
                    .padding(vertical = HbSpacing.Xs),
            ) {
                row.forEach { cell ->
                    BasicText(
                        text = cell,
                        style = HbTypography.Label,
                        color = { colors.text.primary },
                        modifier = Modifier
                            .width(COLUMN_WIDTH)
                            .padding(horizontal = HbSpacing.S),
                    )
                }
            }
            if (index < rows.lastIndex) {
                HbDivider()
            }
        }
    }
}

private val COLUMN_WIDTH = 200.dp
