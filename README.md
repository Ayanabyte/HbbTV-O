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
