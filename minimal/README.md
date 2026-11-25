# O! LOVEStv mínimo · fuente 5.3.17
Esta APK NO incluye la aplicación completa.
Únicamente compila dos pantallas nuevas y cinco clases extraídas de la integración HbbTV 5.3.17 (TV + Messenger + SurfaceControlViewHost), más la autorización TVSDK y dos puentes ARM32 O! existentes.

Arranque automático tras TvView.onVideoAvailable; AZUL se envía al motor de Tcl, no muestra un menú falso.
No incorpora EPG, IPTV, radio, VOD, grabaciones ni interfaces antiguas.
Paquete independiente com.ayanabyte.o.lovestv.minimal5317.
Las clases extraídas del código aportado se generan en CI desde el ZIP overlay verificado y solo se reempaquetan a nombres O!. Se mantienen atribuciones de la fuente original en la documentación.
El APK no está confirmado funcional en la TV TCL hasta probarlo.

## 0.0.2 — TDT vs HbbTV, prueba diferencial

El registro del usuario (25 nov 2026) muestra `ApplicationExitInfo motivo=2, status=9` (SIGKILL), sin traza Java/nativa, y el motor registra que SurfaceControlViewHost se adjunta cuando `video=false`. La ausencia de `onVideoAvailable` y el reinicio de PID hacen que no sepamos si está fallando el sintonizador, la integración HbbTV o una intervención externa del sistema. SIGKILL también puede ser causado por presión de memoria en dispositivos sin notificación específica.

La primera opción usa **solo TvView**, sin instanciar HbbTvPlayerView, sin binding HbbTV, sin Messenger ni HbbtvServer, pero en el **mismo proceso com.tcl.tv**. La segunda conserva el motor original extraído de 5.3.17 para comparar. Ambas guardan los eventos de tune, callbacks del sintonizador y ciclo de vida en el mismo diario. El monitor permanece en proceso distinto. No existe una barra artificial simulando LOVEStv.

Comparar: 1) prueba SOLO TDT, esperar ≥15 s y guardar TXT; 2) prueba TDT+LOVEStv, esperar, guardar TXT, y si se cierra revisar la línea anterior a la salida.

## 0.0.3 — esperar vídeo antes de iniciar HbbTV

El usuario ha probado ambos modos de 0.0.2. TDT solo: TvView.onVideoAvailable confirmado y sesión estable al salir voluntariamente. Con HbbTV: `HbbTvRuntimeService.onCreate` y SurfaceControlViewHost se activan mientras `videoReady=false`, antes de cualquier onVideoAvailable, seguido por salida SIGKILL (status 9) y nuevo PID. La causa de SIGKILL no está confirmada (no hay tombstone), pero el arranque previo al vídeo es observable y corregible.

De la clase original 5.3.17 `HbbTvPlayerView` se cambian únicamente las condiciones de **visibilidad de SurfaceView** y de **bindService**: no se inicia el servicio ni se crea la superficie remota hasta que la misma TvView confirme vídeo. Al llegar `onVideoAvailable`, se habilita la superficie y se conecta el Messenger/SurfaceControlViewHost sin un segundo TvView y sin pulsar AZUL. Añadidos registros precisos de fase. No hay cambios a las clases de LOVEStv, HbbtvServer y navegación del emisor.

**Probar:** opción 1 SOLO TDT de control; opción 2 TDT+LOVEStv esperando vídeo. Enviar TXT incluso si vuelve a cerrar. El resultado de esta compilación no confirma funcionamiento en TCL.

## 0.0.4 — diferenciar superficie HbbTV y registro TVSDK

En 0.0.3, Antena 3 devolvió onVideoAvailable antes de enlazar el servicio, y el SurfaceControlViewHost se adjuntó con video=true. El proceso murió (SIGKILL, exit status 9) durante el comienzo de TVSDK, tras comprobar nombre de proceso, sin retorno de systemAuthorized. La causa sigue incierta.

Se añade un experimento separado de **HbbTV SIN TVSDK**. En este modo se conserva la superficie remota del navegador y su protocolo Messenger, pero se detiene la inicialización antes de leer el firmware/autorización del fabricante y antes de ejecutar HbbtvServer.init. El experimento **CON TVSDK** añade trazas antes/después de cada fase: PackageManager, SHA256 de APK, extracción de autorización sin registrar el valor, carga de TTvFunctionManager, getInstance y systemAuthorized. La app Solo TDT sigue intacta. Enviar registros separados de ambos modos.

## 0.0.5 — diagnosticar el vídeo negro por capa superpuesta

El archivo 0.0.4 registra onVideoAvailable y SurfaceControlViewHost con video=true para ambos modos HbbTV, pero el usuario informa que no ve Antena 3. En HbbTvPlayerView, un SurfaceView TRANSLUCENT está configurado con ZOrderMediaOverlay(true) por encima de TvView y recibe un SurfacePackage remoto aun sin página HbbTV. En Android 11, una capa opaca puede ocultar el canal; no está probado que sea la única causa.

Ahora se inicializa la superficie de HbbTV con alpha=0, manteniendo la conexión de Messenger sin suprimir el motor. Mediante INFO/MENU se puede alternar alpha entre 0 y 1 (no usar alpha fraccionario en Android 11), registrando el valor, surfaceReady, attached y started. De este modo se puede probar si la superficie es la causa de pantalla negra. El diagnóstico del registro TVSDK sigue intacto y la rama principal no se modifica.

Prueba principal: opción 2 SIN TVSDK; comprobar si ahora se ve Antena 3. Abrir INFO, mostrar la capa, comprobar si oscurece el vídeo; volver a INFO y ocultarla. Después probar opción 3 si procede. Guardar TXT.
