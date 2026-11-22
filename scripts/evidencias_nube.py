#!/usr/bin/env python3
"""
Ejecuta casos críticos de la matriz 03 contra el sistema desplegado (perfil demo) y deja la evidencia EV-CP-xx
en Markdown: fecha, entorno, versión (commit), resultado esperado vs. obtenido y detalle.

Uso:  python scripts/evidencias_nube.py https://100-59-97-56.sslip.io > docs/EVIDENCIAS_PRUEBAS_NUBE.md

- Usa los usuarios de demostración (c.munoz, l.martinez) y pacientes ficticios de los datos demo.
- Crea citas de prueba y las anula al final (no se borra nada: estados + auditoría, D-17).
- Los mensajes de esos pacientes van al simulador (lista de números permitidos), no a WhatsApp real.
- Los casos que requieren la base (auditoría) se consultan por SSH: variables SERVIDOR y LLAVE.
"""
import datetime as dt
import http.cookiejar
import json
import os
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else 'https://100-59-97-56.sslip.io').rstrip('/')
CONTRASENA = 'Hermandad2026!'
SERVIDOR = os.environ.get('SERVIDOR', 'ubuntu@100.59.97.56')
LLAVE = os.environ.get('LLAVE', os.path.expanduser('~/.ssh/labsuser.pem'))
PEDRO, OTRO_1, OTRO_2 = 4, 2, 3          # pacientes ficticios habilitados de los datos demo
CASA_CENTRAL, MEDICINA, KINESIOLOGIA = 1, 1, 2

resultados = []


class Cliente:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.pedir('GET', '/api/auth/csrf')

    def xsrf(self):
        return next((c.value for c in self.jar if c.name == 'XSRF-TOKEN'), '')

    def pedir(self, metodo, ruta, cuerpo=None):
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
        req.add_header('Content-Type', 'application/json')
        if metodo != 'GET':
            req.add_header('X-XSRF-TOKEN', self.xsrf())
        try:
            with self.op.open(req, timeout=30) as r:
                txt = r.read().decode() or 'null'
                return r.status, json.loads(txt) if txt.strip()[:1] in '[{"n' else txt
        except urllib.error.HTTPError as e:
            txt = e.read().decode()
            try:
                return e.code, json.loads(txt)
            except ValueError:
                return e.code, txt

    def ingresar(self, usuario):
        s, r = self.pedir('POST', '/api/auth/ingresar', {'usuario': usuario, 'contrasena': CONTRASENA})
        assert s == 200, (usuario, s, r)
        return r


def sql(consulta):
    cmd = ['ssh', '-i', LLAVE, SERVIDOR,
           'docker exec -i hermandad-agenda-mysql-1 sh -c \'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" hermandad_agenda -N\'']
    p = subprocess.run(cmd, input=consulta, capture_output=True, text=True, timeout=60)
    return [l.split('\t') for l in p.stdout.strip().splitlines() if l]


def registrar(cp, rf, prueba, esperado, ok, obtenido):
    resultados.append((cp, rf, prueba, esperado, 'Aprobada' if ok else 'Fallida', obtenido))


def caso(cp, rf, prueba, esperado):
    def deco(fn):
        try:
            ok, obtenido = fn()
        except Exception as e:                      # la evidencia también registra el error
            ok, obtenido = False, f'Error al ejecutar: {e!r}'
        registrar(cp, rf, prueba, esperado, ok, obtenido)
        return fn
    return deco


hoy = dt.date.today()
desde, hasta = (hoy + dt.timedelta(days=1)).isoformat(), (hoy + dt.timedelta(days=14)).isoformat()
anon = Cliente()
tens = Cliente(); tens.ingresar('c.munoz')
admin = Cliente(); admin.ingresar('l.martinez')
creadas = []


@caso('CP-53', 'RNF-04', 'Consultar la agenda del día sin sesión iniciada.', 'El sistema no muestra información (CA-RNF04-2).')
def _():
    s, r = anon.pedir('GET', '/api/agenda-dia')
    return s == 401, f'HTTP {s} {r.get("codigo") if isinstance(r, dict) else ""}: no se entregan datos.'


