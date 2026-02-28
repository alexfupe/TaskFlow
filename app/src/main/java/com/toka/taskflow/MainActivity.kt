package com.toka.taskflow

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.toka.taskflow.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 1. MODELOS DE DATOS Y RETROFIT
// ==========================================

data class LoginRequest(@SerializedName("Username") val username: String, @SerializedName("Password") val password: String)
data class LoginResponse(@SerializedName("token") val token: String, @SerializedName("user") val user: UserProfile)
data class UserProfile(@SerializedName("Username") val name: String, @SerializedName("Email") val email: String, @SerializedName("Role") val role: String)
data class ChangePasswordRequest(@SerializedName("newPassword") val newPassword: String)

data class IncidenciaApi(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("status") val status: String,
    @SerializedName("comentario") val comentario: String? = "",
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("finishedAt") val finishedAt: String? = null
)

data class IncidenciaUI(
    val id: String = "",
    val titulo: String,
    val descripcion: String,
    val estado: String,
    val comentario: String,
    val fotos: List<Uri> = emptyList(),
    val createdAt: String = "",
    val finishedAt: String = ""
)

fun IncidenciaApi.toUI(): IncidenciaUI = IncidenciaUI(id = this.id ?: "", titulo = this.title, descripcion = this.description, estado = this.status.uppercase(), comentario = this.comentario ?: "", createdAt = this.createdAt ?: "", finishedAt = this.finishedAt ?: "")
fun IncidenciaUI.toApi(): IncidenciaApi = IncidenciaApi(id = if (this.id.isNullOrEmpty()) null else this.id, title = this.titulo, description = this.descripcion, status = this.estado.lowercase(), comentario = this.comentario)

interface ApiService {
    @POST("users/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("users/logout") suspend fun logout(@Header("Authorization") token: String)
    @PUT("users/change-password") suspend fun changePassword(@Header("Authorization") token: String, @Body request: ChangePasswordRequest)

    @GET("tasks/my-tasks") suspend fun getIncidencias(@Header("Authorization") token: String): List<IncidenciaApi>
    @PUT("tasks/{id}") suspend fun updateIncidencia(@Header("Authorization") token: String, @Path("id") id: String, @Body incidencia: IncidenciaApi)
    @POST("tasks/") suspend fun createIncidencia(@Header("Authorization") token: String, @Body incidencia: IncidenciaApi): Map<String, Any>
    @DELETE("tasks/{id}") suspend fun deleteIncidencia(@Header("Authorization") token: String, @Path("id") id: String)
}

object RetrofitClient {
    private const val BASE_URL = "https://scleroblastic-jabberingly-hipolito.ngrok-free.dev/"
    val apiService: ApiService by lazy { Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java) }
}

// ==========================================
// 2. VIEWMODEL
// ==========================================

class TaskViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<List<IncidenciaUI>>(emptyList())
    val uiState: StateFlow<List<IncidenciaUI>> = _uiState.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()
    private var authToken: String? = null

    fun login(u: String, p: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.login(LoginRequest(u, p))
                authToken = "Bearer ${response.token}"; _currentUser.value = response.user
                onSuccess()
                cargarDatos()
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Error: Usuario o contraseña incorrectos")
            } finally { _isLoading.value = false }
        }
    }

    fun cargarDatos() {
        val token = authToken ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try { _uiState.value = RetrofitClient.apiService.getIncidencias(token).map { it.toUI() } }
            catch (e: Exception) { e.printStackTrace() } finally { _isLoading.value = false }
        }
    }

    fun crearTarea(titulo: String, desc: String, comentario: String, onComplete: () -> Unit) {
        val token = authToken ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nueva = IncidenciaApi(title = titulo, description = desc, status = "pendiente", comentario = comentario)
                RetrofitClient.apiService.createIncidencia(token, nueva)
                cargarDatos()
                onComplete()
            } catch (e: Exception) { e.printStackTrace() }
            finally { _isLoading.value = false }
        }
    }

    fun actualizarIncidencia(incidencia: IncidenciaUI) {
        val token = authToken ?: return
        viewModelScope.launch {
            try {
                _isLoading.value = true
                RetrofitClient.apiService.updateIncidencia(token, incidencia.id, incidencia.toApi())
                cargarDatos()
            }
            catch (e: Exception) { e.printStackTrace() } finally { _isLoading.value = false }
        }
    }

    fun borrarTarea(id: String, onComplete: (Boolean, String) -> Unit) {
        val token = authToken ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.deleteIncidencia(token, id)
                _uiState.value = _uiState.value.filter { it.id != id }
                onComplete(true, "Tarea eliminada")
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, "Error al eliminar la tarea")
                cargarDatos()
            }
        }
    }

    fun cambiarPassword(nuevaPassword: String, onComplete: (Boolean, String) -> Unit) {
        val token = authToken ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                RetrofitClient.apiService.changePassword(token, ChangePasswordRequest(nuevaPassword))
                onComplete(true, "Contraseña actualizada")
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, "Error al cambiar la contraseña")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        val token = authToken
        viewModelScope.launch {
            if (token != null) {
                try { RetrofitClient.apiService.logout(token) } catch (e: Exception) { e.printStackTrace() }
            }
            authToken = null; _currentUser.value = null; _uiState.value = emptyList()
        }
    }
}

