# HbbTV O!

Proyecto Android TV independiente, escrito en Kotlin desde cero para investigar el motor HbbTV instalado en televisores TCL y reproducir TDT mediante Android TV Input Framework.

## Identidad

- App: **HbbTV O!**
- Package: `com.ayanabyte.hbbtvo`
- Primera versión: `0.1.0`
- TV de referencia: TCL G07 / RT51A con Android 11

## Qué implementa

- Enumeración de canales DVB-T y DVB-T2 reales del proveedor de Android TV.
- Sintonización mediante `TvView`, sin IDs de canales inventados.
- Inicialización opcional por reflexión de `com.tcl.hbbtv.HbbtvServer` y `com.tcl.tbrowser.Tbrowser`.
- Registro de eventos de HbbTV y callback de inicialización del navegador.
- Comprobación del mapeo del mando de TCL y envío de tecla Azul.
- Interfaz simple para mando a distancia y logs visibles + Logcat (`HbbTVO`).

## Prueba con Bugjaeger

1. Instalar la APK de `app` en TCL.
2. Abrir **HbbTV O!** y permitir leer la lista de canales.
3. Esperar `onVideoAvailable` en un canal terrestre.
4. Elegir **Analizar TCL**, **Iniciar motor**, **Start HbbTV** y **Azul**.
5. Obtener registros: `logcat -d -v time -s HbbTVO:I '*:S'`.

`HbbtvServer.start()` no garantiza que la AIT del canal se haya reconocido. La aplicación registra por separado inicialización, reconocimiento del navegador y aceptación del mando. LOVEStv embebido queda **pendiente de validación**.

## Compilar

Abrir la carpeta con Android Studio (JDK 17, SDK 34 y conexión para descargar los complementos Gradle) y ejecutar `assembleDebug` sobre el módulo `app`. En CI se construye con Gradle 8.2.1, AGP 8.1.4 y Kotlin 1.9.22. No se necesita copiar APKs ni archivos de otras aplicaciones al código del proyecto.

Repositorio previsto: `https://github.com/Ayanabyte/HbbTV-O`

## Diagnóstico de identificación DVB (0.1.3)

El botón **Contexto DVB** compara de forma exclusivamente de lectura la tripleta ONID/TSID/SID del canal Android TV con `TTvChannelManager.getCurrentProgramInfo()`, que expone `channelId`, `onID`, `transportStreamID` y `serviceID`. También lee `TTvFunctionManager.isHbbtvOn()` y `HbbtvServer.getState()`. Los métodos están presentes en los JAR proporcionados por el usuario; los JAR propietarios no se incluyen en el repositorio. Una discrepancia o valor nulo no demuestra por sí solo qué control interno de TCL falta.

En Bugjaeger: `logcat -d -v time -s HbbTVO:I '*:S'`.

## Permisos y firma TCL (0.1.5)

El botón **Permisos TCL** informa del UID real, coincidencia de firmas y permisos efectivos. Se declara `android.permission.WRITE_SECURE_SETTINGS` para una prueba explícita y opcional con el shell ADB de la propia TV: `pm grant com.ayanabyte.hbbtvo android.permission.WRITE_SECURE_SETTINGS`. El resultado depende del firmware; una concesión no proporciona UID 1000 ni firmas TCL. El botón es de lectura y no altera configuraciones.

Para comparar, pulsar **Permisos TCL**, luego **HbbTV ON**, **Gestor ON** y de nuevo **Contexto DVB**. Si la activación sigue sin persistir, el bloqueo se halla fuera de ese permiso de desarrollo, o intervienen otras condiciones.

## Secuencia experimental TVSDK + HbbTV (0.1.6)

El botón **TVSDK + HbbTV** ejecuta una preparación comprobable sin clonar credenciales TCL: inspección de TVSDK y estado inicial; inicialización de `HbbtvServer`; petición `setLivetvMode(true)`; inicialización de `Tbrowser`; arranque en `onSuccess(true)`; reinicio de sesión `TvView` y sintonización del **mismo canal** con el motor arrancado. A continuación observa durante 30 s `HbbtvServer.getState()` y `THbbtvManager.getState()`, registrando transiciones y si se llega a `EN_TCL_HBBTV_ON_NO_VOD`. El botón **Estado TCL** consulta las mismas fuentes en cualquier momento.

El experimento prioriza Antena 3 normal (no A3 UHD) cuando está disponible. Si el motor responde `start()=0` pero continúa `NO_EXIST`, no se informa falsamente de que LOVEStv funciona. Las aplicaciones oficiales TCL usan otros privilegios; `systemAuthorized(String)` está presente, pero esta APK no incorpora ni extrae credenciales privadas del fabricante. Ningún código de Channel+ se copia al proyecto.

Logs: `logcat -d -v time -s HbbTVO:I '*:S' | tail -n 100`.

## Secuencia de arranque corregida (0.1.7)

A partir del diagnóstico 0.1.6 (se registró `HbbtvServer.init()=1` pero faltó `Tbrowser.onSuccess`), la prueba **TVSDK + HbbTV** prioriza `initTbrowser` y su callback. Solo después de recibir `onSuccess(true)` solicita `setLivetvMode(true)` y llama a `HbbtvServer.start()`. La secuencia posterior de resintonización y vigilancia de estado sigue igual. Un aviso de 12 segundos diferencia un navegador que no responde de un arranque que se ejecutó pero no detecta la AIT. Esta hipótesis se prueba sin afirmar que el cambio sea causa confirmada.

## Prueba de proceso de referencia Channel+ (0.1.8)

El análisis del APK anterior Channel+ LOVEStv 1.45 confirma que su `ProbeActivity` usaba `android:process="com.tcl.tv"` bajo el UID no privilegiado de Channel+. Sus logs muestran `hbbtv_app_exist=1`, `EN_TCL_HBBTV_ON_NO_VOD` y carga de `hbbtv.lovestv.es` dentro de ese proceso. En HbbTV O! 0.1.8 se prueba **solo esta diferencia aislada**: `MainActivity` se declara en un proceso denominado `com.tcl.tv`, **manteniendo el paquete** `com.ayanabyte.hbbtvo`, su firma ordinaria y su propio UID. No concede permisos ni suplanta el UID de TCL. La actividad imprime el nombre de proceso real a través de `Application.getProcessName()` para contrastarlo con `ps -A`.

Procedimiento: cerrar completamente la app antigua y cualquier experimento que comparta proceso, abrir HbbTV O! y pulsar `TVSDK + HbbTV`. Buscar `HbbTV estado`, `hbbtv_app_exist`, `LOAD_PAGE_START` y `EN_TCL_HBBTV_ON_NO_VOD`. Una coincidencia del nombre de proceso por sí sola no garantiza activación de HbbTV.

## Comparación con el canal exacto de Channel+ (0.1.9)

Los logs originales de Channel+ (22-11-2026, 15:32:40) muestran carga real de LOVEStv en la señal `c=antena3uhd`, con `hbbtv_app_exist=1` y HbbTV `ON_NO_VOD`. La prueba HbbTV O! 0.1.8 se hizo en `Antena 3` sin UHD y solo probó el cambio de proceso. Esta versión prioriza una variante de Antena 3 UHD de la lista **real** TIF, y solo recurre a Antena 3 normal si UHD no está disponible. Antes de arrancar, registra la identidad DVB y el canal elegido, para evitar conclusiones si el servicio difiere del de Channel+.

El parámetro web `ch=149` no se trata como ID DVB ni se codifica en el sintonizador. No se modifica la secuencia de inicialización del motor de la versión 0.1.8.
