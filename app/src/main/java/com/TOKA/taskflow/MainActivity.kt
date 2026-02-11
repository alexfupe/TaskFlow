package com.TOKA.taskflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.TOKA.taskflow.ui.theme.TaskFlowTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Incidencia(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val estado: MutableState<String>,
    val comentario: MutableState<String>,
    val fotos: MutableList<Uri> = mutableStateListOf()
)

data class UserProfile(
    val name: String,
    val email: String,
    val role: String,
    val avatarUrl: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskFlowTheme() {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TaskFlowApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TaskFlowApp(modifier: Modifier = Modifier) {
    var currentScreen by remember { mutableStateOf("login") }
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }
    var selectedIncidencia by remember { mutableStateOf<Incidencia?>(null) }

    val listaIncidencias = remember { mutableStateListOf<Incidencia>() }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    fun recargarDatos() {
        scope.launch {
            isLoading = true
            delay(1000)

            if (listaIncidencias.isEmpty()) {
                listaIncidencias.addAll(
                    listOf(
                        Incidencia(1, "Fuga de agua", "Tubería rota en cocina", mutableStateOf("PENDIENTE"), mutableStateOf("")),
                        Incidencia(2, "Luz pasillo", "Bombilla fundida planta 2", mutableStateOf("PROCESO"), mutableStateOf("Repuestos pedidos")),
                        Incidencia(3, "Aire Acondicionado", "Mantenimiento anual", mutableStateOf("HECHO"), mutableStateOf("Todo OK"))
                    )
                )
            }
            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (currentScreen) {
            "login" -> {
                LoginScreen(onLoginSuccess = { username ->
                    currentUser = UserProfile(name = username, email = "$username@empresa.com", role = "Técnico Senior")
                    currentScreen = "dashboard"
                    recargarDatos()
                })
            }
            "dashboard" -> {
                DashboardScreen(
                    user = currentUser!!,
                    isLoading = isLoading,
                    onNavigateToProfile = { currentScreen = "profile" },
                    onNavigateToIncidencias = { currentScreen = "incidencias_list" },
                    onLogout = {
                        currentUser = null
                        currentScreen = "login"
                        listaIncidencias.clear()
                    },
                    onRefresh = { recargarDatos() }
                )
            }
            "profile" -> {
                ProfileScreen(
                    user = currentUser!!,
                    onBack = { currentScreen = "dashboard" }
                )
            }
            "incidencias_list" -> {
                IncidenciasListScreen(
                    incidencias = listaIncidencias,
                    isLoading = isLoading,
                    onBack = { currentScreen = "dashboard" },
                    onRefresh = { recargarDatos() },
                    onIncidenciaClick = { incidencia ->
                        selectedIncidencia = incidencia
                        currentScreen = "detalle"
                    }
                )
            }
            "detalle" -> {
                if (selectedIncidencia != null) {
                    DetalleIncidenciaScreen(
                        incidencia = selectedIncidencia!!,
                        onSaveAndExit = { currentScreen = "incidencias_list" },
                        onBack = { currentScreen = "incidencias_list" }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("TaskFlow", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Gestión de Tareas", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            if (username.isNotEmpty() && password.isNotEmpty()) onLoginSuccess(username)
            else Toast.makeText(context, "Rellena los campos", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Entrar")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    user: UserProfile,
    isLoading: Boolean,
    onNavigateToProfile: () -> Unit,
    onNavigateToIncidencias: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TaskFlow Panel") },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recargar datos")
                        }
                    }
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Ver Perfil") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, onClick = { showMenu = false; onNavigateToProfile() })
                            DropdownMenuItem(text = { Text("Incidencias") }, leadingIcon = { Icon(Icons.Default.List, contentDescription = null) }, onClick = { showMenu = false; onNavigateToIncidencias() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Cerrar Sesión") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.Red) }, onClick = { showMenu = false; onLogout() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¡Bienvenido,", fontSize = 28.sp, color = Color.Gray)
            Text(user.name + "!", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(48.dp))
            Card(modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onNavigateToIncidencias() }, elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Gestionar Incidencias", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(user: UserProfile, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = user.name.take(1).uppercase(), fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Nombre", fontSize = 14.sp, color = Color.Gray)
            Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Correo Electrónico", fontSize = 14.sp, color = Color.Gray)
            Text(user.email, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Rol / Cargo", fontSize = 14.sp, color = Color.Gray)
            BadgeEstado(user.role)
        }
    }
}

@Composable
fun IncidenciasListScreen(incidencias: List<Incidencia>, isLoading: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onIncidenciaClick: (Incidencia) -> Unit) {
    val pendientes = incidencias.filter { it.estado.value == "PENDIENTE" }
    val enProceso = incidencias.filter { it.estado.value == "PROCESO" }
    val hechos = incidencias.filter { it.estado.value == "HECHO" }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mis Tareas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Recargar") }
        }

        if (incidencias.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay incidencias cargadas.", color = Color.Gray)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (pendientes.isNotEmpty()) {
                item { SectionHeader("Pendientes", Color(0xFFFF9800)) }
                items(pendientes) { incidencia -> TaskItem(incidencia, onIncidenciaClick) }
            }
            if (enProceso.isNotEmpty()) {
                item { SectionHeader("En Proceso", Color(0xFF2196F3)) }
                items(enProceso) { incidencia -> TaskItem(incidencia, onIncidenciaClick) }
            }
            if (hechos.isNotEmpty()) {
                item { SectionHeader("Hecho", Color(0xFF4CAF50)) }
                items(hechos) { incidencia -> TaskItem(incidencia, onIncidenciaClick) }
            }
        }
    }
}

@Composable
fun DetalleIncidenciaScreen(incidencia: Incidencia, onSaveAndExit: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val tempPhotoUri = remember { mutableStateOf<Uri?>(null) }
    var photoToView by remember { mutableStateOf<Uri?>(null) }
    var isEditingComentario by remember { mutableStateOf(false) }
    var comentarioTemporal by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoUri.value != null) {
            incidencia.fotos.add(tempPhotoUri.value!!)
            Toast.makeText(context, "Foto guardada localmente", Toast.LENGTH_SHORT).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val uri = crearArchivoImagen(context)
            tempPhotoUri.value = uri
            cameraLauncher.launch(uri)
        }
    }

    if (photoToView != null) FullScreenImageDialog(uri = photoToView!!, onDismiss = { photoToView = null })

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text("Detalle Tarea", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.weight(1f)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(incidencia.titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(incidencia.descripcion, fontSize = 16.sp, color = Color.Gray)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Estado:", fontWeight = FontWeight.Bold)
                DropdownEstado(incidencia.estado.value) { nuevoEstado -> incidencia.estado.value = nuevoEstado }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Observaciones:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            if (isEditingComentario) {
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(value = comentarioTemporal, onValueChange = { comentarioTemporal = it }, modifier = Modifier.weight(1f), placeholder = { Text("Escribe aquí...") })
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        incidencia.comentario.value = comentarioTemporal
                        isEditingComentario = false
                    }, modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(48.dp)) {
                        Icon(Icons.Default.Check, contentDescription = "Guardar", tint = Color.White)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = if (incidencia.comentario.value.isEmpty()) "Sin observaciones" else incidencia.comentario.value, color = if (incidencia.comentario.value.isEmpty()) Color.Gray else Color.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        comentarioTemporal = incidencia.comentario.value
                        isEditingComentario = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Evidencias Local (${incidencia.fotos.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(onClick = {
                    val permission = Manifest.permission.CAMERA
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        val uri = crearArchivoImagen(context)
                        tempPhotoUri.value = uri
                        cameraLauncher.launch(uri)
                    } else { permissionLauncher.launch(permission) }
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Foto")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(incidencia.fotos) { uri ->
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(modifier = Modifier.clickable { photoToView = uri }) { ImagenConRotacion(uri, thumbnail = true) }
                        IconButton(onClick = { incidencia.fotos.remove(uri) }, modifier = Modifier.offset(x = 4.dp, y = (-4).dp).background(Color.Red, CircleShape).size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar", tint = Color.White, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            onSaveAndExit()
        }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("GUARDAR Y SALIR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp).background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text(text = title, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun TaskItem(incidencia: Incidencia, onClick: (Incidencia) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onClick(incidencia) }, elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(incidencia.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(incidencia.descripcion, fontSize = 14.sp, maxLines = 1, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DropdownEstado(currentStatus: String, onStatusChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val buttonColor = when(currentStatus) { "PENDIENTE" -> Color(0xFFFF9800); "PROCESO" -> Color(0xFF2196F3); "HECHO" -> Color(0xFF4CAF50); else -> MaterialTheme.colorScheme.primary }
    Box {
        Button(onClick = { expanded = true }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor), modifier = Modifier.width(150.dp)) { Text(currentStatus, color = Color.White) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("PENDIENTE", "PROCESO", "HECHO").forEach { status -> DropdownMenuItem(text = { Text(status) }, onClick = { onStatusChange(status); expanded = false }) }
        }
    }
}

@Composable
fun BadgeEstado(estado: String) {
    val color = when(estado) { "PENDIENTE" -> Color(0xFFFF9800); "PROCESO" -> Color(0xFF2196F3); "HECHO" -> Color(0xFF4CAF50); else -> MaterialTheme.colorScheme.primary }
    Text(text = estado, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
fun ImagenConRotacion(uri: Uri, thumbnail: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        try {
            var input = context.contentResolver.openInputStream(uri)
            val exifInterface = input?.let { ExifInterface(it) }
            val orientation = exifInterface?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            input?.close()
            input = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options()
            options.inSampleSize = if (thumbnail) 4 else 1
            val originalBitmap = BitmapFactory.decodeStream(input, null, options)
            input?.close()
            if (originalBitmap != null && orientation != null) rotateBitmap(originalBitmap, orientation) else originalBitmap
        } catch (e: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = if (thumbnail) Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray) else Modifier.fillMaxWidth(), contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit)
    }
}

fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
fun FullScreenImageDialog(uri: Uri, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }, contentAlignment = Alignment.Center) { ImagenConRotacion(uri, thumbnail = false) }
    }
}

fun crearArchivoImagen(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "com.toka.taskflow.provider", file)
}