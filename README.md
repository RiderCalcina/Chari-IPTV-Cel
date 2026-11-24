# Chari-IPTV Cel (Versión Móvil) 📱✨

[![Android](https://img.shields.io/badge/Plataforma-Android%207.0%2B%20(API%2024%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0%2B-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-purple.svg)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/Versi%C3%B3n-1.2-brightgreen.svg)]()
[![License](https://img.shields.io/badge/Licencia-Libre%20Acceso-orange.svg)]()

**Chari-IPTV Cel** es la edición de alto rendimiento diseñada específicamente para teléfonos inteligentes y tabletas Android del ecosistema [Chari-IPTV](https://github.com/RiderCalcina/Chari-IPTV).

Esta aplicación te permite sintonizar canales de televisión abierta en español de todo el mundo y disfrutar de la filmografía completa de Studio Ghibli, combinando una interfaz táctil moderna (Material 3) con un motor de reproducción multimedia de nivel profesional.

---

## 🚀 Novedades y Características de la Versión 1.2

### 1. 🎋 Filmografía Completa de Studio Ghibli (1984–2023)
* **25 Películas en Orden Cronológico:** Catálogo histórico íntegro desde *Nausicaä del Valle del Viento* (1984) hasta *El chico y la garza* (2023).
* **Fuentes Duales:** Enlaces primarios y espejos de respaldo públicos (BitChute + Internet Archive).
* **Reanudación Automática de Reproducción:** Si sales a mitad de una película, la app te preguntará si deseas continuar exactamente donde lo dejaste (`ResumePlaybackDialog`) con guardado persistente de posición (`GhibliPlaybackStore`).
* **Fichas Cinematográficas:** Pósters con proporción cinematográfica (2:3), año de estreno y número cronológico.

### 2. 👆 Gestos Táctiles en Pantalla
El reproductor nativo integra controles táctiles intuitivos que evitan tener que pausar o buscar botones diminutos:
* **Brillo de Pantalla:** Desliza verticalmente en la mitad izquierda de la pantalla para aumentar o atenuar el brillo.
* **Volumen Multimedia:** Desliza verticalmente en la mitad derecha de la pantalla para subir o bajar el volumen.
* **Salto Rápido:** Doble toque en el lado izquierdo para retroceder 10 segundos, o en el lado derecho para adelantar 10 segundos.
* **Alternar Controles:** Un solo toque en cualquier parte de la pantalla oculta o muestra el HUD interactivo.

### 3. 🔄 Rotación 100% Controlada por Sensor Físico
* La reproducción inicia de forma natural y estable en la orientación en que sostengas tu equipo.
* La pantalla **únicamente girará cuando tú voltees físicamente el celular**, eliminando saltos bruscos o temporizadores molestos.

### 4. 🌟 Sección "Importante" y Donaciones
* Acceso directo mediante una insignia destacada con borde ámbar y brillo dorado en la barra superior.
* **Códigos QR Verticales:** Códigos QR en alta resolución dispuestos de forma vertical, adaptados a la lectura cómoda en celulares con su respectiva leyenda explicativa:
  * 💳 **PayPal:** Donaciones voluntarias internacionales con tarjeta de crédito, débito o saldo.
  * 📱 **Yape (Perú / BCP):** Transferencias directas en moneda local.
  * 🌐 **Sitio Web Oficial:** Enlace y QR hacia [www.qwertyaserty.com](https://qwertyaserty.com) para actualizaciones y soporte.
* Cuadro de aviso legal, autoría y exención de responsabilidad transparente.

### 5. ⭐ Gestión Rápida de Favoritos
* Identificación con **estrella dorada (`⭐`)** al estilo de la versión de televisión.
* Mantén presionado cualquier canal o película para añadirlo o quitarlo de tus favoritos al instante.

### 6. 📺 Google Cast & Picture-in-Picture (PiP)
* **Chromecast Integrado:** Transmite cualquier canal o película a tu Smart TV con un solo toque desde el botón de transmisión.
* **Modo PiP:** Continúa escuchando o viendo la transmisión en una ventana flotante mientras usas WhatsApp, navegas en internet o revisas otras aplicaciones.

### 7. ⚡ Arquitectura de Alto Rendimiento
* **Multi-Proveedor Asíncrono:** Conexión modular con `IptvOrgProvider` (enfoque en transmisiones hispanohablantes), `TdtChannelsProvider`, `PlutoTvProvider` y `AniVodProvider`. Cero bloqueos en el hilo principal.
* **Room Database v13:** Caché persistente local con vigencia inteligente de 4 horas (TTL).
* **Dual OkHttp + DoH:** Resolución DNS sobre HTTPS (Cloudflare + Google DNS) inmune a bloqueos por proveedores de internet.
* **Decodificador Jellyfin FFmpeg:** Soporte para transmisiones con pistas de audio complejas o códecs de TV digital.
* **Proxy Local Anti-SSRF:** Servidor interno en loopback (puerto 8899) para inyección de cabeceras seguras y recuperación ante caídas de señal.

---

## 📋 Estructura de Navegación

Las pestañas superiores se encuentran organizadas en el siguiente orden:

1. **🌟 Importante** *(Modal de donaciones y soporte con códigos QR)*
2. **⭐ Favoritos** *(Acceso rápido a tus canales preferidos guardados en el móvil)*
3. **🌎 Países** *(Filtro por países hispanohablantes con selector de banderas)*
4. **⚽ Deportes** *(Fútbol, transmisiones deportivas en vivo)*
5. **📰 Noticias** *(Canales informativos nacionales e internacionales)*
6. **🎬 Películas** *(Canales dedicados al cine)*
7. **📺 Series** *(Telenovelas, drama y comedia)*
8. **👶 Infantil** *(Animación y programación para niños)*
9. **🎵 Música** *(Canales musicales y de videoclips)*
10. **🎋 Studio Ghibli** *(Filmografía completa 1984–2023)*

---

## 🛠️ Tecnologías y Librerías

* **Lenguaje:** Kotlin 1.9+
* **Interfaz de Usuario:** Jetpack Compose (BOM 2024+) con Material Design 3
* **Reproductor:** Media3 ExoPlayer 1.9.0 + Jellyfin FFmpeg Audio Extension (`1.9.0+1`)
* **Inyección de Dependencias:** Dagger Hilt 2.51
* **Base de Datos Local:** AndroidX Room v13 (KSP)
* **Red:** OkHttp 4.12.0 con DnsOverHttps
* **Carga de Imágenes:** Coil 2.7.0 (Soporte PNG, WEBP y SVG)
* **Transmisión:** Google Cast Framework SDK
* **Optimización:** R8 / ProGuard (`isMinifyEnabled = true`, `isShrinkResources = true`)

---

## 📦 Compilación y Generación del APK

### Requisitos
* JDK 17
* Android SDK 36 (Android 15 / 16 Preview)
* Android Studio Ladybug / Koala o superior

### Pasos de Compilación

1. Clonar el repositorio:
```bash
git clone https://github.com/RiderCalcina/Chary-IPTV.git
cd Chary-IPTV
```

2. Compilar el APK Release optimizado:
```bash
./gradlew :app:assembleRelease
```

3. El APK final se generará en:
```text
app/build/outputs/apk/release/Chari-IPTV_Cel_1.2.apk
```

---

## ⚖️ Aviso Legal y Exención de Responsabilidad

* **Naturaleza del Software:** Chari-IPTV Cel es un reproductor multimedia cliente de libre acceso. No almacena, gestiona, transmite ni retransmite señales o contenidos en servidores propios.
* **Señales de Televisión:** Todos los enlaces IPTV proceden de listas e índices abiertos de dominio público en internet (`iptv-org`, `TDTChannels`). El desarrollador no tiene vinculación con las emisiones ni garantiza su disponibilidad continua.
* **Catálogo Studio Ghibli:** El apartado indexa enlaces de preservación cultural pública alojados en Internet Archive (`archive.org`). Ningún archivo de video reside en los servidores del desarrollador.
* **Propiedad Intelectual:** Todas las marcas, logotipos, producciones y señales pertenecen a sus respectivos autores, cadenas televisivas y a Studio Ghibli Inc.
