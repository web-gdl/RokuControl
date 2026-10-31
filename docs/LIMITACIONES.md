# Limitaciones conocidas

## Sin verificar en hardware (lo más importante)

- **No se probó en un teléfono Android ni con un Roku real.** La lógica y el protocolo se probaron con un Roku simulado (64 pruebas) y el APK se verificó estáticamente (firma, permisos, contenido). La interfaz compila y se empaqueta, pero **no se ha visto en pantalla**: pueden existir fallos de diseño o de ejecución que solo aparecen al usarla.
- «Encender» un TV en espera usa la tecla `PowerOn`; la documentación de ECP consultada solo lista `PowerOff` entre las teclas de TV. Muchos Roku TV la aceptan, pero no está verificado en ningún modelo. Si falla, la app avisa de que no se pudo enviar.

## Del protocolo y del Roku

- Volumen, silencio, encendido/apagado y canales son **solo Roku TV**. Los reproductores (Express, Streaming Stick, Ultra) no los admiten por ECP: los botones aparecen atenuados con la explicación.
- Si el TV está totalmente apagado y no mantiene la red en espera, no responde y **no puede encenderse por ECP** (no se implementó Wake-on-LAN).
- Roku OS 14.1+ exige «Control por aplicaciones móviles» habilitado; si está limitado/desactivado, las órdenes dan 403 y la app indica cómo habilitarlo.
- El teclado solo funciona mientras el Roku muestra su teclado en pantalla.
- La búsqueda SSDP no atraviesa redes distintas, VLAN ni routers con «aislamiento de clientes»/«AP isolation»; en ese caso usa la IP manual (si el aislamiento bloquea todo, tampoco funcionará).
- Funciones de ECP no implementadas: entradas HDMI/AV, canales de sintonizador, «buscar mi control» (FindRemote), iconos de las aplicaciones (se muestran por nombre), búsqueda por voz.

## De la app

- Solo vertical y solo español. Tema oscuro único.
- Las teclas de volumen físicas del teléfono no controlan el Roku.
- Sin ofuscación (R8): el APK es de ~40 KB y es código abierto, no hace falta.
- Una IP que cambie mientras la app está cerrada se corrige al abrirla (búsqueda SSDP); con una red que bloquee multicast habrá que conectarla de nuevo por IP.
- Los avisos de error se limitan a uno cada 2,5 s para no saturar al mantener pulsado; puede omitirse un aviso legítimo que ocurra justo después de otro.