// ==========================================
// 3. MAIN ACTIVITY
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskFlowTheme {
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
    val currentUser by viewModel.currentUser.collectAsState()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(viewModel, onLoginSuccess = { navController.navigate("dashboard") { popUpTo("login") { inclusive = true } } }) }
        composable("dashboard") { currentUser?.let { user -> DashboardScreen(user, viewModel, { navController.navigate("profile") }, { navController.navigate("incidencias_list") }, { navController.navigate("nueva_tarea") }, { viewModel.logout(); navController.navigate("login") { popUpTo("dashboard") { inclusive = true } } }) } }
        composable("nueva_tarea") { NuevaTareaScreen(viewModel, onBack = { navController.popBackStack() }) }
        composable("profile") { currentUser?.let { user -> ProfileScreen(user, viewModel, onBack = { navController.popBackStack() }) } }
        composable("incidencias_list") {
            val incidencias by viewModel.uiState.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()

            IncidenciasListScreen(incidencias, isLoading, viewModel, { navController.popBackStack() }, { viewModel.cargarDatos() }, { incidencia ->
                val json = Uri.encode(Gson().toJson(incidencia))
                navController.navigate("detalle/$json")
            })
        }
        composable("detalle/{incidenciaJson}") { backStackEntry ->
            val json = backStackEntry.arguments?.getString("incidenciaJson")
            val incidencia = remember { Gson().fromJson(json, IncidenciaUI::class.java) }
            val incidenciaState = remember { mutableStateOf(incidencia) }
            DetalleIncidenciaScreen(incidenciaState.value, { incidenciaState.value = it }, { viewModel.actualizarIncidencia(incidenciaState.value); navController.popBackStack() }, { navController.popBackStack() })
        }
    }
}

// ==========================================
// 4. PANTALLAS
// ==========================================

