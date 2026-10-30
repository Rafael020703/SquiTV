package rsv.squitv.ui.updates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.R
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.domain.model.AppUpdateInfo

@Composable
fun UpdateAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, icon: ImageVector? = null) {
    val colors = AppDesignSystem.colors
    val shape = AppDesignSystem.shapes.button
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).adaptiveFocus(shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) colors.primary else colors.surfaceVariant,
            contentColor = if (primary) colors.background else colors.textPrimary
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = AppDesignSystem.typography.label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun UpdateStatusCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = AppDesignSystem.colors.primary,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = AppDesignSystem.colors
    Surface(
        modifier = modifier.fillMaxWidth(), shape = AppDesignSystem.shapes.extraLarge,
        color = colors.surface, border = BorderStroke(1.dp, colors.border)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(52.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = AppDesignSystem.typography.title.copy(fontSize = 22.sp), fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(description, style = AppDesignSystem.typography.body.copy(fontSize = 14.sp), color = colors.textSecondary)
                }
            }
            content()
        }
    }
}

@Composable
fun UpdateVersionSummary(currentVersion: String, updateInfo: AppUpdateInfo? = null) {
    val colors = AppDesignSystem.colors
    Surface(shape = AppDesignSystem.shapes.medium, color = colors.backgroundSecondary) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.current_version_label), color = colors.textSecondary, style = AppDesignSystem.typography.caption)
                Text(currentVersion, color = colors.textPrimary, style = AppDesignSystem.typography.title)
            }
            if (updateInfo != null) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.available_version_label), color = colors.textSecondary, style = AppDesignSystem.typography.caption)
                    Text("v${updateInfo.versionName}", color = colors.primary, style = AppDesignSystem.typography.title)
                }
            }
        }
    }
}

/** Plain readable release notes; technical download/checksum sections stay on GitHub. */
fun releaseHighlights(markdown: String): List<String> {
    var excludedLevel: Int? = null
    var inCode = false
    return buildList {
        for (raw in markdown.take(40_000).lines()) {
            val line = raw.trim()
            if (line.startsWith("```")) { inCode = !inCode; continue }
            if (inCode || line.isBlank() || line.matches(Regex("[-_*]{3,}"))) continue
            val heading = Regex("^(#{1,6})\\s+(.+)").find(line)
            if (heading != null) {
                val level = heading.groupValues[1].length
                val title = heading.groupValues[2].lowercase()
                if (excludedLevel?.let { level <= it } == true) excludedLevel = null
                if (title.startsWith("download") || title.startsWith("verifica") || title.startsWith("checks") ||
                    title.startsWith("sha-256") || title.startsWith("assets") || title.startsWith("arquivos")) {
                    excludedLevel = level
                }
                continue
            }
            if (excludedLevel != null) continue
            val text = line.replace(Regex("!?\\[([^]]*)]\\([^)]+\\)"), "$1")
                .replace(Regex("^[-*+]\\s+|^\\d+[.)]\\s+|^>\\s*"), "")
                .replace("**", "").replace("__", "").replace("`", "").trim()
            if (text.isNotBlank() && !text.matches(Regex("[a-fA-F0-9]{64}"))) add(text)
            if (size >= 60) break
        }
    }
}

@Composable
fun UpdateReleaseNotes(info: AppUpdateInfo) {
    val lines = remember(info.changelog) { releaseHighlights(info.changelog) }
    var expanded by rememberSaveable(info.versionName) { mutableStateOf(false) }
    val colors = AppDesignSystem.colors
    Surface(shape = AppDesignSystem.shapes.extraLarge, color = colors.surface, border = BorderStroke(1.dp, colors.border)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.changelog_title), color = colors.textPrimary, style = AppDesignSystem.typography.title)
            if (lines.isEmpty()) Text(stringResource(R.string.no_changelog), color = colors.textSecondary)
            (if (expanded) lines else lines.take(4)).forEach { text ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", color = colors.primary)
                    Text(text, Modifier.weight(1f), color = colors.textSecondary, style = AppDesignSystem.typography.body.copy(fontSize = 14.sp))
                }
            }
            if (lines.size > 4) UpdateAction(
                text = stringResource(if (expanded) R.string.updater_show_less else R.string.updater_show_more),
                onClick = { expanded = !expanded }, primary = false
            )
        }
    }
}

@Composable
fun UpdateLoginNotice(info: AppUpdateInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppDesignSystem.colors
    Surface(
        onClick = onClick, modifier = modifier.widthIn(max = 360.dp).heightIn(min = 48.dp).adaptiveFocus(AppDesignSystem.shapes.large),
        color = colors.surface, shape = AppDesignSystem.shapes.large, border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f))
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(stringResource(R.string.updater_version_ready, info.versionName), color = colors.textPrimary, style = AppDesignSystem.typography.label,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.updater_view), color = colors.textSecondary, style = AppDesignSystem.typography.caption)
            }
        }
    }
}
