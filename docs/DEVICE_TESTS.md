# Pruebas reales en TCL RT51A

Fecha: 22 de noviembre de 2026. La TV respondió como `product:G07_4K_GB_NF`, `model:Smart_TV`, Android TV conectada por ADB inalámbrico.

## Inspección antes del cambio

- Se abrió la APK diagnóstica instalada; la captura mostraba una pantalla titulada “Prueba LOVEStv 1.45”, con acciones para comprobar acceso, probar internamente, abrir la app de TV TCL y compartir o guardar un informe. La pantalla instruía elegir un canal y pulsar Azul en la app original. Por tanto, esa APK era una herramienta de prueba, no la interfaz del servicio LOVEStv.
- Se abrió `com.tcl.tv/.TVActivity`. La ventana enfocada fue `com.tcl.tv/com.tcl.tv.TVActivity`.
- El logcat de TCL mostró el servicio HbbTV consultando una lista de 95 canales y el navegador terminando la carga de `https://hbbtvloves.mediaset.es/pro1/index.xhtml?...`.
- La entrada de TV TCL estaba instalada como componente de 32 bits (`armeabi-v7a`).
- No se encontró una app LOVEStv separada en el inventario de paquetes de terceros. La evidencia recogida apunta a que el servicio interactivo se abre desde la app de TV TCL.
- La captura de la interfaz HbbTV del sistema no se pudo conservar: `screencap` devolvió un archivo vacío cuando la superposición del navegador del televisor estaba activa. La carga de la página está confirmada por logcat, pero no se presenta una captura visual de LOVEStv.

Los APK extraídos y el logcat completo se guardaron localmente durante la inspección. No se incluyen en este repositorio.

## Alcance de la nueva APK

La APK nueva comprueba disponibilidad de componentes TCL por APIs públicas y abre la actividad exportada de TV. No inicia directamente el motor del fabricante, no cambia el canal, no detecta automáticamente el resultado de Azul y no afirma integrar la aplicación interactiva. Para comprobar el último paso, la persona debe seleccionar un canal, pulsar Azul y registrar el resultado manualmente al volver a HbbTV O!.

## Ejecución de la APK nueva

![Pantalla de diagnóstico de HbbTV O! en la TCL](images/device-diagnostic.png)

- `assembleDebug`: compilación correcta con Gradle 8.13 y JDK 17.
- Instalación por ADB: correcta con el identificador nuevo `com.ayanabyte.hbbtvo`; la APK anterior `com.ayanabyte.hbbtvo.live` se mantuvo instalada.
- Inicio en la TV: correcto; se observó la pantalla diagnóstica nueva a 1920×1080.
- **Comprobar acceso**: informó `com.tcl.tv` versión `2.05.07_5ea8bb8_240719`, actividad disponible y entrada de sintonizador instalada.
- **Prueba del dispositivo**: observó TCL / Smart TV, Android 11/API 30, proceso de 32 bits, ABI `armeabi-v7a` y archivos de bibliotecas HbbTV/TBrowser presentes.
- **Abrir TV TCL**: al activar el botón con el mando simulado por ADB, la ventana enfocada cambió a `com.tcl.tv/.TVActivity`. La captura de pantalla de esa superposición devolvió cero bytes.
- **Volver a HbbTV O!**: Atrás desde la TV TCL no devolvió a O! en esta prueba; al volver a abrir O! desde el lanzador, reapareció el informe y la anotación de derivación.
- **Guardar informe**: creó `files/informe-hbbtv-o.txt` en el almacenamiento privado de O!.
- Logcat no mostró fallos de la actividad nueva.

La prueba se detuvo en la entrada de la TV original. No se cambió el canal ni se envió Azul desde esa prueba de aceptación; esos pasos requieren el mando del televisor. Así que la ejecución confirma el diagnóstico y la derivación, no la apertura de LOVEStv desde HbbTV O!.

## Aceptación pendiente

- Instalar y abrir la APK con identificador `com.ayanabyte.hbbtvo` en la TCL.
- Comprobar cada acción de diagnóstico y el informe.
- Abrir TV TCL, seleccionar Antena 3 y pulsar Azul manualmente; volver y anotar el resultado.
- Repetir manualmente en RTVE y Mediaset si se solicita.

Hasta completar esos pasos desde la APK nueva, no se declara que HbbTV O! abra o navegue por LOVEStv.
