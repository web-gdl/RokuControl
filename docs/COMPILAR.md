# Compilar

1. JDK 17 o superior (`java`, `javac`, `keytool`) y `zip`.
2. Crear `tools/` con:
   - `android-36.jar`: `android.jar` de la plataforma Android 36 (p. ej. del repositorio Sable/android-platforms, carpeta `android-36`).
   - `lib/jadx-1.5.1-all.jar`: del zip de jadx 1.5.1 (contiene D8 y apksig).
   - `ap/prebuilt/linux/aapt2_64`: dentro de `apktool_2.11.1.jar` (carpeta `prebuilt/linux`); darle permiso de ejecución.
3. `./build.sh`. Crea `keystore/release.p12` si no existe (contraseña por defecto `rokuremote`, cámbiala con `KEYSTORE_PASSWORD`).
4. Resultado: `out/RokuControl-<versión>.apk`, con firma v2 y v3 verificadas por apksig.

`build.sh` falla ante cualquier error de javac; el código compila sin avisos.
