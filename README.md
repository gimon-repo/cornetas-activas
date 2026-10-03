# Cornetas Activas (Google TV / Android TV)

App que evita que las cornetas Bluetooth se apaguen solas cuando no hay audio.
Combina varias técnicas a la vez:
- Mantiene abierta sin pausa la transmisión de audio Bluetooth hacia las cornetas.
- Envía un tono muy grave (mezcla de 30 y 50 Hz) a bajo nivel, que no se oye.
- En modo Combinado, cada pocos minutos refuerza ese tono 4 veces (+12 dB) durante 3 segundos.
- Compensa el volumen: si bajas el volumen de la TV, la app sube su señal para que llegue igual a las cornetas.
- Modo descanso: si no hay cornetas Bluetooth conectadas, deja de enviar audio (tampoco suena por la TV)
  y vuelve sola en cuanto se conectan.
Se mezcla con el audio de otras apps.

## Instalar en la TV (con la app Downloader)
1. En la TV: Ajustes > Sistema > Acerca de > pulsa 7 veces "Compilación del SO" para activar Opciones de desarrollador.
2. Instala "Downloader" (de AFTVnews) desde la Play Store de la TV.
3. Ajustes > Apps > Seguridad y restricciones > Fuentes desconocidas > activa "Downloader".
4. Sube `CornetasActivas.apk` a un enlace directo (Google Drive con enlace público, Dropbox con ?dl=1, etc.),
   abre Downloader, escribe el enlace y pulsa Instalar.

## Instalar con ADB (desde una computadora)
1. Activa Opciones de desarrollador (paso 1 arriba) y dentro activa "Depuración USB" / "Depuración de red".
2. `adb connect IP_DE_LA_TV:5555` (acepta el aviso en la TV)
3. `adb install CornetasActivas.apk`

## Uso
Abre "Cornetas Activas" y pulsa **Activar**. Queda una notificación mientras está activa.
- Modo Combinado (recomendado), Continuo o solo Pulsos; pulsos cada 1/3/5/10 min.
- Si las cornetas aún se apagan: sube el Nivel o acorta el intervalo. Si oyes zumbido: baja el Nivel o usa 20 Hz.
- "Iniciar al encender la TV" la arranca sola tras reiniciar la TV.
- Con la TV en silencio (mute) o volumen 0 no sale ninguna señal y las cornetas se apagarán igual.

## Permisos (para que funcione siempre en 2do plano)
Al abrir la app pide, uno por uno:
1. **Notificaciones** (Android 13+): pulsa Permitir.
2. **Sin optimización de batería**: pulsa Permitir. Esto deja que siga activa siempre y que se reactive sola.
Arriba de la pantalla se ve "Permisos: notificaciones ✓ · sin límite de batería ✓". Si alguno sale ✗, pulsa **Revisar permisos**.

Además la app se arranca sola al encender/reiniciar la TV y una alarma la revisa cada 15 minutos
y la vuelve a activar si el sistema la cerró.

Si tu TV no muestra la pantalla de batería, se puede dar ese permiso por ADB:
`adb shell dumpsys deviceidle whitelist +com.favio.cornetas`

## Compilar
`build.sh` compila sin Gradle (aapt2 + javac + dx + apksigner). La clave de firma está en
`keystore/cornetas.jks` (contraseña `android`); usa la misma para que las actualizaciones se instalen encima.