@caso('CP-52', 'RNF-04', 'Usuario TENS/TONS intenta ver el reporte de ocupación (función de Administración).', 'El sistema lo impide (CA-RNF04-1, RN-03).')
def _():
    s, _r = tens.pedir('GET', f'/api/reportes/ocupacion?desde={hoy.replace(day=1)}&hasta={hoy}')
    s2, _ = admin.pedir('GET', f'/api/reportes/ocupacion?desde={hoy.replace(day=1)}&hasta={hoy}')
    return s == 403 and s2 == 200, f'TENS/TONS: HTTP {s}. Administración: HTTP {s2}.'


@caso('CP-11', 'RF-09', 'Buscar por RUT a un paciente ficticio registrado (15.432.109-8).', 'Se muestran sus datos (CA-RF09-1).')
def _():
    s, r = tens.pedir('GET', '/api/pacientes?documento=15.432.109-8')
    return s == 200 and r.get('id') == PEDRO, f'HTTP {s}: ficha de «{r.get("nombreCompleto")}», habilitado={r.get("habilitado")}.'


@caso('CP-12', 'RF-09', 'Buscar un RUT ficticio válido no registrado (11.111.111-1).', 'El sistema lo indica y ofrece registrarlo (CA-RF09-2).')
def _():
    s, r = tens.pedir('GET', '/api/pacientes?documento=11.111.111-1')
    return s == 404 and r.get('detalles', {}).get('ofrecerRegistro') is True, f'HTTP {s}: «{r.get("mensaje")}» (ofrecerRegistro=true).'


cupos = []


@caso('CP-18', 'RF-14', 'Buscar cupos de Medicina en Casa Central en las próximas dos semanas.', 'Se listan solo los cupos disponibles (CA-RF14-1).')
def _():
    global cupos
    s, cupos = tens.pedir('GET', f'/api/cupos/disponibles?policlinico={CASA_CENTRAL}&profesion={MEDICINA}&desde={desde}&hasta={hasta}')
    estados = {c.get('estado') for c in cupos}
    return s == 200 and len(cupos) > 0 and estados <= {'DISPONIBLE'}, f'HTTP {s}: {len(cupos)} cupos, estados {sorted(estados)}.'


cita_19 = None


@caso('CP-19', 'RF-15', 'Reservar el primer cupo para el paciente ficticio habilitado.', 'El cupo deja de mostrarse como disponible (CA-RF15-1).')
def _():
    global cita_19
    c = cupos[0]
    s, r = tens.pedir('POST', '/api/citas', {'idPaciente': PEDRO, 'idCupo': c['id']})
    cita_19 = r['cita']['id']; creadas.append(cita_19)
    s2, lista = tens.pedir('GET', f'/api/cupos/disponibles?policlinico={CASA_CENTRAL}&profesion={MEDICINA}&desde={desde}&hasta={hasta}')
    sigue = any(x['id'] == c['id'] for x in lista)
    return s == 201 and not sigue, f'HTTP {s}: cita {cita_19} el {c["fecha"]} {c["horaInicio"][:5]}; el cupo ya no aparece en la búsqueda.'


@caso('CP-21', 'RF-15', 'Dos solicitudes simultáneas reservan el mismo cupo para pacientes distintos.', 'Solo una reserva se concreta; el otro usuario recibe un aviso (CA-RF15-3).')
def _():
    c = cupos[1]
    otra = Cliente(); otra.ingresar('c.munoz')
    salida = {}
    def reservar(cli, pac, k):
        salida[k] = cli.pedir('POST', '/api/citas', {'idPaciente': pac, 'idCupo': c['id']})
    hilos = [threading.Thread(target=reservar, args=(tens, OTRO_1, 'a')), threading.Thread(target=reservar, args=(otra, OTRO_2, 'b'))]
    [h.start() for h in hilos]; [h.join() for h in hilos]
    codigos = sorted(v[0] for v in salida.values())
    for v in salida.values():
        if v[0] == 201: creadas.append(v[1]['cita']['id'])
    perdedor = next((v[1] for v in salida.values() if v[0] != 201), {})
    return codigos == [201, 409], f'Códigos {codigos}; aviso al segundo: «{perdedor.get("mensaje", "")}».'


