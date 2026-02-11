package com.toka.taskflow

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "incidencias")
data class IncidenciaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titulo: String,
    val descripcion: String,
    val estado: String,
    val comentario: String,
    val fotosUriJson: String
)

@Dao
interface IncidenciaDao {
    @Query("SELECT * FROM incidencias")
    suspend fun getAll(): List<IncidenciaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incidencia: IncidenciaEntity)

    @Update
    suspend fun update(incidencia: IncidenciaEntity)
}

@Database(entities = [IncidenciaEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incidenciaDao(): IncidenciaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taskflow_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

interface TaskFlowApi

data class IncidenciaUI(
    val id: Int = 0,
    val titulo: String,
    val descripcion: String,
    val estado: String,
    val comentario: String,
    val fotos: List<Uri> = emptyList()
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.incidenciaDao()

    private val _uiState = MutableStateFlow<List<IncidenciaUI>>(emptyList())
    val uiState: StateFlow<List<IncidenciaUI>> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        cargarDatos()
    }

    fun cargarDatos() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(800)
            val entidades = dao.getAll()
            if (entidades.isEmpty()) {
                insertarEjemplos()
            } else {
                _uiState.value = entidades.map { it.toUI() }
            }
            _isLoading.value = false
        }
    }

    private suspend fun insertarEjemplos() {
        val ejemplos = listOf(
            IncidenciaEntity(titulo = "Fuga de agua", descripcion = "Tubería rota en cocina", estado = "PENDIENTE", comentario = "", fotosUriJson = "[]"),
            IncidenciaEntity(titulo = "Luz pasillo", descripcion = "Bombilla fundida planta 2", estado = "PROCESO", comentario = "Repuestos pedidos", fotosUriJson = "[]"),
            IncidenciaEntity(titulo = "Aire Acondicionado", descripcion = "Mantenimiento anual", estado = "HECHO", comentario = "Todo OK", fotosUriJson = "[]")
        )
        ejemplos.forEach { dao.insert(it) }
        cargarDatos()
    }

    fun actualizarIncidencia(incidencia: IncidenciaUI) {
        viewModelScope.launch {
            dao.update(incidencia.toEntity())
            cargarDatos()
        }
    }

    private fun IncidenciaEntity.toUI(): IncidenciaUI {
        val type = object : TypeToken<List<String>>() {}.type
        val uriStrings: List<String> = Gson().fromJson(this.fotosUriJson, type) ?: emptyList()
        return IncidenciaUI(id, titulo, descripcion, estado, comentario, uriStrings.map { Uri.parse(it) })
    }

    private fun IncidenciaUI.toEntity(): IncidenciaEntity {
        val uriStrings = this.fotos.map { it.toString() }
        val json = Gson().toJson(uriStrings)
        return IncidenciaEntity(id, titulo, descripcion, estado, comentario, json)
    }
}

data class UserProfile(
    val name: String,
    val email: String,
    val role: String,
    val avatarUrl: String? = null
)

val DarkColorPalette = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color.White
)

