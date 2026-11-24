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


## O! 0.2.0 — diagnóstico escalonado de SIGSEGV

El registro de 0.1.9 confirmó TVSDK autorizado y un SIGSEGV antes de pulsar AZUL y **antes** de HbbtvServer.start; el último evento previo era \`Tbrowser.onSuccess\` y el initWebView manual. Por ello la 0.2.0 aplica tres fases independientes, con marcadores sincronizados en disco antes de cada llamada al motor:

1. TVSDK + HbbtvServer.init + teclas/eventos, sin navegador. Se inicializa automáticamente; el usuario debe esperar 10 segundos.
2. Primer botón AZUL: Tbrowser.initTbrowser, **sin** llamar a initWebView manual ni HbbtvServer.start. Esperar otros 10 segundos.
3. Segundo botón AZUL: HbbtvServer.start. Esperar 15 segundos; un tercer AZUL envía la tecla al motor si sigue vivo.

El menú OK → GUARDAR diagnóstico TXT incluye fase actual y registro recuperado de cierre. El diagnóstico de ciclo de vida registra foco de ventana y onPause. Puentes nativos del antiguo software siguen desactivados por defecto. El firmware y la aplicación TCL originales permanecen intactos. El resultado confirma solo la fase que se haya alcanzado, no el funcionamiento de LOVEStv.

## O! 0.2.1 — recuperación tras cierre nativo y comparación con/sin HbbtvServer

El diagnóstico 0.2.0 confirmó que TVSDK devuelve true, HbbtvServer.init funciona, el callback de Tbrowser termina y **se produce SIGSEGV 1-2 segundos después sin llamar a HbbtvServer.start ni a initWebView manual**. Además la ventana se pausa y pierde el foco al mismo tiempo, sin demostrar causalidad.

Ahora una actividad de inicio segura `LauncherActivity` se ejecuta en el proceso normal del paquete; la actividad de laboratorio `MainActivity` continúa separada en el proceso TCL autorizado `com.tcl.tv`. El cierre de la segunda no debería matar la primera. La pantalla de inicio puede detectar el último `REASON_CRASH_NATIVE` mediante ApplicationExitInfo, leer el diario persistente del laboratorio y guardar un diagnóstico TXT incluso cuando el laboratorio se cierre. Intentará guardar automáticamente tras un cierre nativo observado, sin eliminar los botones de descarga manual.

Los dos modos de prueba son «Tbrowser SIN HbbtvServer.init» y «Tbrowser CON HbbtvServer.init». El primero omite también `HbbtvServer.start`, para comprobar si el navegador por sí solo provoca el cierre. En ambos se omite `initWebView` manual y permanecen desactivados por defecto los puentes nativos experimentales. Un botón AZUL inicia Tbrowser después de autorizar TVSDK. El modo combinado puede probar el servidor más adelante. Los certificados y el firmware TCL original permanecen intactos. La compilación no valida comportamiento en el televisor.

## O! 0.2.2: localizar el SIGSEGV del servidor TCL

El diagnóstico de 0.2.1 (2026-11-24, 02:28:43) confirmó que en modo «Tbrowser CON servidor» el proceso se cerró por SIGSEGV sin llegar a ejecutar Tbrowser.initTbrowser ni HbbtvServer.start. La última fase correcta fue HbbtvServer.init=1 y registro de eventos del fabricante. Por tanto, iniciar Tbrowser ya no es una condición necesaria para la caída.

En la 0.2.2 el monitor seguro proporciona cuatro experimentos mutuamente excluyentes: (1) HbbtvServer.init **SIN** preparar teclas ni registrar eventos y sin navegador; (2) HbbtvServer.getInstance sin init, sin eventos y sin navegador; (3) HbbtvServer.init CON registro de eventos pero sin navegador para reproducir el fallo; (4) Tbrowser SOLO, control ya estable. Cada experimento registra fases a disco y pulsos sin invocaciones JNI a +3/+8/+15/+25 s. El proceso monitor independiente exporta diagnóstico tras cierres nativos. No se activa HbbtvServer.start ni el menú real de LOVEStv en los experimentos del servidor. No se modifica TCL ni la rama principal.

Comenzar por experimento 1, esperar 15 segundos y guardar el diagnóstico. Si se cierra, el monitor conserva el registro y se puede probar el 2 para distinguir fallo de getInstance de fallo de init. Si el 1 permanece estable y el 3 falla, el foco pasará a registerHbbtvEventListener y preparación de teclas.

## 0.2.3 — aislar vídeo TDT del proceso HbbTV

Problema reportado: la app antigua contiene TvView y HbbtvServer en la misma actividad `MainActivity`, declarada con `android:process="com.tcl.tv"`. Al morir ese proceso por SIGSEGV, desaparece la imagen de TDT aunque el sintonizador funcione. Se evita compartir el proceso.

Se crea `SafeTvActivity` en el proceso normal de la app para reproducir Antena 3 con TvView. La nueva `IsolatedNativeProbeService` es **solo de laboratorio** y se ejecuta en un proceso `com.tcl.tv` distinto, sin poseer la vista de TDT; replica la verificación TVSDK y, a petición del usuario al pulsar AZUL, la fase `getInstance` (prueba 1) o `getInstance + init` (prueba 2), sin Tbrowser ni callbacks HbbTV. El servicio guarda `o-lovestv-isolated-native.log` sincronizado a disco antes y después de cada llamada. Si se cierra por SIGSEGV, `SafeTvActivity` continúa viva y puede detectar el cierre y guardar automáticamente un TXT, o hacerlo manualmente con OK. El monitor principal puede incluir el registro del servicio en su diagnóstico. Tras cerrar la pantalla TDT protegida se solicita cerrar el servicio y forzar un proceso de laboratorio nuevo para la siguiente prueba.

La separación de procesos evita que la muerte del **proceso de laboratorio** destruya directamente la pantalla de TV. No impide que una biblioteca del fabricante pueda interrumpir el propio sintonizador a nivel del sistema: si `onVideoUnavailable` o `onDisconnected` se producen, también se registran. Esta versión **no integra todavía el navegador ni el menú real de LOVEStv**. El firmware original y la rama principal permanecen intactos.


## O! 0.2.4 — prueba integral derivada del análisis del código aportado

La base de comparación fue el archivo de fuentes Channelplus 5.3.16 proporcionado por el usuario. La implementación de esa versión se reconstruye al superponer \`ci/channelplus-5.3.7-overlay.zip\` y aplicar los pasos ordenados de \`.github/workflows/channelplus-537-lovestv.yml\`. No basta con leer la copia base de \`HbbTvRuntimeService.java\` porque los cambios finales están en los pasos del workflow.

Hallazgos de la secuencia de arranque: autostart tras \`TvView.onVideoAvailable\` y canal TDT real (sin botón azul), mismo proceso 32-bit \`com.tcl.tv\` para Activity con TDT y motor vendor, \`Tbrowser.initTbrowser\` con contexto de la Activity real, raíz transparente **adjunta y visible** desde antes de la llamada al navegador, adaptación de certificados y de user agent tras cargar la clase HbbtvServer, \`HbbtvServer.init\`, registro de eventos nativos del emisor, \`Tbrowser\` callback y \`HbbtvServer.start\`. El indicador lateral debe venir de la AIT/HbbTV real y nunca se simula en O!.

Nueva actividad \`NativeBroadcastActivity\` construida de forma independiente bajo el paquete O! (sin reutilizar nombres ni paquetes de proyectos anteriores). Usa un \`TvView\`, contexto Activity real, root transparente visible adjunto, autostart sin pulsar tecla, conserva listener/eventos para evitar GC, y envía las teclas de colores a TVSDK solo **después** de que el motor indique que está preparado. Mantiene registro fsync por etapa y puede ser inspeccionada desde el monitor principal, que permanece fuera del proceso TCL. Los puentes de bibliotecas nativas reescritos para O! se aplican una única vez solo sobre los hashes V138 comprobados; después de posible cierre queda activado modo recuperación.

Se conserva la opción anterior de TDT protegida como diagnóstico aislado. El ensayo integral puede volver a cerrar el proceso del TV del laboratorio porque replica la arquitectura de Activity+TvView que utiliza el ejemplo funcional. No hay evidencia de LOVEStv funcionando en O! antes de probar esta versión físicamente. El árbol \`main\` se mantiene sin tocar.

## O! 0.2.5 — ventana TCL real y comparación con/sin puentes

La fuente de Channelplus 5.3.16 adjunta el root del navegador mediante `activityParent=(ViewGroup)playerActivity.getWindow().getDecorView()` y `activityParent.addView(root, ...)`, cuando la TDT está lista. La 0.2.4 añadió su root dentro de otro FrameLayout y lo dejó en alpha=0 hasta después de HbbtvServer.start. Esta versión monta root directamente bajo DecorView *tras onVideoAvailable* y lo mantiene visible con alpha=1 antes de TVSDK/HbbtvServer/Tbrowser, sin crear un segundo TvView.

Se añaden dos rutas en el monitor para comparar el inicio completo con preparación experimental ARM32 (adaptación del código del ZIP aportado) y sin ella. Cada llamada nativa guarda fases ARMADAS/DEVUELTAS en el archivo fsync antes y después de ejecutarla. El monitor independiente sigue permitiendo guardar el diario del último cierre.

**No se da por resuelto LOVEStv.** Los diagnósticos anteriores demostraron que HbbtvServer.init puede causar SIGSEGV aun sin navegador, así que la causa exacta no se puede atribuir a la vista. Para confirmar el fallo a nivel de función nativa es necesario recuperar `adb logcat -b crash` o un tombstone; ApplicationExitInfo del firmware no ha facilitado su contenido.
