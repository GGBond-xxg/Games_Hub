package com.bond.md3elauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.md3elauncher.data.ThemeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Application adapters preserve existing callbacks and layout contracts while using each library's controls.
@Composable
internal fun HubSurface(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 0.dp, border: BorderStroke? = null, content: @Composable () -> Unit) {
    val glass = LocalThemePreferences.current.style == ThemeStyle.GLASS
    Surface(if (glass) modifier.styleSurface(shape, color) else modifier, shape, if (glass) Color.Transparent else color,
        contentColor, if (glass) 0.dp else tonalElevation, if (glass) 0.dp else shadowElevation, if (glass) null else border, content)
}

@Composable
internal fun HubCard(modifier: Modifier = Modifier, shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(), elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {
    when (LocalThemePreferences.current.style) {
        ThemeStyle.M3 -> Card(modifier, shape, colors, elevation, border, content)
        ThemeStyle.MIUIX -> top.yukonga.miuix.kmp.basic.Card(modifier) {
            CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurfaceContainer) { content() }
        }
        ThemeStyle.GLASS -> HubSurface(modifier, shape, colors.containerColor, colors.contentColor, border = border) { Column(content = content) }
    }
}

@Composable
internal fun HubElevatedCard(modifier: Modifier = Modifier, shape: Shape = CardDefaults.elevatedShape,
    colors: CardColors = CardDefaults.elevatedCardColors(), elevation: CardElevation = CardDefaults.elevatedCardElevation(),
    content: @Composable ColumnScope.() -> Unit) {
    if (LocalThemePreferences.current.style == ThemeStyle.M3) ElevatedCard(modifier, shape, colors, elevation, content)
    else HubCard(modifier, shape, colors, elevation, content = content)
}

@Composable
internal fun HubElevatedCard(onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape = CardDefaults.elevatedShape,
    content: @Composable ColumnScope.() -> Unit) {
    if (LocalThemePreferences.current.style == ThemeStyle.M3) ElevatedCard(onClick = onClick, modifier = modifier, shape = shape, content = content)
    else HubElevatedCard(modifier.clickable(onClick = onClick), shape, content = content)
}

@Composable
internal fun HubOutlinedCard(modifier: Modifier = Modifier, shape: Shape = CardDefaults.outlinedShape,
    colors: CardColors = CardDefaults.outlinedCardColors(), elevation: CardElevation = CardDefaults.outlinedCardElevation(),
    border: BorderStroke = CardDefaults.outlinedCardBorder(), content: @Composable ColumnScope.() -> Unit) {
    if (LocalThemePreferences.current.style == ThemeStyle.M3) OutlinedCard(modifier, shape, colors, elevation, border, content)
    else HubCard(modifier, shape, colors, elevation, border, content)
}

@Composable
internal fun HubOutlinedCard(onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape = CardDefaults.outlinedShape,
    content: @Composable ColumnScope.() -> Unit) {
    if (LocalThemePreferences.current.style == ThemeStyle.M3) OutlinedCard(onClick = onClick, modifier = modifier, shape = shape, content = content)
    else HubOutlinedCard(modifier.clickable(onClick = onClick), shape, content = content)
}

@Composable
private fun StyledButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, shape: Shape,
    colors: ButtonColors, contentPadding: PaddingValues, kind: Int, border: BorderStroke?, content: @Composable RowScope.() -> Unit) {
    when (LocalThemePreferences.current.style) {
        ThemeStyle.MIUIX -> top.yukonga.miuix.kmp.basic.Button(onClick, modifier, enabled,
            colors = when (kind) {
                0 -> top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
                3 -> top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent,
                    contentColor = MiuixTheme.colorScheme.primary)
                else -> top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors()
            },
            insideMargin = contentPadding, minHeight = 48.dp) {
            CompositionLocalProvider(LocalContentColor provides top.yukonga.miuix.kmp.theme.LocalContentColor.current) { content() }
        }
        ThemeStyle.GLASS -> {
            val foreground = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            val tint = if (kind == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            // Draw the material inside the measured control, once. A Material Button's
            // extra touch-target layer used to produce a second, displaced capsule.
            val surface = if (kind == 3) Modifier else Modifier.styleSurface(shape, tint)
            Row(modifier.heightIn(min = 48.dp).clip(shape).then(surface)
                .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .padding(contentPadding), horizontalArrangement = Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides foreground) {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
                }
            }
        }
        ThemeStyle.M3 -> when (kind) {
            1 -> FilledTonalButton(onClick, modifier, enabled, shape, colors, contentPadding = contentPadding, content = content)
            2 -> OutlinedButton(onClick, modifier, enabled, shape, colors, border = border, contentPadding = contentPadding, content = content)
            3 -> TextButton(onClick, modifier, enabled, shape, colors, contentPadding = contentPadding, content = content)
            else -> Button(onClick, modifier, enabled, shape, colors, contentPadding = contentPadding, content = content)
        }
    }
}
@Composable
internal fun HubButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) =
    StyledButton(onClick, modifier, enabled, shape, colors, contentPadding, 0, null, content)