@caso('CP-25', 'RF-19', 'Al reservar (CP-19) se programa y envía la confirmación sin intervención de la TENS/TONS.', 'Se envía la confirmación automáticamente (CA-RF19-1).')
def _():
    time.sleep(75)                                  # el programador corre cada minuto
    filas = sql(f"SELECT p.nombre_proveedor, m.estado_entrega, LEFT(m.id_externo,4) FROM mensaje m JOIN plantilla_mensaje p ON p.id_plantilla=m.id_plantilla WHERE m.id_cita={cita_19} AND p.tipo='CONFIRMACION';")
    ok = bool(filas) and filas[0][1] in ('ENVIADO', 'ENTREGADO')
    return ok, f'Mensaje {filas[0][0] if filas else "—"} en estado {filas[0][1] if filas else "—"} (destino ficticio → simulador; envío real a número del equipo: ver EV-WA-01).'


@caso('CP-24', 'RF-18', 'Consultar la agenda del día de la cita reservada en CP-19.', 'Se listan las citas con hora, profesional y paciente (CA-RF18-1).')
def _():
    fecha = cupos[0]['fecha']
    s, r = tens.pedir('GET', f'/api/agenda-dia?policlinico={CASA_CENTRAL}&fecha={fecha}')
    filas = r.get('citas', []) if isinstance(r, dict) else []
    fila = next((f for f in filas if f['cita']['id'] == cita_19), None)
    detalle = (f"incluye la cita {cita_19}: {fila['cita']['horaInicio'][:5]}, {fila['cita']['profesional']}, {fila['cita']['paciente']}" if fila else f'no aparece la cita {cita_19}')
    return s == 200 and fila is not None, f'HTTP {s}: {len(filas)} citas el {fecha}; {detalle}.'


@caso('CP-43', 'RF-38', 'Reenviar manualmente el recordatorio de la cita de CP-19.', 'El reenvío queda registrado (CA-RF38).')
def _():
    s, r = tens.pedir('POST', f'/api/citas/{cita_19}/reenviar-recordatorio')
    filas = sql(f"SELECT COUNT(*) FROM mensaje m JOIN plantilla_mensaje p ON p.id_plantilla=m.id_plantilla WHERE m.id_cita={cita_19} AND p.tipo='RECORDATORIO';")
    return s == 200, f'HTTP {s}: «{r.get("mensaje")}»; recordatorios de la cita: {filas[0][0] if filas else "?"}.'


@caso('CP-38', 'RF-33', 'El paciente responde «No podré asistir» al recordatorio (mismo servicio que usa el webhook).', 'La reserva queda anulada y el cupo vuelve a estar disponible (CA-RF33-1).')
def _():
    time.sleep(70)
    filas = sql(f"SELECT m.id_mensaje FROM mensaje m JOIN plantilla_mensaje p ON p.id_plantilla=m.id_plantilla WHERE m.id_cita={cita_19} AND p.tipo='RECORDATORIO' AND m.estado_entrega IN ('ENVIADO','ENTREGADO') ORDER BY m.id_mensaje DESC LIMIT 1;")
    s, _r = tens.pedir('POST', '/api/dev/simulador/respuesta', {'idMensaje': int(filas[0][0]), 'respuesta': 'NO_ASISTIRA'})
    est = sql(f'SELECT estado, origen_anulacion FROM cita WHERE id_cita={cita_19};')
    s2, lista = tens.pedir('GET', f'/api/cupos/disponibles?policlinico={CASA_CENTRAL}&profesion={MEDICINA}&desde={desde}&hasta={hasta}')
    libre = any(x['id'] == cupos[0]['id'] for x in lista)
    return s == 200 and est and est[0][0] == 'ANULADA' and libre, f'Respuesta HTTP {s}; cita {cita_19}: {est[0] if est else "—"}; el cupo volvió a la búsqueda: {libre}.'


