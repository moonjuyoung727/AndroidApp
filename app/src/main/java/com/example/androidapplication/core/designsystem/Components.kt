package com.example.androidapplication.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.data.ListSource
import java.time.Instant

/** 화면 좌우 여백. */
val ScreenPadding = 20.dp

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .surfaceCard()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** 레퍼런스의 원형 버튼. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    enabled: Boolean = true,
    tint: Color = NeuAccent,
    size: Dp = 48.dp,
    background: Color = NeuSurfaceStrong
) {
    Box(modifier.size(size)) {
        Box(
            Modifier
                .matchParentSize()
                .alpha(if (enabled) 1f else 0.4f)
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.46f)) }
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).background(NeuDanger, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) { Text(if (badge > 99) "99+" else "$badge", color = NeuBg, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** 카드 모서리의 흐린 아이콘. 자세한 내용은 눌렀을 때만 보여 준다. */
@Composable
fun DimIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = NeuMuted) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
}

@Composable
fun BigNumber(value: String, unit: String? = null, modifier: Modifier = Modifier, color: Color = NeuAccent, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displaySmall) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, Modifier.alignByBaseline(), color = color, style = style, maxLines = 1)
        if (unit != null) Text(unit, Modifier.alignByBaseline(), color = NeuMuted, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, ringSize: Dp = 72.dp, center: @Composable BoxScope.() -> Unit) {
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0x1FFFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(NeuAccent, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/** 상태는 색만으로 구분하지 않고 라벨을 함께 쓴다. */
@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, color = NeuMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** 탭형 선택. 선택된 항목만 면을 가진다. */
@Composable
fun <K> ChipRow(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            Box(
                Modifier
                    .alpha(if (enabled) 1f else 0.5f)
                    .clip(CircleShape)
                    .background(if (isSelected) NeuSurfaceStrong else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label, color = if (isSelected) NeuAccent else NeuMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    counter: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            enabled = enabled,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            shape = NeuFieldShape,
            colors = cleanFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon?.let { icon -> { Icon(icon, null, tint = NeuMuted, modifier = Modifier.size(20.dp)) } },
            trailingIcon = trailingIcon
        )
        if (error != null || supporting != null || counter != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(error ?: supporting.orEmpty(), Modifier.weight(1f), color = if (error != null) NeuDanger else NeuMuted, style = MaterialTheme.typography.labelMedium)
                if (counter != null) Text(counter, color = NeuMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 밑줄 없는 채움형 입력창. */
@Composable
fun cleanFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedTextColor = NeuAccent,
    unfocusedTextColor = NeuAccent,
    errorTextColor = NeuAccent,
    disabledTextColor = NeuMuted,
    focusedContainerColor = NeuSurface,
    unfocusedContainerColor = NeuSurface,
    errorContainerColor = NeuSurface,
    disabledContainerColor = NeuSurface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    focusedLabelColor = NeuMuted,
    unfocusedLabelColor = NeuMuted,
    disabledLabelColor = NeuMuted,
    errorLabelColor = NeuDanger,
    cursorColor = NeuAccent,
    errorCursorColor = NeuDanger
)

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    NeuTextField(
        value = value,
        onValueChange = onValueChange,
        label = placeholder,
        modifier = modifier,
        leadingIcon = Icons.Default.Search,
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "검색어 지우기", tint = NeuMuted) } }
        } else null
    )
}

@Composable
fun NeuOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = NeuAccent
) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(NeuSurfaceStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    NeuButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = NeuAccent, enabled: Boolean = true) {
    Text(
        text,
        modifier.alpha(if (enabled) 1f else 0.4f).clip(NeuSmallShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1
    )
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = NeuAccent) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, color = NeuMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Text(value, Modifier.weight(1f), color = valueColor, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** 행을 묶는 카드. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    NeuCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), content = content)
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    titleColor: Color = NeuAccent,
    enabled: Boolean = true,
    divider: Boolean = true,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier.alpha(if (enabled) 1f else 0.5f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = if (titleColor == NeuAccent) NeuMuted else titleColor, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = NeuMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Text(value, color = NeuMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            when {
                trailing != null -> trailing()
                onClick != null && showChevron -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NeuMuted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 20.dp), color = NeuLine)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = NeuMuted.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) Text(message, color = NeuMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    dismissLabel: String = "취소"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuBg,
        shape = NeuCardShape,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, color = NeuMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (danger) NeuDanger else NeuAccent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = NeuMuted) } }
    )
}

@Composable
fun SettingToggle(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, color = NeuMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeuBg,
                checkedTrackColor = NeuAccent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = NeuMuted,
                uncheckedTrackColor = NeuSurfaceStrong,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun <K> SettingSelect(
    title: String,
    description: String?,
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, color = NeuMuted, style = MaterialTheme.typography.bodySmall)
        }
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), color = NeuMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = NeuMuted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NeuBg) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(key) })
                }
            }
        }
    }
}

/** 안내 문구. 상자 없이 작은 글자로만 표시한다. */
@Composable
fun NoticeBox(text: String, modifier: Modifier = Modifier, color: Color = NeuMuted) {
    Text(text, modifier.fillMaxWidth().padding(horizontal = 4.dp), color = color, style = MaterialTheme.typography.bodySmall)
}

/** 조회 상태와 마지막 갱신 시각. */
@Composable
fun DataFreshness(source: ListSource, fetchedAt: Instant?, modifier: Modifier = Modifier) {
    val time = fetchedAt?.let(KstTime::time)
    val text = when {
        source == ListSource.Loading && time == null -> source.label
        source == ListSource.Stale -> "${source.label}${time?.let { " · $it" } ?: ""}"
        else -> "${source.label} · $time"
    }
    Text(text, modifier, color = if (source == ListSource.Stale) NeuWarning else NeuMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
}

@Composable
fun Divider() = HorizontalDivider(color = NeuLine)

@Composable
fun FullScreenMessage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
}

@Composable
fun VerticalGap(height: Int = 12) = Spacer(Modifier.height(height.dp))

/** 자세한 내용과 드문 동작을 담는 시트. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanSheet(onDismiss: () -> Unit, title: String? = null, scrollable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NeuBg) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 28.dp)
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                VerticalGap(12)
            }
            content()
        }
    }
}
