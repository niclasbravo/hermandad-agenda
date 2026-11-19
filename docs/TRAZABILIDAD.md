# Trazabilidad

RF → caso de uso → clases → endpoint → pantalla → prueba. Las rutas de clases son relativas a `backend/src/main/java/cl/udd/hermandad/agenda/`; las pantallas, a `frontend/src/`.

| RF | CU | Clases principales | Endpoint | Pantalla (ruta · componente) | Prueba |
|---|---|---|---|---|---|
| RF-01 Policlínicos y box | CU-01 | `catalogo/dominio/Policlinico`, `Box`, `catalogo/aplicacion/CatalogoService` | `GET/POST /api/policlinicos`, `PUT /api/policlinicos/{id}`, `POST /api/policlinicos/{id}/box`, `PUT /api/box/{id}` | `/catalogo` · `panel/catalogo/Catalogo.tsx` | PENDIENTE |
| RF-02 Profesionales N:M | CU-01 | `Profesional`, `CatalogoService.registrarProfesional` | `GET/POST /api/profesionales`, `PUT /api/profesionales/{id}`, `POST …/desactivar` | `/catalogo` (modal Registrar profesional) | PENDIENTE |
| RF-03 Credencial | CU-01 | `Credencial` (embebida) | idem RF-02 | `/catalogo` | PENDIENTE |
| RF-04 Bloques semanales | CU-02 | `agenda/dominio/BloqueHorario`, `AgendaService.crearBloque/modificarBloque` | `POST /api/bloques`, `PUT /api/bloques/{id}`, `POST …/desactivar`, `POST …/resolver-afectadas` | `/agenda-cupos` · `panel/agenda-cupos/AgendaCupos.tsx` | PENDIENTE |
| RF-05 Duración por profesión | CU-01 | `Profesion.definirDuracion` | `PUT /api/profesiones/{id}/duracion` | `/catalogo` (modal Modificar duración) | PENDIENTE |
| RF-06 Generar cupos | CU-02 | `BloqueHorario.generarCupos/horasDelDia`, `AgendaService.previsualizar` | `POST /api/bloques/previsualizar` | `/agenda-cupos` (panel Nuevo bloque) | PENDIENTE |
| RF-07 Bloquear cupos | CU-02 | `Bloqueo`, `AgendaService.bloquearCupos` | `POST /api/bloqueos` | `/agenda-cupos` (modal Bloquear) | PENDIENTE |
| RF-08 Publicación gradual | CU-02 | `Cupo.estado/estaPublicado`, `Profesional.configurarPublicacion` | `PUT /api/profesionales/{id}/publicacion` | `/agenda-cupos` (modal Publicación y reglas) | PENDIENTE |
| RF-09 Buscar paciente | CU-03 | `pacientes/aplicacion/PacienteService.buscar`, `DocumentoIdentidad.normalizar` | `GET /api/pacientes?documento=` | `/pacientes` · `panel/pacientes/BuscarPaciente.tsx` | PENDIENTE |
| RF-10 Registrar paciente | CU-03 | `Paciente`, `PacienteService.registrar` | `POST /api/pacientes` | `/pacientes/nuevo` · `FormularioPaciente.tsx` | PENDIENTE |
| RF-11 Actualizar paciente | CU-03 | `PacienteService.actualizar` | `PUT /api/pacientes/{id}` | `/pacientes/:id/editar` | PENDIENTE |
| RF-12 Adulto responsable | CU-03 | `AdultoResponsable`, `Paciente.actualizarDatos` (RN-05) | `POST/PUT /api/pacientes` | `CamposPaciente.tsx` | PENDIENTE |
| RF-13 Verificación FONASA/CESFAM | CU-03 | `VerificacionRequisitos.calcularResultado`, `PacienteService.registrarVerificacion` | `POST /api/pacientes/{id}/verificaciones` | `/pacientes/:id` · `FichaPaciente.tsx` | PENDIENTE |
| RF-14 Cupos disponibles | CU-04 | `ICupoRepository.buscarDisponibles`, `AgendamientoService.consultarCupos` | `GET /api/cupos/disponibles` | `/reservar-cita` · `panel/reservar/ReservarCita.tsx` | PENDIENTE |
| RF-15 Reservar | CU-04 | `Cita.nueva`, `AgendamientoService.reservar`, `CupoRepositorySQL.obtenerParaReserva` | `POST /api/citas` | `/reservar-cita`, panel lateral en `/agenda-dia` | PENDIENTE |
| RF-16 Serie de sesiones | CU-04 | `SerieSesiones`, `AgendamientoService.previsualizarSerie/reservarSerie` | `POST /api/citas/serie/previsualizar`, `POST /api/citas/serie` | `/reservar-cita` | PENDIENTE |
| RF-17 Anular | CU-04 | `Cita.anular`, `AgendamientoService.anular` | `POST /api/citas/{id}/anular` | `/agenda-dia` (modal Anular) | PENDIENTE |
| RF-18 Agenda del día | CU-06 | `asistencia/dominio/AgendaDelDia`, `ResumenDia`, `AsistenciaService` | `GET /api/agenda-dia` | `/agenda-dia` · `panel/agenda-dia/AgendaDia.tsx` | PENDIENTE |
| RF-19 Confirmación automática | CU-05 | `NotificacionService.programarConfirmacion/enviarPendientes`, `ProgramadorTareas` | (programador) | `/simulador-whatsapp` | PENDIENTE |
| RF-20 Contenido del mensaje | CU-05 | `RedactorMensajes` | — | `/simulador-whatsapp` | PENDIENTE |
| RF-21 Recordatorio el día antes | CU-05 | `ReglaRecordatorio`, `NotificacionService.programarRecordatorios` | — | `/simulador-whatsapp` | PENDIENTE |
| RF-22 Registrar asistencia | CU-06 | `Cita.registrarAsistencia`, `AsistenciaService.registrarAsistencia` | `POST /api/citas/{id}/asistencia` | `/agenda-dia` | PENDIENTE |
| RF-23 Historial de citas | CU-03 | `AgendamientoService.historial`, `CitaDtos.estadoTexto` | `GET /api/pacientes/{id}/historial` | `/pacientes/:id` | PENDIENTE |
| RF-24 Inasistencia reiterada | CU-07 | `ListadoInasistencia`, `ReporteService.inasistenciaReiterada` | `GET /api/reportes/inasistencia-reiterada` | `/reportes` · `panel/reportes/Reportes.tsx` | PENDIENTE |
| RF-25 Lista de espera | CU-04 | `InscripcionEspera`, `ListaEsperaService.inscribir` | `GET/POST /api/lista-espera` | `/lista-espera` · `panel/ListaEspera.tsx`, `/reservar-cita` | PENDIENTE |
| RF-26 Aviso de cupo liberado | CU-04 | `AvisoCupoLiberado`, `ListaEsperaService.avisarCupoLiberado/avisosPendientes` | `GET /api/avisos-cupo`, `POST …/atender` | `/agenda-dia` (fila amarilla) | PENDIENTE |
| RF-27 Ocupación | CU-07 | `ReporteOcupacion`, `ConsultaReportesSQL.ocupacion` | `GET /api/reportes/ocupacion` | `/reportes` | PENDIENTE |
| RF-28 Asistencia | CU-07 | `ReporteAsistencia` | `GET /api/reportes/asistencia` | `/reportes` | PENDIENTE |
| RF-29 Demanda | CU-07 | `ReporteDemanda` | `GET /api/reportes/demanda` | `/reportes` | PENDIENTE |
| RF-30 Caracterización | CU-07 | `ReporteCaracterizacion`, `CategoriaEtaria` | `GET /api/reportes/caracterizacion`, `GET/PUT /api/categorias-etarias` | `/reportes` | PENDIENTE |
| RF-33 «No podré asistir» anula | CU-05 | `NotificacionService.procesarRespuesta`, `SolicitudAnulacion`, `AgendamientoService.alSolicitarAnulacion` | `POST /webhooks/whatsapp`, `POST /api/dev/simulador/respuesta` | `/simulador-whatsapp` | PENDIENTE |
| RF-34 Reserva en línea | CU-08 | `ReservaEnLineaService.reservar`, `AgendamientoService.reservarEnLinea` | `GET /api/publico/cupos`, `POST /api/publico/reservas` | `/reservar` · `reserva-en-linea/ReservaEnLinea.tsx` | PENDIENTE |
| RF-35 Identificación en línea | CU-08 | `ReservaEnLineaService.identificar`, `LimiteIntentos` | `POST /api/publico/identificacion` | `/reservar` (paso 1) | PENDIENTE |
| RF-36 Registro en línea | CU-08 | `ReservaEnLineaService.registrar` | `POST /api/publico/pacientes` | `/reservar` (datos del paciente) | PENDIENTE |
| RF-37 % web y tope | CU-02 | `ReglaReservaWeb`, `AgendaService.recalcularCuposWeb`, `ParametroService` | `GET/PUT /api/parametros` | `/agenda-cupos` (Publicación y reglas) | PENDIENTE |
| RF-38 Refuerzos y reenvío | CU-05 | `ReglaRecordatorio.momentosRefuerzo`, `NotificacionService.reenviarRecordatorio` | `POST /api/citas/{id}/reenviar-recordatorio` | `/agenda-dia` (modal Gestionar confirmación) | PENDIENTE |
| RF-39 Resultado de llamada | CU-05 | `LlamadaConfirmacion`, `NotificacionService.registrarLlamada` | `POST /api/citas/{id}/llamadas` | `/agenda-dia` (modal Gestionar confirmación) | PENDIENTE |
| RF-40 Vista por profesional o box | CU-02, CU-06 | `AgendaService.agendaPorBox`, `VistaAgenda` | `GET /api/agenda/semana-box`, `GET /api/agenda-dia?vista=` | `/agenda-cupos` (Por box), `/agenda-dia` (Por box) | PENDIENTE |
| RF-41 Certificados | CU-08 | `DocumentoAdjunto`, `DocumentoService` (magic bytes), `IAlmacenArchivos`, `AlmacenArchivosLocal` | `POST /api/publico/reservas/{id}/certificados`, `GET /api/documentos/{id}/archivo` | `/reservar` (certificados), `/agenda-dia` (modal Verificar reserva en línea) | PENDIENTE |
| RF-42 Adelantar con motivo | CU-04 | `InscripcionEspera.adelantar`, `MovimientoEspera` | `POST /api/lista-espera/{id}/adelantar` | `/lista-espera` (modal Adelantar) | PENDIENTE |
| RF-43 Retirar con motivo | CU-04 | `InscripcionEspera.retirar`, `MotivoRetiro` | `POST /api/lista-espera/{id}/retirar` | `/lista-espera` (modal Quitar) | PENDIENTE |

## Requisitos no funcionales

| RNF | Cómo se cumple | Prueba |
|---|---|---|
| RNF-01 Reserva en ≤ 2 min | Buscador de RUT en la cabecera, «Reservar cita» en la ficha, «Primera hora disponible», confirmación en un clic | PENDIENTE |
| RNF-02 Lenguaje simple | Textos del Figma; plantillas sin abreviaturas | Revisión manual |
| RNF-04 Perfiles | `SeguridadWebConfig`, `@PreAuthorize`, `Usuario.tienePermiso` | PENDIENTE |
| RNF-05 Auditoría | `AuditoriaService` | PENDIENTE |
| RNF-06 Sin datos clínicos | `RedactorMensajes` | PENDIENTE |
| RNF-07 Carga | Perfil `carga` (`DatosDemoSeeder`) e índices del esquema | Manual |
| RNF-09 Confirmación ≤ 5 min | `ProgramadorTareas` cada minuto | PENDIENTE |
| RNF-10/11 Disponibilidad y respaldos | `restart: unless-stopped`, `scripts/respaldo.sh`, `scripts/restaurar.sh` | Manual |
| RNF-12 Reportes al día | Consultas en vivo (`ConsultaReportesSQL`) | PENDIENTE |
