# O! · LOVEStv Lab

Aplicación de prueba Android TV nueva e independiente para probar sintonización TDT con TvView y navegador HbbTV de TCL con botón azul.

No copia un DEX antiguo, ni librerías del fabricante, ni autorizaciones de apps de sistema. Las APIs del firmware TCL podrían negar acceso a aplicaciones externas: la compilación no demuestra funcionamiento.

## Prueba de aceptación

1. Abrir O! y conceder acceso a listas de TV.
2. Confirmar imagen de Antena 3 o elegir otro canal con OK.
3. Pulsar AZUL y verificar apertura real de LOVEStv dentro de O!.
4. Salir al mismo canal y pulsar AZUL de nuevo SIN reiniciar la aplicación.
5. En MENÚ / INFO puede verse y compartirse el diagnóstico; abrir TV TCL original solo permite comparar.

Paquete independiente: com.ayanabyte.o.lovestv.lab.
Compilación: rama o-lovestv-lab, GitHub Actions, APK de depuración ARM32.
Para el registro: adb logcat -d -v time -s OLoveTv:I '*:S'

La firma de depuración puede variar en compilaciones distintas. Si una actualización no se instala, desinstalar solamente esta aplicación de prueba antes de instalar la APK nueva.

## Mando TCL, versión 0.1.3

En modo TDT, OK, flecha ABAJO y ATRÁS abren SIEMPRE el menú con Diagnóstico como primera opción. No hace falta tecla MENÚ ni INFO. Cuando HbbTV falla, se muestra automáticamente un diálogo grande y navegable con «DIAGNÓSTICO», «COMPARTIR» y «CERRAR». El diagnóstico dispone de «COPIAR».

Si LOVEStv llega a abrirse, las flechas y OK se entregan a HbbTV, y ATRÁS sigue siendo la tecla de navegación/salida del servicio. El identificador de laboratorio de esta compilación es `com.ayanabyte.o.lovestv.lab4` para evitar conflictos de firma y distinguirla de las versiones anteriores. Sigue siendo una prueba experimental; no presupone que el motor TCL autorice el arranque.

## Exportar diagnóstico: 0.1.4

Mientras ves la TDT, OK / flecha ABAJO / ATRÁS abre el menú. Elige «GUARDAR diagnóstico .TXT en Descargas» o pulsa «GUARDAR .TXT» en el diálogo de diagnóstico. Android 11 guarda a través de MediaStore en `/sdcard/Download/O-LOVEStv-diagnostico-YYYYMMDD_HHMMSS.txt` sin solicitar permisos de almacenamiento. También se puede compartir. En sistemas anteriores a Android 10 se guarda en la carpeta externa específica de la aplicación. Este experimento utiliza `com.ayanabyte.o.lovestv.lab5`; el cambio no supone que HbbTV ya funcione.

## TVSDK 0.1.5 experimental

O! 0.1.5 registra su propio proceso ante TTvFunctionManager antes de inicializar el navegador HbbTV. Verifica la huella SHA-256 de la APK TCL G07/V138 instalada y la huella de su cadena firmada; no exporta el contenido ni lo empaqueta. Si la versión TCL cambia, aborta el registro y lo explica. El valor devuelto por systemAuthorized se registra en el diagnóstico como verdadero/falso, nunca la credencial. No se modifica el firmware, los certificados originales ni la TV TCL. Una autorización aceptada no demuestra por sí sola que DVB/AIT funcione.

El menú OK permite «Reintentar registro TVSDK». En «Ver diagnóstico TVSDK / HbbTV» se indica el estado de autorización y del navegador. Se puede guardar .txt en Descargas. Paquete independiente `com.ayanabyte.o.lovestv.lab6`. No actualiza ni reemplaza otras apps.

## O! LOVEStv Lab 0.1.6 — prueba diferencial del proceso

