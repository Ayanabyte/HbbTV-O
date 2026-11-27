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