@Composable
fun TaskFlowThemeDark(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorPalette, typography = Typography(), content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskFlowThemeDark {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: TaskViewModel = viewModel()
                    TaskFlowApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun TaskFlowApp(viewModel: TaskViewModel) {
    val navController = rememberNavController()
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(onLoginSuccess = { username ->
                currentUser = UserProfile(name = username, email = "$username@empresa.com", role = "Técnico Senior")
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
                viewModel.cargarDatos()
            })
        }

        composable("dashboard") {
            if (currentUser != null) {
                DashboardScreen(
                    user = currentUser!!,
                    viewModel = viewModel,
                    onNavigateToProfile = { navController.navigate("profile") },
                    onNavigateToIncidencias = { navController.navigate("incidencias_list") },
                    onLogout = {
                        currentUser = null
                        navController.navigate("login") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("profile") {
            if (currentUser != null) {
                ProfileScreen(user = currentUser!!, onBack = { navController.popBackStack() })
            }
        }

        composable("incidencias_list") {
            val incidencias by viewModel.uiState.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()

            IncidenciasListScreen(
                incidencias = incidencias,
                isLoading = isLoading,
                onBack = { navController.popBackStack() },
                onRefresh = { viewModel.cargarDatos() },
                onIncidenciaClick = { incidencia ->
                    val json = Uri.encode(Gson().toJson(incidencia))
                    navController.navigate("detalle/$json")
                }
            )
        }

        composable("detalle/{incidenciaJson}") { backStackEntry ->
            val json = backStackEntry.arguments?.getString("incidenciaJson")
            val incidencia = remember { Gson().fromJson(json, IncidenciaUI::class.java) }
            val incidenciaState = remember { mutableStateOf(incidencia) }

            DetalleIncidenciaScreen(
                incidencia = incidenciaState.value,
                onUpdate = { nuevaIncidencia -> incidenciaState.value = nuevaIncidencia },
                onSaveAndExit = {
                    viewModel.actualizarIncidencia(incidenciaState.value)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("TaskFlow", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Gestión de Tareas", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username, onValueChange = { username = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Gray)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
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
    viewModel: TaskViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToIncidencias: () -> Unit,
    onLogout: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TaskFlow Panel", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        IconButton(onClick = { viewModel.cargarDatos() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Menu, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onSurface) }
                        DropdownMenu(
                            expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(text = { Text("Ver Perfil", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; onNavigateToProfile() })
                            DropdownMenuItem(text = { Text("Incidencias", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; onNavigateToIncidencias() })
                            HorizontalDivider(color = Color.Gray)
                            DropdownMenuItem(text = { Text("Cerrar Sesión", color = Color.Red) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.Red) }, onClick = { showMenu = false; onLogout() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¡Bienvenido,", fontSize = 28.sp, color = Color.Gray)
            Text(user.name + "!", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(48.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onNavigateToIncidencias() },
                elevation = CardDefaults.cardElevation(6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Gestionar Incidencias", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(user: UserProfile, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onBackground) }
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = user.name.take(1).uppercase(), fontSize = 48.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Nombre", fontSize = 14.sp, color = Color.Gray)
            Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Correo Electrónico", fontSize = 14.sp, color = Color.Gray)
            Text(user.email, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Rol / Cargo", fontSize = 14.sp, color = Color.Gray)
            BadgeEstado(user.role)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidenciasListScreen(
    incidencias: List<IncidenciaUI>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onIncidenciaClick: (IncidenciaUI) -> Unit
) {
    var filtroSeleccionado by remember { mutableStateOf("TODOS") }
    val incidenciasFiltradas = remember(incidencias, filtroSeleccionado) {
        if (filtroSeleccionado == "TODOS") incidencias
        else incidencias.filter { it.estado == filtroSeleccionado }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Tareas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface) } },
                actions = {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                    else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = MaterialTheme.colorScheme.onSurface) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipItem("Todos", filtroSeleccionado == "TODOS") { filtroSeleccionado = "TODOS" }
                FilterChipItem("Pendientes", filtroSeleccionado == "PENDIENTE") { filtroSeleccionado = "PENDIENTE" }
                FilterChipItem("En Proceso", filtroSeleccionado == "PROCESO") { filtroSeleccionado = "PROCESO" }
                FilterChipItem("Hecho", filtroSeleccionado == "HECHO") { filtroSeleccionado = "HECHO" }
            }

            if (incidencias.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay incidencias.", color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(incidenciasFiltradas) { incidencia -> TaskItem(incidencia, onIncidenciaClick) }
                    item { Spacer(modifier = Modifier.height(30.dp)) }
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) Color.Black else Color.Gray
    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(backgroundColor).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun DetalleIncidenciaScreen(
    incidencia: IncidenciaUI,
    onUpdate: (IncidenciaUI) -> Unit,
    onSaveAndExit: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tempPhotoUri = remember { mutableStateOf<Uri?>(null) }
    var photoToView by remember { mutableStateOf<Uri?>(null) }
    var isEditingComentario by remember { mutableStateOf(false) }
    var comentarioTemporal by remember { mutableStateOf("") }
    val fotosState = remember { mutableStateListOf<Uri>().apply { addAll(incidencia.fotos) } }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoUri.value != null) {
            fotosState.add(tempPhotoUri.value!!)
            onUpdate(incidencia.copy(fotos = fotosState.toList()))
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onBackground) }
            Text("Detalle Tarea", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }

        Column(modifier = Modifier.weight(1f)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(incidencia.titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(incidencia.descripcion, fontSize = 16.sp, color = Color.Gray)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Estado:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                DropdownEstado(incidencia.estado) { nuevoEstado ->
                    onUpdate(incidencia.copy(estado = nuevoEstado))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Observaciones:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(4.dp))

            if (isEditingComentario) {
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = comentarioTemporal, onValueChange = { comentarioTemporal = it }, modifier = Modifier.weight(1f), placeholder = { Text("Escribe aquí...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        onUpdate(incidencia.copy(comentario = comentarioTemporal))
                        isEditingComentario = false
                    }, modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(48.dp)) {
                        Icon(Icons.Default.Check, contentDescription = "Guardar", tint = Color.Black)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = if (incidencia.comentario.isEmpty()) "Sin observaciones" else incidencia.comentario, color = if (incidencia.comentario.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        comentarioTemporal = incidencia.comentario
                        isEditingComentario = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Evidencias Local (${fotosState.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
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
                items(fotosState) { uri ->
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(modifier = Modifier.clickable { photoToView = uri }) { ImagenConRotacion(uri, thumbnail = true) }
                        IconButton(onClick = {
                            fotosState.remove(uri)
                            onUpdate(incidencia.copy(fotos = fotosState.toList()))
                        }, modifier = Modifier.offset(x = 4.dp, y = (-4).dp).background(Color.Red, CircleShape).size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar", tint = Color.White, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onSaveAndExit() }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("GUARDAR Y SALIR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TaskItem(incidencia: IncidenciaUI, onClick: (IncidenciaUI) -> Unit) {
    val statusColor = when(incidencia.estado) {
        "PENDIENTE" -> Color(0xFFFF9800)
        "PROCESO" -> Color(0xFF2196F3)
        "HECHO" -> Color(0xFF4CAF50)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onClick(incidencia) },
        elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(6.dp).background(statusColor))
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Text(incidencia.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(incidencia.descripcion, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 16.dp))
        }
    }
}

@Composable
fun DropdownEstado(currentStatus: String, onStatusChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val buttonColor = when(currentStatus) { "PENDIENTE" -> Color(0xFFFF9800); "PROCESO" -> Color(0xFF2196F3); "HECHO" -> Color(0xFF4CAF50); else -> MaterialTheme.colorScheme.primary }
    Box {
        Button(onClick = { expanded = true }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor), modifier = Modifier.width(150.dp)) { Text(currentStatus, color = Color.Black) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            listOf("PENDIENTE", "PROCESO", "HECHO").forEach { status -> DropdownMenuItem(text = { Text(status, color = MaterialTheme.colorScheme.onSurface) }, onClick = { onStatusChange(status); expanded = false }) }
        }
    }
}

@Composable
fun BadgeEstado(estado: String) {
    val color = when(estado) { "PENDIENTE" -> Color(0xFFFF9800); "PROCESO" -> Color(0xFF2196F3); "HECHO" -> Color(0xFF4CAF50); else -> MaterialTheme.colorScheme.primary }
    Text(text = estado, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
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
        } catch (_: Exception) { null }
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
    Dialog(onDismissRequest = onDismiss) { Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }, contentAlignment = Alignment.Center) { ImagenConRotacion(uri, thumbnail = false) } }
}

fun crearArchivoImagen(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "com.toka.taskflow.provider", file)
}