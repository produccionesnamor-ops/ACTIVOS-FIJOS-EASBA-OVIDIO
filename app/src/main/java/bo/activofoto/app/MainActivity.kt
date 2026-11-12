package bo.activofoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

private val Navy = Color(0xFF214B78)
private val Amber = Color(0xFFFFAA36)
private val CanvasColor = Color(0xFFF4F7FB)
private val Subtle = Color(0xFF65758A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Navy,
                secondary = Amber,
                background = CanvasColor,
                surface = Color.White
            )) {
                ActivoFotoApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivoFotoApp() {
    val context = LocalContext.current
    val store = remember { InventoryStore(context.applicationContext) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var page by rememberSaveable { mutableStateOf("inicio") }
    var selectedCode by rememberSaveable { mutableStateOf("") }
    var inputCode by rememberSaveable { mutableStateOf("") }
    var inputDescription by rememberSaveable { mutableStateOf("") }
    var assets by remember { mutableStateOf(emptyList<AssetSummary>()) }
    var photos by remember { mutableStateOf(emptyList<PhotoRecord>()) }
    var busy by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<PhotoRecord?>(null) }

    // Se conserva la captura pendiente incluso si Android recrea la actividad.
    var pendingPath by rememberSaveable { mutableStateOf("") }
    var pendingCode by rememberSaveable { mutableStateOf("") }
    var pendingDescription by rememberSaveable { mutableStateOf("") }

    suspend fun refresh() {
        val updated = withContext(Dispatchers.IO) { store.getAssets() }
        assets = updated
        photos = if (selectedCode.isNotBlank()) {
            withContext(Dispatchers.IO) { store.getPhotos(selectedCode) }
        } else emptyList()
    }

    fun reportError(error: Throwable) {
        scope.launch { snackbar.showSnackbar(error.message ?: "Ocurrió un error") }
    }

    LaunchedEffect(page, selectedCode) { refresh() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val source = File(pendingPath)
        val photographedCode = pendingCode
        val description = pendingDescription
        pendingPath = ""
        if (success && source.isFile && photographedCode.isNotBlank()) {
            busy = true
            scope.launch {
                var generated: File? = null
                try {
                    val takenAt = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        generated = stampPhoto(context, source, photographedCode, takenAt)
                        try {
                            store.registerPhoto(photographedCode, description, generated!!, takenAt)
                        } catch (error: Throwable) {
                            generated?.delete()
                            throw error
                        }
                    }
                    selectedCode = photographedCode
                    inputCode = photographedCode
                    page = "detalle"
                    refresh()
                    snackbar.showSnackbar("Foto identificada y guardada correctamente")
                } catch (error: Throwable) {
                    reportError(error)
                } finally {
                    source.delete()
                    busy = false
                }
            }
        } else {
            if (source.isFile) source.delete()
            scope.launch { snackbar.showSnackbar("Captura cancelada; no se guardó ninguna foto") }
        }
    }

    fun startCapture() {
        val code = inputCode.trim().uppercase(Locale.ROOT)
        if (code.isEmpty()) {
            scope.launch { snackbar.showSnackbar("Introduce el código del activo antes de tomar la foto") }
            return
        }
        try {
            val directory = File(context.cacheDir, "capturas").apply { mkdirs() }
            val file = File(directory, "captura_${UUID.randomUUID()}.jpg")
            if (!file.createNewFile()) error("No se pudo crear el archivo temporal")
            pendingPath = file.absolutePath
            pendingCode = code
            pendingDescription = inputDescription
            cameraLauncher.launch(shareUri(context, file))
        } catch (error: Throwable) {
            if (pendingPath.isNotBlank()) File(pendingPath).delete()
            pendingPath = ""
            reportError(error)
        }
    }

    fun exportAll() {
        busy = true
        scope.launch {
            try {
                val zip = withContext(Dispatchers.IO) { exportInventory(context, store) }
                shareFile(context, zip, "application/zip")
            } catch (error: Throwable) {
                reportError(error)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = CanvasColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(when (page) {
                        "captura" -> "Nueva fotografía"
                        "detalle" -> "Activo $selectedCode"
                        else -> "ActivoFoto"
                    }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (page != "inicio") TextButton(onClick = {
                        page = if (page == "detalle") "inicio" else if (selectedCode.isNotBlank()) "detalle" else "inicio"
                    }) { Text("‹ Volver") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "captura" -> CaptureScreen(
                    code = inputCode,
                    description = inputDescription,
                    busy = busy,
                    onCodeChange = { inputCode = it.take(60).uppercase(Locale.ROOT) },
                    onDescriptionChange = { inputDescription = it.take(150) },
                    onCapture = ::startCapture
                )
                "detalle" -> DetailScreen(
                    code = selectedCode,
                    description = assets.firstOrNull { it.code == selectedCode }?.description.orEmpty(),
                    photos = photos,
                    busy = busy,
                    onAdd = {
                        inputCode = selectedCode
                        inputDescription = assets.firstOrNull { it.code == selectedCode }?.description.orEmpty()
                        page = "captura"
                    },
                    onOpen = { item ->
                        try { openPhoto(context, photoFile(context, item.fileName)) }
                        catch (error: Throwable) { reportError(error) }
                    },
                    onShare = { item ->
                        try { shareFile(context, photoFile(context, item.fileName), "image/jpeg") }
                        catch (error: Throwable) { reportError(error) }
                    },
                    onDelete = { deleteCandidate = it }
                )
                else -> HomeScreen(
                    assets = assets,
                    busy = busy,
                    onNew = {
                        selectedCode = ""
                        inputCode = ""
                        inputDescription = ""
                        page = "captura"
                    },
                    onSelect = { selectedCode = it; page = "detalle" },
                    onExport = ::exportAll
                )
            }
        }
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminar fotografía") },
            text = { Text("Esta acción borrará la fotografía del celular. ¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                store.deletePhoto(item.id)
                                photoFile(context, item.fileName).delete()
                            }
                            refresh()
                        } catch (error: Throwable) { reportError(error) }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeScreen(
    assets: List<AssetSummary>, busy: Boolean,
    onNew: () -> Unit, onSelect: (String) -> Unit, onExport: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = assets.filter {
        it.code.contains(search, ignoreCase = true) || it.description.contains(search, ignoreCase = true)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("INVENTARIO FOTOGRÁFICO", color = Color(0xFFC6D9EE), style = MaterialTheme.typography.labelMedium)
                    Text("Tus activos, siempre identificados.", color = Color.White,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${assets.size} activos  •  ${assets.sumOf { it.photoCount }} fotografías",
                        color = Color.White)
                    Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy)) {
                        Text("＋ Registrar fotografía", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Buscar activo por código o descripción") },
                leadingIcon = { Text("⌕") }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("ACTIVOS REGISTRADOS", style = MaterialTheme.typography.labelMedium,
                    color = Subtle, fontWeight = FontWeight.Bold)
                TextButton(onClick = onExport, enabled = !busy && assets.any { it.photoCount > 0 }) {
                    Text("Exportar ZIP")
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📷", style = MaterialTheme.typography.headlineLarge)
                        Text(if (assets.isEmpty()) "Aún no hay fotografías" else "No se encontraron activos",
                            fontWeight = FontWeight.Bold)
                        Text("Ingresa un código y toma tu primera fotografía. La información se guarda en el celular.", color = Subtle)
                    }
                }
            }
        }
        items(filtered, key = { it.code }) { asset ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(asset.code) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = Color(0xFFEAF1FA), shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Text("📁", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(asset.code, fontWeight = FontWeight.Bold, color = Navy)
                        Text(asset.description.ifBlank { "Sin descripción" }, color = Subtle,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${asset.photoCount} fotografía(s)", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = Navy)
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Funciona sin internet. Para respaldo en la nube, exporta el ZIP y compártelo con tu servicio preferido cuando estés conectado.",
                style = MaterialTheme.typography.bodySmall, color = Subtle)
        }
    }
}

@Composable
private fun CaptureScreen(
    code: String, description: String, busy: Boolean,
    onCodeChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit, onCapture: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = Color(0xFFEAF1FA), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("1. IDENTIFICA EL ACTIVO", color = Navy, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("El código se imprimirá en cada foto y también formará parte de su nombre de archivo.", color = Subtle)
            }
        }
        OutlinedTextField(
            value = code, onValueChange = onCodeChange, modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Código del activo *") },
            placeholder = { Text("Ej. AF-00125") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            supportingText = { Text("Obligatorio • Puedes cambiarlo antes de cada captura") }
        )
        OutlinedTextField(
            value = description, onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
            label = { Text("Descripción (opcional)") },
            placeholder = { Text("Ej. Monitor Dell – Oficina Administración") }
        )
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. TOMA LA FOTOGRAFÍA", color = Navy, fontWeight = FontWeight.Bold)
                Text("La cámara guardará una foto de buena resolución. ActivoFoto añadirá una banda con el código y la fecha.", color = Subtle)
                Text("Después podrás tomar más fotos del mismo activo, sin volver a escribir el código.", color = Subtle)
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = onCapture, enabled = code.isNotBlank() && !busy,
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (busy) "Guardando…" else "📷  Abrir cámara y tomar foto", fontWeight = FontWeight.Bold) }
        Text("Las fotos se conservan de forma privada dentro de la aplicación hasta que las compartas o exportes.",
            style = MaterialTheme.typography.bodySmall, color = Subtle)
    }
}

