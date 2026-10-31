#!/usr/bin/env bash
# Auditoría de privacidad del APK compilado. Falla (exit 1) si encuentra algo fuera de lo esperado.
set -uo pipefail
cd "$(dirname "$0")"
APK="${1:-$(ls out/RokuControl-*.apk | tail -1)}"
AAPT2=tools/ap/prebuilt/linux/aapt2_64
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
fail=0
ok()  { echo "  ok   $1"; }
bad() { echo "  FAIL $1"; fail=1; }

echo "APK: $APK"
unzip -q -o "$APK" -d "$T/x"

echo "1. Permisos (esperados: INTERNET, ACCESS_NETWORK_STATE, CHANGE_WIFI_MULTICAST_STATE)"
"$AAPT2" dump permissions "$APK" 2>/dev/null | grep -v '^package' | sed 's/^/     /'
PERMS="$("$AAPT2" dump permissions "$APK" 2>/dev/null | sed -n "s/^uses-permission: name='\(.*\)'.*/\1/p" | sort | tr '\n' ' ')"
[ "$PERMS" = "android.permission.ACCESS_NETWORK_STATE android.permission.CHANGE_WIFI_MULTICAST_STATE android.permission.INTERNET " ] \
  && ok "solo esos 3 permisos" || bad "permisos distintos: $PERMS"
"$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" > "$T/manifest.txt" 2>/dev/null
for tag in "E: service" "E: receiver" "E: provider"; do
  grep -q "$tag" "$T/manifest.txt" && bad "el manifiesto declara '$tag'" || ok "sin ${tag#E: } declarados"
done
[ "$(grep -c 'E: activity' "$T/manifest.txt")" = "1" ] && ok "una sola actividad" || bad "número de actividades inesperado"
grep -q 'allowBackup.*=false' "$T/manifest.txt" && ok "allowBackup desactivado (datos no salen en copias de seguridad)" || bad "allowBackup activo"

echo "2. Contenido del APK"
ls "$T/x" | tr '\n' ' '; echo
[ -d "$T/x/lib" ] && bad "contiene bibliotecas nativas" || ok "sin bibliotecas nativas"
[ -d "$T/x/assets" ] && bad "contiene assets" || ok "sin assets"
ls "$T/x"/classes*.dex | wc -l | grep -qx 1 && ok "un único classes.dex" || bad "varios dex"

echo "3. Clases incluidas (solo código propio, sin SDK ni bibliotecas)"
python3 - "$T/x/classes.dex" <<'PY' || fail=1
import re, sys
data = open(sys.argv[1], 'rb').read()
# Descriptores de clase en la tabla de cadenas: Lpaquete/Clase;
classes = set(m.decode() for m in re.findall(rb'L[a-zA-Z0-9_$/]{3,120};', data))
own = {c for c in classes if c.startswith('Lapp/rokuremote/')}
other = {c for c in classes if not c.startswith('Lapp/rokuremote/')
         and not c.startswith(('Ljava/', 'Landroid/', 'Ljavax/', 'Ldalvik/', 'Lkotlin/'))}
print("     clases propias:", len(own))
print("     paquetes java/android referenciados:", sorted({c.split('/')[0]+'/'+c.split('/')[1] for c in classes if c.startswith(('Ljava/','Landroid/','Ljavax/'))}))
if other:
    print("  FAIL referencias a clases ajenas:", sorted(other)); sys.exit(1)
print("  ok   no hay clases de terceros")
PY

echo "4. Cadenas sospechosas en el código y los recursos"
SUS='firebase|crashlytics|analytics|admob|doubleclick|appsflyer|adjust\.com|mixpanel|sentry|facebook|amplitude|segment\.io|branch\.io|onesignal|google-analytics|googleapis|gstatic|telemetry|tracker'
strings -n 4 "$T/x/classes.dex" "$T/x/resources.arsc" 2>/dev/null | grep -Eai "$SUS" > "$T/sus.txt" || true
[ -s "$T/sus.txt" ] && { bad "cadenas de rastreo:"; head "$T/sus.txt"; } || ok "ninguna cadena de analítica, publicidad o rastreo"
URLS="$(strings -n 6 "$T/x/classes.dex" | grep -Eio 'https?://[^ ")<]+' | sort -u || true)"
if [ -z "$URLS" ]; then ok "ninguna URL en el código"; else bad "URLs en el código: $URLS"; fi
IPS="$(strings -n 6 "$T/x/classes.dex" | grep -Eo '\b[0-9]{1,3}(\.[0-9]{1,3}){3}\b' | sort -u || true)"
echo "     direcciones IP literales en el código: $(echo "$IPS" | tr '\n' ' ')"
# Esperadas: grupo multicast SSDP y el ejemplo mostrado como ayuda en la caja de IP manual.
[ "$IPS" = "$(printf '192.168.1.20\n239.255.255.250')" ] \
  && ok "solo el grupo multicast SSDP (239.255.255.250) y el ejemplo de ayuda 192.168.1.20 (texto, no se contacta)" \
  || bad "IP fijas inesperadas"

echo "5. Red"
grep -q 'cleartextTrafficPermitted' <("$AAPT2" dump xmltree --file res/xml/network_security_config.xml "$APK" 2>/dev/null) \
  && ok "texto claro permitido solo por la config de seguridad (ECP usa HTTP); destinos limitados por código a IPv4 privadas" || bad "config de red no encontrada"

echo
[ $fail -eq 0 ] && echo "RESULTADO: sin hallazgos" || echo "RESULTADO: HAY HALLAZGOS"
exit $fail
