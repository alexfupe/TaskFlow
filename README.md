# TaskFlow 📋 

**Asignatura:** Programación Multimedia y Dispositivos Móviles (Android)

## 📖 Contexto del Proyecto
Aplicación corporativa Android para gestionar tareas e incidencias. Permite a los empleados revisar labores, reportar problemas, actualizar estados y adjuntar fotos en tiempo real.

## ✨ Requisitos y Funcionalidades
* **Autenticación:** Login seguro conectado a la API.
* **Dashboard:** Panel principal con accesos directos al listado y creación de tareas.
* **Mis Tareas:** Lista de incidencias con buscador de texto y filtros por estado (Todos, Pendientes, En Proceso, Hecho). Borrado con *Swipe to dismiss*.
* **Detalle y Evidencias:** Vista detallada de la tarea, edición de estado/comentarios y captura de fotos con la cámara nativa.
* **Nueva Tarea:** Formulario de creación de incidencias.
* **Perfil:** Visualización de datos del empleado y cambio de contraseña.

## 🛠️ Tecnologías
* **Android:** Kotlin, Jetpack Compose, MVVM, Retrofit2, Coroutines.
* **Backend:** API REST en Python.

## 🚀 Instalación y Ejecución

### 1. Levantar el Backend (API local)
Ejecuta la API y expón el puerto con ngrok:
```bash
python app.py
ngrok http 5000
````
*(Copia la URL `https://...` generada por ngrok).*

### 2. Configurar el Frontend (Android)

```bash
git clone [https://github.com/alexfupe/TaskFlow.git](https://github.com/alexfupe/TaskFlow.git)
````
1. Abre el proyecto en **Android Studio**.
2. Ve a `RetrofitClient` y pega la URL de ngrok en `BASE_URL` (asegúrate de que acabe en `/`).
3. Sincroniza Gradle y ejecuta la app.
