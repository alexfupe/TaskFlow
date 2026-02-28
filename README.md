TaskFlow 📋
App nativa en Android para la gestión corporativa de tareas e incidencias, conectada a una API REST remota.

✨ Características
Autenticación: Login seguro basado en tokens.

Gestión de Tareas: Crear, listar, buscar, filtrar por estado, editar y eliminar (swipe to dismiss).

Evidencias: Captura de fotos desde la cámara nativa para adjuntar a las incidencias.

Perfil: Visualización de datos de usuario y cambio de contraseña.

🛠️ Tecnologías
Android: Kotlin, Jetpack Compose, Arquitectura MVVM, Retrofit.

Backend: Python + ngrok.

🚀 Instalación y Ejecución
1. Levantar el Backend
Ejecuta la API de Python y expón el puerto local con ngrok:

Bash
python app.py
ngrok http 5000
(Copia la URL https://... generada por ngrok).

2. Configurar la App (Android)
Clona el repositorio:

Bash
git clone https://github.com/TU_USUARIO/TU_REPOSITORIO.git
Abre el proyecto en Android Studio.

Ve a la configuración de Retrofit (RetrofitClient) y cambia la variable BASE_URL por tu enlace de ngrok (asegúrate de que termine en /).

Sincroniza Gradle y ejecuta la app.