@Composable
private fun DetailScreen(
    code: String, description: String, photos: List<PhotoRecord>, busy: Boolean,
    onAdd: () -> Unit, onOpen: (PhotoRecord) -> Unit,
    onShare: (PhotoRecord) -> Unit, onDelete: (PhotoRecord) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Navy),
                shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CÓDIGO DE ACTIVO FIJO", color = Color(0xFFCADFF4),
                        style = MaterialTheme.typography.labelSmall)
                    Text(code, color = Amber, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium)
                    if (description.isNotBlank()) Text(description, color = Color.White)
                    Text("${photos.size} fotografía(s) registrada(s)", color = Color.White)
                    Button(onClick = onAdd, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy)) {
                        Text("＋ Tomar otra foto", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Text("GALERÍA DEL ACTIVO", color = Subtle, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium)
        }
        if (photos.isEmpty()) item { Text("Todavía no hay fotografías para este código.", color = Subtle) }
        items(photos, key = { it.id }) { photo ->
            PhotoCard(photo, onOpen, onShare, onDelete)
        }
    }
}

@Composable
private fun PhotoCard(
    photo: PhotoRecord,
    onOpen: (PhotoRecord) -> Unit,
    onShare: (PhotoRecord) -> Unit,
    onDelete: (PhotoRecord) -> Unit
) {
    val context = LocalContext.current
    val file = remember(photo.fileName) { photoFile(context, photo.fileName) }
    var preview by remember(photo.fileName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(photo.fileName) {
        preview = withContext(Dispatchers.IO) { loadThumbnail(file)?.asImageBitmap() }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (preview != null) {
                Image(bitmap = preview!!, contentDescription = "Fotografía de ${photo.assetCode}",
                    modifier = Modifier.fillMaxWidth().height(195.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(photo) }, contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Cargando foto…", color = Subtle)
                }
            }
            Text(formattedDate(photo.capturedAt), fontWeight = FontWeight.SemiBold)
            Text(photo.fileName, color = Subtle, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = Color(0xFFF0F1F3))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onOpen(photo) }) { Text("Ver") }
                OutlinedButton(onClick = { onShare(photo) }) { Text("Compartir") }
                TextButton(onClick = { onDelete(photo) }) { Text("Eliminar", color = Color(0xFFAC3441)) }
            }
        }
    }
}