@Composable
internal fun HubFilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape, colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) =
    StyledButton(onClick, modifier, enabled, shape, colors, contentPadding, 1, null, content)
@Composable
internal fun HubOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled), contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit) = StyledButton(onClick, modifier, enabled, shape, colors, contentPadding, 2, border, content)
@Composable
internal fun HubTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding, content: @Composable RowScope.() -> Unit) =
    StyledButton(onClick, modifier, enabled, shape, colors, contentPadding, 3, null, content)

@Composable
internal fun HubIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)) {
        if (LocalThemePreferences.current.style == ThemeStyle.MIUIX) top.yukonga.miuix.kmp.basic.IconButton(onClick, modifier, enabled, content = content)
        else IconButton(onClick, modifier, enabled, content = content)
    }
}
@Composable
internal fun HubSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalThemePreferences.current.style == ThemeStyle.MIUIX) top.yukonga.miuix.kmp.basic.Switch(checked, onCheckedChange, modifier, enabled = enabled)
    else if (LocalThemePreferences.current.style == ThemeStyle.GLASS) GlassSwitch(checked, onCheckedChange, modifier, enabled)
    else Switch(checked, onCheckedChange, modifier, enabled = enabled)
}
@Composable
internal fun HubSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null) {
    if (LocalThemePreferences.current.style == ThemeStyle.MIUIX) top.yukonga.miuix.kmp.basic.Slider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished)
    else if (LocalThemePreferences.current.style == ThemeStyle.GLASS) GlassSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished)
    else Slider(value, onValueChange, modifier, enabled, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
}
@Composable
internal fun HubFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null) {
    if (LocalThemePreferences.current.style == ThemeStyle.M3) FilterChip(selected, onClick, label, modifier, enabled, leadingIcon = leadingIcon)
    else StyledButton(onClick, modifier.semantics { this.selected = selected }, enabled, RoundedCornerShape(14.dp),
        ButtonDefaults.buttonColors(), PaddingValues(horizontal = 14.dp, vertical = 8.dp), if (selected) 0 else 1, null) {
        leadingIcon?.invoke(); label()
    }
}

@Composable
internal fun HubOutlinedTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null, singleLine: Boolean = false, minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = MaterialTheme.shapes.small) {
    if (LocalThemePreferences.current.style == ThemeStyle.MIUIX) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            label?.invoke()
            Box {
              top.yukonga.miuix.kmp.basic.TextField(value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled, readOnly = readOnly,
                textStyle = textStyle, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
                trailingIcon = trailingIcon, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions)
              if (value.isEmpty() && placeholder != null) Box(Modifier.matchParentSize().padding(start = 16.dp, end = if (trailingIcon == null) 16.dp else 72.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                  CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurfaceVariantSummary) { placeholder() }
              }
            }
        }
    } else OutlinedTextField(value, onValueChange,
        if (LocalThemePreferences.current.style == ThemeStyle.GLASS) modifier.styleSurface(shape) else modifier,
        enabled, readOnly, textStyle,
        label = label, placeholder = placeholder, trailingIcon = trailingIcon, singleLine = singleLine, minLines = minLines,
        maxLines = maxLines, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, shape = shape,
        colors = if (LocalThemePreferences.current.style == ThemeStyle.GLASS) OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
        ) else OutlinedTextFieldDefaults.colors())
}

@Composable
internal fun HubAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()) {
    val style = LocalThemePreferences.current.style
    // The dialog owns text scrolling in every theme. Callers must not add another
    // unbounded vertical scroller inside this slot (Compose rejects infinite height).
    val scrollableText: (@Composable () -> Unit)? = text?.let { content ->
        { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
    }
    if (style == ThemeStyle.M3) {
        AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, scrollableText, properties = properties); return
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.heightIn(max = 480.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            icon?.invoke()
            ProvideTextStyle(MaterialTheme.typography.titleLarge) { title?.invoke() }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { text?.invoke() }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dismissButton?.invoke(); confirmButton()
            }
        }
    }
    if (style == ThemeStyle.MIUIX) top.yukonga.miuix.kmp.window.WindowDialog(show = true,
        onDismissRequest = if (properties.dismissOnBackPress || properties.dismissOnClickOutside) onDismissRequest else null, modifier = modifier, content = body)
    else Dialog(onDismissRequest, properties = DialogProperties(dismissOnBackPress = properties.dismissOnBackPress, dismissOnClickOutside = properties.dismissOnClickOutside, usePlatformDefaultWidth = false)) {
        HubSurface(modifier.widthIn(max = 640.dp).fillMaxWidth(0.94f).heightIn(max = 480.dp), shape = RoundedCornerShape(28.dp), content = body)
    }
}
