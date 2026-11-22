# Pruebas de HbbTV O!

## Etapa 1 · TDT

Comprobar `READ_TV_LISTINGS`, canales reales, `TvView.tune`, `onVideoAvailable` y el ID de entrada TIF. Una respuesta satisfactoria de `TvView` no implica funcionamiento de HbbTV.

## Etapa 2 · Motor TCL

Verificar presencia de clases TCL, `HbbtvServer.init`, callback de `Tbrowser.initTbrowser`, llamada `HbbtvServer.start`, registro de eventos HbbTV y aceptación de Azul. Guardar logcat completo para comparar con `com.tcl.tv`.

## Etapa 3 · Señalización DVB

Analizar logs AIT y PMT cuando el motor oficial activa una aplicación interactiva y compararlos con una sesión TIF de HbbTV O!. Distinguir la tabla BAT (`table_id=0x4A`) de la AIT (`table_id=0x74`). No interpretar `key_set=0` como prueba definitiva de falta de un permiso concreto.

## Etapa 4 · Integración completa

Investigar si TCL ofrece API de asociación de canal/AIT y un contrato de autorización para clientes externos. Validar apertura, navegación con mando, salida al canal y reinicio de HbbTV.
