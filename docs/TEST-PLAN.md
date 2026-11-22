# Pruebas de HbbTV O!

## Etapa 1 · TDT

Comprobar `READ_TV_LISTINGS`, canales reales, `TvView.tune`, `onVideoAvailable` y el ID de entrada TIF. Una respuesta satisfactoria de `TvView` no implica funcionamiento de HbbTV.

## Etapa 2 · Motor TCL

Validar el flujo de reproducción HbbTV en el televisor de referencia:

1. Instalar el APK y conceder acceso a la lista de canales.
2. Confirmar que se sintoniza un canal DVB-T/T2 y aparece vídeo.
3. Pulsar **Abrir LOVEStv** y comprobar en pantalla si se abre la aplicación emitida por el canal.
4. Probar el botón azul físico y el control **Azul** de la interfaz.
5. Revisar el estado en la interfaz y los mensajes `HbbTVO` de Logcat.
6. Cambiar de canal y confirmar que la sesión de vídeo no se interrumpe al inicializar el navegador.

El estado `HbbtvServer.start()` aceptado y el envío de la tecla azul no demuestran por sí solos que LOVEStv haya aparecido. Registrar el resultado visual y el modelo/firmware del televisor.

## Etapa 3 · Señalización DVB

Analizar logs AIT y PMT cuando el motor oficial activa una aplicación interactiva y compararlos con una sesión TIF de HbbTV O!. Distinguir la tabla BAT (`table_id=0x4A`) de la AIT (`table_id=0x74`). No interpretar `key_set=0` como prueba definitiva de falta de un permiso concreto.

## Etapa 4 · Integración completa

Investigar si TCL ofrece API de asociación de canal/AIT y un contrato de autorización para clientes externos. Validar apertura, navegación con mando, salida al canal y reinicio de HbbTV.
