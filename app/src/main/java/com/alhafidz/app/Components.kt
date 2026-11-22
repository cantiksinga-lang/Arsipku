@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.alhafidz.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun Donut(
    progress: Float, color: Color, modifier: Modifier = Modifier.size(84.dp), stroke: Dp = 9.dp,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val track = LocalPal.current.track
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val s = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, tl, s, style = Stroke(sw))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, s, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
fun RingCard(modifier: Modifier, title: String, big: String, unit: String, sub: String, progress: Float, lv: Lv) {
    val pal = LocalPal.current
    AppCard(modifier) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Donut(progress, lv.color(pal), Modifier.size(104.dp), 11.dp) {
                Text(big, fontWeight = FontWeight.Bold, fontSize = if (big.length > 5) 16.sp else 21.sp)
                Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val el = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val shape = RoundedCornerShape(22.dp)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = el, content = content)
    else Card(modifier = modifier, shape = shape, colors = colors, elevation = el, content = content)
}

@Composable
fun Field(
    v: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false, single: Boolean = true, caps: Boolean = false, decimal: Boolean = false
) {
    OutlinedTextField(
        value = v, onValueChange = onChange, label = { Text(label) }, singleLine = single, modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else if (decimal) KeyboardType.Decimal else KeyboardType.Text,
            capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences
        )
    )
}

@Composable
fun DateField(label: String, ms: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var show by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = if (ms > 0) fmtDate(ms) else "", onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.Event, null) }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        Box(Modifier.matchParentSize().clickable { show = true })
    }
    if (show) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = if (ms > 0) ms.toLocal().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else null
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().ms()) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Batal") } }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun Choice(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, l) ->
            FilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(l) })
        }
    }
}

@Composable
fun Toggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { i, o ->
                val sel = i == selected
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp))
                        .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(o, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun rememberBitmap(name: String?, max: Int = 900): ImageBitmap? {
    val st = produceState<ImageBitmap?>(null, name, max) {
        value = if (name == null) null else withContext(Dispatchers.IO) { Img.load(name, max)?.asImageBitmap() }
    }
    return st.value
}

class Capture(val gallery: () -> Unit, val camera: () -> Unit, val file: () -> Unit)

@Composable
fun rememberCapture(onUri: (Uri) -> Unit): Capture {
    val ctx = LocalContext.current
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val gal = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onUri) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let(onUri) }
    val doc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onUri) }
    return Capture(
        gallery = { gal.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        camera = {
            val f = File(ctx.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camUri = u
            cam.launch(u)
        },
        file = { doc.launch(arrayOf("image/*", "application/pdf")) }
    )
}

@Composable
fun ScanBar(cap: Capture, busy: Boolean, withFile: Boolean, label: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.SemiBold)
                if (busy) { Spacer(Modifier.width(10.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = cap.camera, enabled = !busy) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Kamera") }
                FilledTonalButton(onClick = cap.gallery, enabled = !busy) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galeri") }
                if (withFile) FilledTonalButton(onClick = cap.file, enabled = !busy) { Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("File") }
            }
        }
    }
}

@Composable
fun KmDialog(v: Vehicle, onClose: () -> Unit) {
    var t by remember { mutableStateOf(v.odo.toString()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Update KM — ${v.name}") },
        text = { Field(t, { t = it.filter(Char::isDigit).take(7) }, "KM saat ini", number = true) },
        confirmButton = { TextButton(onClick = { t.toIntOrNull()?.let { Repo.updateOdo(v.id, it) }; onClose() }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text("Ya, hapus", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Batal") } }
    )
}

@Composable
fun SectionLabel(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
}
