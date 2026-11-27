# O! · LiveTv — historial de pruebas

Fecha de registro: 2026-11-27.

Este documento registra las pruebas realizadas en la conversación y sus resultados comunicados por el usuario. Las APK 1.0–1.4 se prepararon fuera de GitHub; este registro no equivale a disponer de sus fuentes completas, una compilación reproducible ni una validación independiente de los binarios.

## Versiones

| Versión | Cambios comunicados | Estado de prueba |
| --- | --- | --- |
| 1.0 (Proyecto Limpio) | APK base proporcionada por el usuario, interfaz O! | Usuario confirma que LOVEStv funciona. |
| 1.1 | Limpieza parcial de referencias de marca; paquete es.tdt.lovestv.o; conservación del motor existente | Usuario confirma funcionamiento. Quedan referencias heredadas internas según revisión comunicada. |
| 1.2 | Arranque TDT, preparación automática de HbbTV, último canal y control por mando | Captura del usuario confirma que todavía aparece el menú de diagnóstico inicial. |
| 1.3 | Menú inicial simplificado con botón LiveTv | Usuario confirma mejora; informa que CH+ y CH− no cambian de canal. |
| 1.4 | Corrección propuesta para CH+/CH− y resintonización dentro del reproductor | Pendiente de confirmación en televisión. |

## Criterios de continuidad

- Mantener el proyecto de pruebas separado de O! principal hasta validar los cambios.
- Preservar el funcionamiento de TDT y LOVEStv ya confirmado.
- LiveTv debe sintonizar y preparar el servicio automáticamente; la apertura de LOVEStv corresponde al botón azul.
- Documentar cada cambio de versión, resultado de compilación, resultado en el televisor y regresiones por separado.
- No dar por superada la prueba de CH+/CH− hasta confirmación del usuario.
- La limpieza total de referencias heredadas sigue pendiente.
- Pendiente incorporar al control de versiones las fuentes propias autorizadas y los cambios exactos de las versiones locales, de forma que cada compilación pueda reproducirse. No incluir credenciales, claves privadas o componentes propietarios sin permiso de redistribución.

## Antecedentes en GitHub

- Rama `tcl-native-lab`: pruebas anteriores de inicialización y registro TVSDK; no confundir con LiveTv 1.x.
- Rama `build-uploaded-lovestv`: herramientas de compilación utilizadas para los proyectos entregados localmente.
- El repositorio principal O! permanece separado.

## 1.6 — corrección experimental de amarillo y carga de contenido (2026-11-27)

- Incidencias comunicadas en 1.4: amarillo no responde y contenido bajo demanda queda cargando.
- La versión intermedia local 1.5 añade ruta directa para Android KEYCODE_PROG_YELLOW (185) y scan code 400; usa el mapa del SDK y un valor TCL de reserva 114. No se ha validado físicamente.
- 1.6 registra el rechazo de la tecla por el motor y permite el despacho normal de Android si no la acepta. No se modifican las bibliotecas nativas.
- Se desactiva temporalmente la aplicación de la personalización de portada GroupLayout41 para aislar la regresión. Se amplían las muestras de diagnóstico a 15 y 30 segundos y los campos mediaErrorCode, networkState, seeking y ended. No se ha establecido la causa de la carga infinita ni se garantiza su resolución.
- El botón verde queda sin cambios en esta revisión.
- APK ensamblada localmente, paquete es.tdt.lovestv.o, versionCode 16, versionName 1.6. Verificación apksigner v2/v3 correcta; certificado coincide con 1.4. SHA-256 del APK: d0a7961228a8521b95dc83fe2b1d315b593a864bcf1c8dcdc85d644709c28b72. La comparación ZIP con 1.4 confirma que, fuera de firmas, solo se modifican AndroidManifest.xml y classes.dex.
- Pendiente prueba física de amarillo y reproducción; fuentes completas de APK local aún no publicadas.
