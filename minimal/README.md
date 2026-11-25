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
