package com.doolan.forge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ForgeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = CardDark)
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, shape = shape) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = shape) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun Tag(text: String, color: Color = Accent) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A row of single-select chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipChoice(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}

/** A row of multi-select chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipMulti(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) })
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    singleLine: Boolean = true,
    suffix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            if (number) {
                val cleaned = v.replace(',', '.')
                if (cleaned.all { it.isDigit() || it == '.' }) onChange(cleaned)
            } else onChange(v)
        },
        label = { Text(label) },
        singleLine = singleLine,
        suffix = if (suffix != null) ({ Text(suffix) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Yes",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Accent) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CardHigh)
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/** Horizontal bar list: label, bar, value text. */
@Composable
fun BarList(items: List<Triple<String, Float, String>>) {
    val max = (items.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(0.0001f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.width(92.dp), fontSize = 13.sp)
                ProgressBar(value / max, Modifier.weight(1f))
                Text(text, modifier = Modifier.width(56.dp).padding(start = 8.dp), fontSize = 13.sp, color = Muted)
            }
        }
    }
}

/** Simple line chart. [values] are plotted left to right; [firstLabel]/[lastLabel] go under the ends. */
@Composable
fun LineChart(
    values: List<Double>,
    firstLabel: String,
    lastLabel: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val lo = values.min()
    val hi = values.max()
    val pad = ((hi - lo) * 0.15).coerceAtLeast(0.5)
    val minY = lo - pad
    val maxY = hi + pad
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${trim(maxY - pad)} $unit", color = Muted, fontSize = 11.sp)
            Text("low ${trim(minY + pad)} $unit", color = Muted, fontSize = 11.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = if (values.size == 1) w / 2f else w * i / (values.size - 1).toFloat()
            fun y(v: Double) = (h - ((v - minY) / (maxY - minY)).toFloat() * h)
            val path = Path()
            values.forEachIndexed { i, v ->
                if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
            }
            if (values.size > 1) {
                drawPath(path, Accent, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
            values.forEachIndexed { i, v ->
                drawCircle(Accent, radius = 7f, center = Offset(x(i), y(v)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(firstLabel, color = Muted, fontSize = 11.sp)
            Text(lastLabel, color = Muted, fontSize = 11.sp)
        }
    }
}

private fun trim(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else String.format(java.util.Locale.US, "%.1f", d)

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

@Composable
fun ClickableRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) { content() }
}

@Composable
fun BodyText(text: String, muted: Boolean = false) {
    Text(
        text,
        fontSize = 14.sp,
        color = if (muted) Muted else MaterialTheme.colorScheme.onSurface
    )
}

/** Small centred number box used for weight / reps / RIR while logging sets. */
@Composable
fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardHigh),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text(hint, color = Muted, fontSize = 14.sp)
                inner()
            }
        }
    )
}
