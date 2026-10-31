# Arquitectura

**Tecnología:** Java 8 sobre el SDK de Android, sin Gradle, AndroidX ni Kotlin; interfaz con vistas nativas y dibujo por Canvas (iconos y cruceta sin imágenes). Elegida por tamaño, arranque rápido, cero dependencias y para repetir la cadena de compilación de los otros proyectos (aapt2 → javac → D8 → apksig).

## Capas

| Capa | Paquete | Conoce Android | Responsabilidad |
|---|---|---|---|
| Red | `ecp` | No (solo `NetBinder`) | HTTP de ECP, SSDP, lectura de respuestas, validación de direcciones |
| Lógica | `core` | No | Dispositivo actual, vigilancia, reconexión, búsqueda, cola de órdenes, avisos |
| Datos | `data` | Sí | SharedPreferences privadas; fijar tráfico a Wi-Fi/Ethernet y bloqueo multicast |
| Interfaz | `ui` | Sí | Construir vistas y reenviar toques al controlador |

`core` y `ecp` se ejecutan en la JVM, por eso las pruebas corren sin dispositivo.

## Protocolo (ECP, puerto 8060)

- Descubrimiento: UDP multicast `239.255.255.250:1900`, `M-SEARCH`, `ST: roku:ecp`; se lee `LOCATION` y solo se aceptan IPv4 locales.
- `GET /query/device-info` (nombre, modelo, `power-mode`, `is-tv` o, en Roku OS 15+, `supports-tv-power-control` / `supports-audio-volume-control`).
- `GET /query/apps`; `POST /launch/<id>`.
- `POST /keypress/<Tecla>`; texto con `Lit_<carácter>` (UTF-8 codificado en porcentaje).

## Hilos y batería

Tres ejecutores de un hilo (vigilancia, envío, búsqueda); la interfaz nunca hace red. La vigilancia (una consulta cada 3 s) solo corre entre `onResume` y `onPause`. Las repeticiones al mantener pulsado se descartan si hay una orden en cola, para no acumular retraso.

## Reconexión

Con 2 consultas fallidas el estado pasa a «reconectando» y sigue intentando; cada 3 fallos lanza una búsqueda SSDP y, si encuentra el mismo número de serie en otra IP, se cambia a ella y la actualiza en lo guardado.