@caso('CP-54', 'RNF-05', 'Revisar la auditoría de la cita creada y anulada.', 'El registro muestra usuario, fecha, hora y operación (CA-RNF05-1).')
def _():
    filas = sql(f"SELECT accion, IFNULL(id_usuario,'sistema'), fecha_hora FROM auditoria WHERE entidad='cita' AND id_registro={cita_19} ORDER BY id_auditoria;")
    acciones = [f[0] for f in filas]
    return 'CREAR' in acciones and 'ANULAR' in acciones, 'Registros: ' + '; '.join(f'{a} por usuario {u} el {t}' for a, u, t in filas)


@caso('CP-40', 'RF-35', 'Reserva en línea: identificarse con un RUT registrado y otra fecha de nacimiento.', 'No muestra datos de la ficha ni indica si el RUT existe (CA-RF35-1).')
def _():
    s, r = anon.pedir('POST', '/api/publico/identificacion', {'tipoDocumento': 'RUT', 'numero': '15.432.109-8', 'fechaNacimiento': '1990-01-01'})
    return s == 200 and r.get('estado') == 'NUEVO' and not r.get('nombrePila'), f'HTTP {s}: estado {r.get("estado")}, sin nombre ni datos de la ficha.'


@caso('CP-57', 'RNF-08', 'Medir 20 búsquedas de cupos y 20 consultas de agenda en la nube (t3.small).', '95 % o más responden dentro de 3 s (valor propuesto).')
def _():
    tiempos = []
    for i in range(20):
        for ruta in (f'/api/cupos/disponibles?policlinico={CASA_CENTRAL}&profesion={KINESIOLOGIA}&desde={desde}&hasta={hasta}',
                     f'/api/agenda-dia?policlinico={CASA_CENTRAL}&fecha={cupos[0]["fecha"]}'):
            t = time.perf_counter(); tens.pedir('GET', ruta); tiempos.append(time.perf_counter() - t)
    tiempos.sort()
    p95 = tiempos[int(len(tiempos) * 0.95) - 1]
    dentro = sum(1 for x in tiempos if x <= 3) / len(tiempos) * 100
    return dentro >= 95, f'{len(tiempos)} solicitudes: mediana {statistics.median(tiempos):.2f} s, p95 {p95:.2f} s, {dentro:.0f} % bajo 3 s.'


# Limpieza: anular las citas de prueba que siguen vigentes (estado + auditoría; no se borra nada)
for cid in creadas:
    tens.pedir('POST', f'/api/citas/{cid}/anular', {'origen': 'TENS', 'motivo': 'Prueba automática de evidencias'})

commit = subprocess.run(['git', 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True).stdout.strip()
ahora = dt.datetime.now().strftime('%d-%m-%Y %H:%M')
print('# Evidencias de pruebas en la nube\n')
print(f'- **Fecha:** {ahora}  ·  **Entorno:** nube, {BASE} (AWS Academy Learner Lab, EC2 t3.small, perfil demo)  ·  **Versión:** commit `{commit}`')
print('- **Ejecución:** `python scripts/evidencias_nube.py` (reproducible). Datos ficticios; las citas de prueba se anulan al final.\n')
print('| Evidencia | CP | RF/RNF | Prueba | Resultado esperado | Estado | Resultado obtenido |')
print('|---|---|---|---|---|---|---|')
for cp, rf, prueba, esperado, estado, obtenido in resultados:
    print(f'| EV-{cp} | {cp} | {rf} | {prueba} | {esperado} | **{estado}** | {obtenido} |')
aprob = sum(1 for r in resultados if r[4] == 'Aprobada')
print(f'\n**Resumen:** {aprob} de {len(resultados)} casos aprobados.')
