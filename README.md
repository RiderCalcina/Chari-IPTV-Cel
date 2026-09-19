# Chary-IPTV (Versión Móvil) 📱

**Chary-IPTV** es la versión diseñada específicamente para dispositivos móviles Android del proyecto original de televisión [Chari-IPTV](https://github.com/RiderCalcina/Chari-IPTV). 

Esta aplicación te permite visualizar canales de televisión en vivo (IPTV) organizados por países, con un fuerte enfoque en el rendimiento, la facilidad de uso y la integración con el ecosistema móvil.

## 🚀 Características Principales

*   **Arquitectura Moderna:** Desarrollada 100% en Kotlin con Jetpack Compose y Clean Architecture.
*   **Google Cast (Chromecast):** Transmite cualquier canal directamente a tu Smart TV o dispositivo Chromecast con un solo toque.
*   **Rotación Dinámica e Inteligente:** Navega el catálogo cómodamente en formato vertical u horizontal. Al reproducir un video en el dispositivo, la pantalla pasa automáticamente a modo apaisado (Landscape) para una experiencia inmersiva.
*   **UI Optimizada para Móviles:** Diseño táctil fluido basado en Material Design 3, eliminando los bordes y controles engorrosos de la versión de TV.
*   **Noticias RSS Integradas:** Mantente informado con un feed de noticias que se desliza sutilmente en la cabecera de la app de forma minimalista.
*   **Gestión de Favoritos:** Mantén presionado cualquier canal para guardarlo en tu lista de favoritos y acceder a él rápidamente.

## 📺 Diferencias Clave: Chary-IPTV (Móvil) vs Chari-IPTV (Android TV)

Ambas aplicaciones comparten la misma poderosa lógica interna (uso de Room, Hilt, Corrutinas, y Repositorios), pero están optimizadas para experiencias completamente distintas:

| Característica | Chary-IPTV (Esta versión - Móvil) | Chari-IPTV (Versión Android TV) |
| :--- | :--- | :--- |
| **Navegación** | 100% táctil (Gestos, Taps, Scroll libre). | D-Pad (Control remoto direccional). |
| **Librerías UI** | `androidx.compose.material3` estándar. | `androidx.tv.material3` (enfocada a Leanback). |
| **Reproducción** | Rotación dinámica (horizontal automático) o transmisión vía Google Cast. | Siempre a pantalla completa (Full Screen TV). |
| **Google Cast** | Soportado e integrado nativamente. | No necesario (La TV es el receptor). |
| **Cabecera** | Fila ultra delgada con cinta de noticias RSS. | Menú lateral / Cabecera tradicional para TV. |
| **Manifest** | Requiere soporte Touch y pantallas móviles. | Requiere Leanback y bloquea la orientación. |

## 🛠️ Tecnologías Utilizadas

*   **Kotlin**
*   **Jetpack Compose** (Material 3)
*   **Media3 (ExoPlayer)** para reproducción de video HLS
*   **Google Cast SDK** para transmisión a TV
*   **Dagger Hilt** para Inyección de Dependencias
*   **Room** para Base de Datos local (Favoritos y Caché)
*   **Retrofit & OkHttp** para peticiones de red
*   **Coil** para carga de imágenes asíncrona

## 📦 Instalación y Compilación

1.  Clona el repositorio: `git clone https://github.com/RiderCalcina/Chary-IPTV.git`
2.  Abre el proyecto en Android Studio.
3.  Sincroniza los archivos de Gradle.
4.  Ejecuta la aplicación en un emulador o dispositivo físico Android (`assembleDebug`).