La APK antigua funcional 1.44 declara la actividad HbbTV de prueba con `android:process="com.tcl.tv"` mientras conserva su propio paquete y UID. Esta compilación aplica esa misma declaración únicamente a la actividad de laboratorio O! para determinar si explica el `systemAuthorized=false` de 0.1.5. El gestor Android conserva la identidad del paquete `com.ayanabyte.o.lovestv.lab7` y su UID: un nombre de proceso coincidente no equivale a instalar la aplicación del fabricante. Se registra el nombre de proceso real de `/proc/self/cmdline`, el UID y el booleano de autorización. También se pasa el contexto de Activity a `TTvFunctionManager.getInstance`, como en el código anterior. Se aborta el inicio HbbTV si TVSDK no confirma la autorización. El informe exportable no incluye el valor firmado. Sigue siendo una prueba sin validar en la TV.

## O! 0.1.7: diagnóstico de cierre inesperado

Mantiene el nombre de proceso de actividad `com.tcl.tv` que en 0.1.6 consiguió avanzar hasta «HbbTV preparado» (observación en TCL; confirmación de TVSDK aún por registrar). Añade registro persistente interno sin copiar autorización, guarda cada fase con sincronización a disco, y muestra el último registro recuperado en la siguiente apertura tras cualquier cierre abrupto. En Android 11 consulta `ApplicationExitInfo` para identificar códigos Java crash/native crash/signal/kill cuando Android los expone. Una vez reabierta la app, OK → Guardar diagnóstico TXT en Descargas incluye tanto la sesión actual como la anterior. Pruebas temporizadas +1/+5/+15 segundos al preparar HbbTV y más registros sobre ciclo de vida Android. Evita destruir `HbbtvServer` desde `onStop` porque puede activarse durante transiciones normales. Paquete nuevo `com.ayanabyte.o.lovestv.lab8`; no actualiza la aplicación anterior ni la rama principal.

## O! 0.1.8: native pre-initialization, certificate bridge, browser canvas

The diagnostic from 0.1.7 confirmed that TVSDK returns `systemAuthorized=true` with a separate UID, that `HbbtvServer.start` returns 0 and that the process then dies from SIGSEGV (reason=5, status=11). This variant retains working process identity and adds the missing initialization used by the old functional APK: verified native certificate/section bridge, vendor user-agent bridge and canvas preparation **before** `HbbtvServer.start`.

ARM32 bridge libraries were extracted from the user's supplied functional APK, stripped of debug info and obsolete package JNI names, then verified by SHA256; their source bytes are stored as compressed base64 with an extraction step in GitHub Actions. The operating code checks SHA-256 of the loaded V138 HbbTV and SITA libraries before calling native methods. No firmware mutation, no vendor signing keys, and no unverified native addresses are used. The native bridge only modifies memory inside the current O! app process.

Crash guard: a marker persists while testing the bridge. If the process crashes before 20 seconds, the next app launch automatically skips those native modifications and the diagnostic reports recovery mode. From the menu, **Rearmar puentes nativos** clears the marker for another launch. Exporting a TXT includes the previous crashed session.

**This version remains an experimental test, not a confirmed fix.** Package `com.ayanabyte.o.lovestv.lab9`; old apps and main branch unchanged.

## 0.1.9 — aislamiento de cierre nativo

En 0.1.8 TVSDK autorizó O! y la WebView TCL se creó, pero el proceso sufrió SIGSEGV (Android ApplicationExitInfo: reason=5, status=11) poco después de HbbtvServer.start. Puentes nativos omitidos por modo recuperación en la sesión documentada; por tanto no se puede atribuir la caída a su ejecución. El estado seguía EN_TCL_HBBTV_NO_EXIST, sin AIT.

0.1.9 separa dos fases: TVSDK/HbbtvServer.init/Tbrowser.init (sin llamar HbbtvServer.start), y activación explícita del servidor con el botón AZUL. En la primera fase registra tres latidos sin invocar JNI (+3, +8, +15s). En la segunda registra el instante de start y deja un marcador persistente hasta sobrevivir 15 segundos. Los eventos HbbTV se registran con argumentos limitados. La preparación de puentes nativos es experimental y está desactivada por defecto, sin alterar la aplicación antigua ni firmware TCL.

**Prueba:** abre Antena 3, espera diez segundos sin pulsar azul, después pulsa AZUL. Si se cierra, vuelve a abrir la app y guarda el diagnóstico TXT: incluirá el registro anterior y el marcador de la fase donde ocurrió el cierre.