@Composable
fun LoginScreen(viewModel: TaskViewModel, onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TaskFlow", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("Gestión de Tareas", fontSize = 16.sp, color = TextoGris)
            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Contraseña") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) CircularProgressIndicator()
            else {
                Button(
                    onClick = { if (username.isNotEmpty() && password.isNotEmpty()) viewModel.login(username, password, onLoginSuccess) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } else Toast.makeText(context, "Rellena los campos", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NegroFondo)
                ) { Text("ENTRAR", color = NegroFondo, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(user: UserProfile, viewModel: TaskViewModel, onProfile: () -> Unit, onList: () -> Unit, onAdd: () -> Unit, onLogout: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TaskFlow Panel", fontWeight = FontWeight.Bold) },
                actions = {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Menu, null) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Ver Perfil") }, onClick = { showMenu = false; onProfile() })
                            DropdownMenuItem(text = { Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onLogout() })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¡Bienvenido!", fontSize = 24.sp, color = TextoGris)
            Text(user.name, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(56.dp))

            DashboardCard("Mis Incidencias", Icons.AutoMirrored.Filled.List, onList)
            Spacer(modifier = Modifier.height(16.dp))
            DashboardCard("Añadir Nueva", Icons.Default.Add, onAdd)
        }
    }
}

@Composable
fun DashboardCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().height(110.dp).clickable { onClick() }, elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevaTareaScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    var titulo by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var comentario by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nueva Tarea", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(24.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = comentario, onValueChange = { comentario = it }, label = { Text("Comentarios / Observaciones") }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(32.dp))

            if (isLoading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                Button(
                    onClick = { if (titulo.isNotEmpty()) viewModel.crearTarea(titulo, desc, comentario, onBack) },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NegroFondo)
                ) {
                    Icon(Icons.Default.Check, null, tint = NegroFondo)
                    Spacer(Modifier.width(8.dp))
                    Text("GUARDAR CAMBIOS", color = NegroFondo, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(user: UserProfile, viewModel: TaskViewModel, onBack: () -> Unit) {
    var nuevaPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mi Perfil", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Text(text = user.name.take(1).uppercase(), fontSize = 48.sp, color = NegroFondo, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Nombre", fontSize = 14.sp, color = TextoGris)
            Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Correo Electrónico", fontSize = 14.sp, color = TextoGris)
            Text(user.email, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Rol / Cargo", fontSize = 14.sp, color = TextoGris)
            BadgeEstado(user.role)
            Spacer(modifier = Modifier.height(48.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Seguridad", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nuevaPassword, onValueChange = { nuevaPassword = it }, label = { Text("Nueva Contraseña") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) CircularProgressIndicator()
            else {
                Button(
                    onClick = {
                        if (nuevaPassword.isNotEmpty()) { viewModel.cambiarPassword(nuevaPassword) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if(success) nuevaPassword = "" } }
                        else Toast.makeText(context, "Escribe una contraseña", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NegroFondo)
                ) { Text("ACTUALIZAR CONTRASEÑA", color = NegroFondo, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidenciasListScreen(incidencias: List<IncidenciaUI>, isLoading: Boolean, viewModel: TaskViewModel, onBack: () -> Unit, onRefresh: () -> Unit, onIncidenciaClick: (IncidenciaUI) -> Unit) {
    var filtroSeleccionado by remember { mutableStateOf("TODOS") }
    var searchQuery by remember { mutableStateOf("") }

    val filtradas = incidencias.filter {
        (filtroSeleccionado == "TODOS" || it.estado == filtroSeleccionado) &&
                (it.titulo.contains(searchQuery, ignoreCase = true) || it.descripcion.contains(searchQuery, ignoreCase = true))
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Tareas", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar tareas...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipItem("Todos", filtroSeleccionado == "TODOS") { filtroSeleccionado = "TODOS" }
                FilterChipItem("Pendientes", filtroSeleccionado == "PENDIENTE") { filtroSeleccionado = "PENDIENTE" }
                FilterChipItem("En Proceso", filtroSeleccionado == "PROCESO") { filtroSeleccionado = "PROCESO" }
                FilterChipItem("Hecho", filtroSeleccionado == "HECHO") { filtroSeleccionado = "HECHO" }
            }

            if (isLoading && filtradas.isEmpty() && searchQuery.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else if (filtradas.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No se encontraron tareas", color = TextoGris) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtradas, key = { it.id }) { incidencia ->
                        val scope = rememberCoroutineScope()
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                    scope.launch { viewModel.borrarTarea(incidencia.id) { _, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } }
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState, enableDismissFromEndToStart = false,
                            backgroundContent = {
                                val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.error else Color.Transparent
                                Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(4.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.onError) }
                            }
                        ) { TaskItem(incidencia, onIncidenciaClick) }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItem(incidencia: IncidenciaUI, onClick: (IncidenciaUI) -> Unit) {
    val statusColor = when(incidencia.estado) { "PENDIENTE" -> StatusPendiente; "PROCESO" -> StatusProceso; "HECHO" -> StatusHecho; else -> TextoGris }
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick(incidencia) }, elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(6.dp).background(statusColor))
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Text(incidencia.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(incidencia.descripcion, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextoGris)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 16.dp), tint = TextoGris)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleIncidenciaScreen(incidencia: IncidenciaUI, onUpdate: (IncidenciaUI) -> Unit, onSaveAndExit: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val tempPhotoUri = remember { mutableStateOf<Uri?>(null) }
    var photoToView by remember { mutableStateOf<Uri?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var comentarioTemp by remember { mutableStateOf(incidencia.comentario) }
    val fotosState = remember { mutableStateListOf<Uri>().apply { addAll(incidencia.fotos) } }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoUri.value != null) { fotosState.add(tempPhotoUri.value!!); onUpdate(incidencia.copy(fotos = fotosState.toList())) }
    }

    if (photoToView != null) FullScreenImageDialog(uri = photoToView!!, onDismiss = { photoToView = null })

    Scaffold(
        topBar = { TopAppBar(title = { Text("Detalle de la Tarea", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp)) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(incidencia.titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(incidencia.descripcion, fontSize = 16.sp, color = TextoGris)

                if (incidencia.createdAt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Publicada: ${incidencia.createdAt}", fontSize = 12.sp, color = TextoGris)
                }
                if (incidencia.finishedAt.isNotEmpty() && incidencia.estado == "HECHO") {
                    Text("Finalizada: ${incidencia.finishedAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado:", fontWeight = FontWeight.Bold)
                    DropdownEstado(incidencia.estado) { onUpdate(incidencia.copy(estado = it)) }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Comentarios / Observaciones:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (isEditing) {
                    Row(verticalAlignment = Alignment.Top) {
                        OutlinedTextField(value = comentarioTemp, onValueChange = { comentarioTemp = it }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onUpdate(incidencia.copy(comentario = comentarioTemp)); isEditing = false }) { Icon(Icons.Default.Check, null, tint = StatusHecho) }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (incidencia.comentario.isEmpty()) "Sin comentarios" else incidencia.comentario, modifier = Modifier.weight(1f))
                        IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Evidencias Locales (${fotosState.size})", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { val uri = crearArchivoImagen(context); tempPhotoUri.value = uri; cameraLauncher.launch(uri) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NegroFondo)
                    ) {
                        Icon(Icons.Default.Add, null, tint = NegroFondo)
                        Spacer(Modifier.width(4.dp))
                        Text("Foto", color = NegroFondo, fontWeight = FontWeight.Bold)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(fotosState) { uri ->
                        Box {
                            Box(Modifier.clickable { photoToView = uri }) { ImagenConRotacion(uri, true) }
                            IconButton(onClick = { fotosState.remove(uri); onUpdate(incidencia.copy(fotos = fotosState.toList())) }, modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.error, CircleShape).size(20.dp)) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(12.dp)) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onSaveAndExit, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NegroFondo)
            ) {
                Icon(Icons.Default.Check, null, tint = NegroFondo)
                Spacer(Modifier.width(8.dp))
                Text("GUARDAR CAMBIOS", color = NegroFondo, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- UTILIDADES ---

@Composable
fun FilterChipItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val tc = if (selected) NegroFondo else TextoGris
    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = tc, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun DropdownEstado(current: String, onChange: (String) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { exp = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NegroFondo)) { Text(current, color = NegroFondo, fontWeight = FontWeight.Bold) }
        DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
            listOf("PENDIENTE", "PROCESO", "HECHO").forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { onChange(s); exp = false }) }
        }
    }
}

@Composable
fun BadgeEstado(estado: String) {
    val color = when(estado.uppercase()) { "HECHO" -> StatusHecho; "PENDIENTE" -> StatusPendiente; else -> StatusProceso }
    Text(estado, Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = NegroFondo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ImagenConRotacion(uri: Uri, thumbnail: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        try { val input = context.contentResolver.openInputStream(uri); val original = BitmapFactory.decodeStream(input); input?.close(); original } catch (e: Exception) { null }
    }
    bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = if (thumbnail) Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)) else Modifier.fillMaxWidth(), contentScale = ContentScale.Crop) }
}

@Composable
fun FullScreenImageDialog(uri: Uri, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { Box(Modifier.fillMaxSize().background(NegroFondo).clickable { onDismiss() }, contentAlignment = Alignment.Center) { ImagenConRotacion(uri, false) } }
}

fun crearArchivoImagen(context: Context): Uri {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File.createTempFile("JPEG_${ts}_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "com.toka.taskflow.provider", file)
}