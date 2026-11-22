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

## Prueba 0.1.9 — ABI TCL 32-bit (hipótesis basada en Channel+ 1.42)

El registro original de Channel+ (20:57:47–20:57:59) demuestra que su ejecución funcional usó un proceso de **32 bits** (`64bit=false`), registró TVSDK (el método `systemAuthorized` devolvió true) **antes** de `HbbtvServer.init()`, inicializó `Tbrowser` directamente sobre su Activity y alcanzó `EN_TCL_HBBTV_ON_NO_VOD`.

HbbTV O! 0.1.8 reprodujo el nombre `com.tcl.tv` de proceso pero no comprobó su ABI real. 0.1.9 añade `android:use32bitAbi="true"` como intento de selección de zygote de 32 bits, registra `Process.is64Bit()`, ABIs soportados y `nativeLibraryDir` tanto al abrir como en el preflight. **No se asume que el atributo haya surtido efecto**: el registro de proceso en el dispositivo es la prueba definitiva. Si sigue en 64 bits, hace falta una variante APK con solo librerías `armeabi-v7a` empaquetadas y firmadas, como la variante hbbtv32 de Channel+.

El registro OEM de Channel+ no se replica sin conocer un procedimiento verificable y autorizado. Ni un `start()=0` ni `Process.is64Bit()=false` demuestran por sí solos que LOVEStv esté funcionando.

## Prueba 0.1.10 — mantener la sesión DVB sin reset

Inspección del **APK real** Channel+ LOVEStv 1.45 (`classes.dex`, `ProbeActivity` y `ExperimentalAuthorization`): su flujo normal sintoniza el `TvView`, espera a que haya vídeo, registra TVSDK, inicia `HbbtvServer` y `Tbrowser` con la Activity real, y conserva la sesión DVB. La acción `retune()` existe como experimento de usuario independiente y no es un paso obligatorio tras `start`.

HbbTV O! 0.1.9, en cambio, ejecutaba `tvView.reset()` y una segunda `tune()` ~1,25 s después de `HbbtvServer.start()`, incluso si el canal ya reproducía. En 0.1.10 se quita exclusivamente ese reset/tune automático y se mantiene el mismo `TvView` y señal de TDT mientras se monitoriza el estado HbbTV durante 30 s. El cambio reduce una posible desconexión de estado PMT/AIT entre motor y sintonizador, sin concluir que resuelva el acceso nativo.

La descompilación verificó además que `ExperimentalAuthorization.begin()` en la APK de referencia calcula primero la huella SHA-256 de la APK oficial TCL, busca una cadena interna en el DEX del fabricante y la entrega a `TTvFunctionManager.systemAuthorized(String)`; esa llamada devuelve `true` en los registros originales. HbbTV O! no recupera ni reutiliza credenciales OEM. La ausencia del registro sigue siendo una diferencia y puede impedir la integración aun conservando el `TvView`.
