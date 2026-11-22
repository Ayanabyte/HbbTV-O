# HbbTV O!

Aplicación Android TV independiente, escrita en Kotlin, para recibir canales TDT y abrir aplicaciones HbbTV mediante las bibliotecas TCL disponibles en el televisor.

## Uso

1. Instala la APK en el televisor TCL y concede el permiso para consultar la lista de canales.
2. Espera a que aparezca la imagen de un canal TDT compatible con HbbTV.
3. Pulsa **Abrir LOVEStv**. La app inicializa el navegador HbbTV del televisor, conserva el canal sintonizado y envía la tecla azul.
4. El botón **Azul** permite volver a enviar esa tecla. **Estado HbbTV** muestra el estado informado por las bibliotecas TCL.

La app selecciona Antena 3 cuando está disponible; si no, sintoniza el primer canal TDT encontrado. También permite cambiar al canal anterior o siguiente.

## Implementación

- Consulta canales DVB-T y DVB-T2 reales desde Android TV Input Framework.
- Reproduce la emisión en directo con `TvView`.
- Integra `com.tcl.hbbtv.HbbtvServer` y `com.tcl.tbrowser.Tbrowser` mediante sus APIs disponibles en el dispositivo.
- Reenvía la tecla azul del mando al motor HbbTV de TCL.
- Mantiene su propio paquete, proceso y permisos mínimos para la consulta de canales.

La disponibilidad de LOVEStv depende de que el canal esté transmitiendo la aplicación HbbTV y de que HbbTV esté habilitado en el firmware. La inicialización del motor o la aceptación de una tecla no prueban por sí solas que la aplicación LOVEStv haya abierto; la app informa los estados que expone TCL.

## Compilar

Abrir la carpeta con Android Studio, JDK 17 y Android SDK 34, o ejecutar `gradle --no-daemon :app:assembleDebug` con Gradle 8.2.1. El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Dispositivo de referencia

TCL G07 / RT51A con Android TV 11. Los nombres y métodos del motor HbbTV dependen del firmware TCL instalado.
