# Informe de privacidad y seguridad

Alcance: lo que se puede comprobar revisando el APK y el código. **No se afirma que la app sea «100 % privada»**: se documenta qué se comprobó, cómo repetirlo y qué no se puede demostrar.

## Permisos (3) y por qué

| Permiso | Para qué | ¿Se puede quitar? |
|---|---|---|
| `INTERNET` | Android exige este permiso para abrir **cualquier** socket, también hacia la red local. Sin él no hay HTTP ni UDP al Roku. | No. Por eso la restricción a red local está en el código (abajo). |
| `ACCESS_NETWORK_STATE` | Elegir la red Wi-Fi/Ethernet y fijar el tráfico a ella aunque los datos móviles estén activos. | Sí, pero el control podría salir por datos móviles y fallar. |
| `CHANGE_WIFI_MULTICAST_STATE` | Recibir las respuestas SSDP al buscar Roku (solo mientras se busca). | Sí; dejaría de funcionar la búsqueda automática (quedaría la IP manual). |

Sin permisos de ubicación, contactos, almacenamiento, micrófono, cámara, notificaciones ni identificadores. Sin servicios, receptores, proveedores ni segundo plano: una sola actividad.

## Qué se comprobó (`./audit.sh`, salida en `auditoria-salida.txt`)

- Exactamente los 3 permisos anteriores; sin service/receiver/provider; una actividad; `allowBackup=false`.
- El APK contiene solo `classes.dex`, recursos y manifiesto: sin bibliotecas nativas ni assets.
- Todas las clases del dex son propias (`app/rokuremote`) o del SDK de Android/Java: **ningún SDK de terceros** (ni analítica, ni publicidad, ni rastreo). Dependencias: ninguna.
- Ninguna cadena de analítica/publicidad/telemetría (firebase, crashlytics, admob, appsflyer, sentry, facebook, etc.) en código ni recursos.
- **Ninguna URL** en el código. Direcciones IP literales: solo el grupo multicast SSDP `239.255.255.250` y un texto de ejemplo (`192.168.1.20`) en la caja de IP, que no se contacta.

## Cómo se limita el tráfico a la red local

1. La IP manual solo se acepta si es IPv4 privada (10/8, 172.16/12, 192.168/16) o de enlace local (169.254/16); nombres de host, puertos y rutas se rechazan.
2. Las respuestas SSDP se ignoran si su `LOCATION` no es una IP local.
3. `RemoteController.select()` y los dispositivos guardados se validan con `Hosts.isAllowedTarget` (red local o el propio teléfono, 127/8, que no sale del dispositivo).
4. No hay en el código ningún otro punto que abra conexiones: los sockets se crean solo en `EcpClient` (HTTP) y `SsdpDiscovery` (UDP); `WifiNetBinder` solo abre la conexión que `EcpClient` le pide (`grep -rn "Socket\|openConnection" src` lo confirma). Las pruebas verifican los puntos 1-3 y que las únicas rutas usadas son `/query/*`, `/keypress/*` y `/launch/*`.
5. El tráfico se fija a la red Wi-Fi/Ethernet (`WifiNetBinder`), no a datos móviles.

## Datos guardados

Solo en preferencias privadas de la app: para cada Roku recordado, **número de serie, IP y nombre** (máx. 20), y el serie del último usado. Sin historial, sin registros, sin identificadores del teléfono. `allowBackup=false`: no se incluye en copias de seguridad. Desinstalar la app los borra. Una prueba verifica que no se guarda nada más.

## Riesgos y lo que NO se puede demostrar

- **ECP es HTTP sin cifrar ni autenticación fuerte**: cualquiera en tu red Wi-Fi puede ver o enviar las mismas órdenes. Es una limitación del protocolo del Roku, no de la app. `cleartextTrafficPermitted=true` es necesario para ello (Android no permite listar rangos de IP; el límite real es el código).
- La restricción a red local es una garantía del **código de esta versión**; el permiso `INTERNET` por sí solo permitiría más. Quien recompile otra versión puede cambiarla; por eso conviene verificar el APK con `audit.sh` y comparar el SHA-256.
- Se revisó el APK y el código fuente, pero no el tráfico real en un teléfono (no hay captura de red con un dispositivo físico). Una captura con un Roku real sería la prueba definitiva.
- No se audita el sistema operativo, el teclado del teléfono (el texto del teclado de la app pasa por tu teclado habitual: usa uno de confianza) ni el firmware del Roku, que tiene su propia telemetría.
- Android 17 (SDK 37) añade el permiso `ACCESS_LOCAL_NETWORK` para apps con targetSdk 37; con targetSdk 36 las apps con `INTERNET` lo reciben implícitamente. Al subir el targetSdk habrá que solicitarlo.
