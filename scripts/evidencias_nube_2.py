#!/usr/bin/env python3
"""
Segunda tanda de evidencias de la matriz 03 contra el sistema desplegado (perfil demo).
Cubre los casos que no estaban en scripts/evidencias_nube.py: catálogo, agenda, pacientes, citas, lista de espera,
confirmaciones, asistencia, reportes y reserva en línea.

Uso:  python scripts/evidencias_nube_2.py https://100-59-97-56.sslip.io > docs/evidencias_nube_2.md

- Solo datos ficticios. Lo que crea queda identificado como «prueba de evidencias» y al final se anula o desactiva
  (citas anuladas, inscripciones retiradas, profesionales, bloques, box y policlínico desactivados). No se borra nada (D-17).
- Los parámetros que cambia (hora del recordatorio, refuerzos, % de cupos web) se restauran al final.
- Los mensajes de los pacientes ficticios van al simulador (lista de números permitidos), no a WhatsApp real.
- Las consultas a la base se hacen por SSH: variables SERVIDOR y LLAVE.
"""
import datetime as dt
import email.utils
import http.cookiejar
import json
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo

BASE = (sys.argv[1] if len(sys.argv) > 1 else 'https://100-59-97-56.sslip.io').rstrip('/')
CONTRASENA = 'Hermandad2026!'
SERVIDOR = os.environ.get('SERVIDOR', 'ubuntu@100.59.97.56')
LLAVE = os.environ.get('LLAVE', os.path.expanduser('~/.ssh/labsuser.pem'))
PEDRO, CASA_CENTRAL, MEDICINA, KINESIOLOGIA = 4, 1, 1, 2
ZONA = ZoneInfo('America/Santiago')
MARCA = 'Prueba de evidencias'

resultados = []
st = {}                       # estado compartido entre casos
limpieza = []                 # funciones de limpieza (se ejecutan al final, en orden inverso)


def log(*a):
    print(*a, file=sys.stderr, flush=True)


class Cliente:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.pedir('GET', '/api/auth/csrf')

    def xsrf(self):
        return next((c.value for c in self.jar if c.name == 'XSRF-TOKEN'), '')

    def pedir(self, metodo, ruta, cuerpo=None, cabeceras=None, crudo=None, tipo='application/json'):
        datos = crudo if crudo is not None else (json.dumps(cuerpo).encode() if cuerpo is not None else None)
        req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
        req.add_header('Content-Type', tipo)
        for k, v in (cabeceras or {}).items():
            req.add_header(k, v)
        if metodo != 'GET':
            req.add_header('X-XSRF-TOKEN', self.xsrf())
        try:
            with self.op.open(req, timeout=40) as r:
                self.ultima_fecha = r.headers.get('Date')
                txt = r.read().decode() or 'null'
                return r.status, (json.loads(txt) if txt.strip()[:1] in '[{"n' else txt)
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
    cmd = ['ssh', '-o', 'StrictHostKeyChecking=no', '-i', LLAVE, SERVIDOR,
           'docker exec -i hermandad-agenda-mysql-1 sh -c \'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" hermandad_agenda -N\'']
    p = subprocess.run(cmd, input=consulta, capture_output=True, text=True, timeout=90)
    return [l.split('\t') for l in p.stdout.strip().splitlines() if l]


def ahora_servidor():
    s, _ = anon.pedir('GET', '/api/auth/csrf')
    utc = email.utils.parsedate_to_datetime(anon.ultima_fecha)
    return utc.astimezone(ZONA).replace(tzinfo=None)


def caso(cp, rf, prueba, esperado):
    def deco(fn):
        log('·', cp, prueba[:70])
        try:
            r = fn()
            estado, obtenido = (('Aprobada' if r[0] else 'Fallida'), r[1]) if isinstance(r[0], bool) else r
        except Exception as e:
            estado, obtenido = 'Fallida', f'Error al ejecutar: {e!r}'
        resultados.append((cp, rf, prueba, esperado, estado, obtenido))
        log('   ->', estado, obtenido[:150])
        return fn
    return deco


def rut(n):
    s, m = 0, 2
    for d in reversed(str(n)):
        s += int(d) * m
        m = 2 if m == 7 else m + 1
    dv = 11 - s % 11
    dv = '0' if dv == 11 else 'K' if dv == 10 else str(dv)
    return f'{n:,}'.replace(',', '.') + '-' + dv


def rut_libre():
    while True:
        r = rut(random.randint(21_000_000, 24_999_999))
        s, _ = tens.pedir('GET', '/api/pacientes?documento=' + r)
        if s == 404:
            return r


def paciente(rut_txt, telefono, nacimiento='1980-05-10', adulto=None, nombres='Prueba'):
    return {'tipoDocumento': 'RUT', 'numeroDocumento': rut_txt, 'nombres': nombres, 'apellidos': 'Evidencias Ficticio',
            'fechaNacimiento': nacimiento, 'telefonoCelular': telefono, 'correo': None,
            'direccion': 'Calle Ficticia 123, Santiago', 'genero': 'Femenino', 'nacionalidad': 'Chilena', 'adulto': adulto}


def verificar(id_pac):
    return tens.pedir('POST', f'/api/pacientes/{id_pac}/verificaciones',
                      {'fechaVerificacion': hoy.isoformat(), 'cesfamInscripcion': 'CESFAM Ficticio Centro',
                       'fonasaVigente': True, 'inscritoCesfam': True, 'observacion': MARCA, 'idsDocumento': []})


def reservar(id_pac, id_cupo):
    s, r = tens.pedir('POST', '/api/citas', {'idPaciente': id_pac, 'idCupo': id_cupo})
    if s == 201:
        st.setdefault('citas', []).append(r['cita']['id'])
    return s, r


def disponibles(**q):
    qs = '&'.join(f'{k}={v}' for k, v in q.items())
    s, r = tens.pedir('GET', '/api/cupos/disponibles?' + qs)
    return r if s == 200 else []


def semana(profesional, desde):
    s, r = admin.pedir('GET', f'/api/agenda/semana?profesional={profesional}&desde={desde}')
    return r if s == 200 else []


def mensajes_de(cita, tipo=None):
    filtro = f" AND p.tipo='{tipo}'" if tipo else ''
    return sql(f"SELECT m.id_mensaje, p.tipo, m.estado_entrega, m.telefono_destino, m.enviado_por, IFNULL(m.id_usuario,''), "
               f"m.fecha_programada, IFNULL(m.fecha_envio,'') FROM mensaje m JOIN plantilla_mensaje p ON p.id_plantilla=m.id_plantilla "
               f"WHERE m.id_cita={cita}{filtro} ORDER BY m.id_mensaje;")


def esperar_mensaje(cita, tipo, estados=('ENVIADO', 'ENTREGADO', 'LEIDO', 'FALLIDO'), maximo=200):
    t0 = time.time()
    while time.time() - t0 < maximo:
        f = mensajes_de(cita, tipo)
        if f and f[-1][2] in estados:
            return f
        time.sleep(15)
    return mensajes_de(cita, tipo)


# ------------------------------------------------------------------ preparación
anon = Cliente()
tens = Cliente(); tens.ingresar('c.munoz')
admin = Cliente(); admin.ingresar('l.martinez')
ahora = ahora_servidor()
hoy = ahora.date()
log('Hora del servidor (Chile):', ahora)
_, PROFESIONES = admin.pedir('GET', '/api/profesiones')
PSICOLOGIA = next(p for p in PROFESIONES if p['nombre'].lower().startswith('psico'))
_, PARAMS = admin.pedir('GET', '/api/parametros')
PARAM = {p['clave']: p['valor'] for p in PARAMS}
log('Parámetros:', {k: PARAM.get(k) for k in ('RECORDATORIO_HORA', 'REFUERZOS_CANTIDAD', 'REFUERZOS_HORAS_ANTES', 'PORCENTAJE_CUPOS_WEB', 'UMBRAL_INASISTENCIA')})
_, FICHA_PEDRO = tens.pedir('GET', f'/api/pacientes/{PEDRO}')

# ------------------------------------------------------------------ M1 · catálogo


@caso('CP-01', 'RF-01', 'Registrar un policlínico ficticio con nombre, dirección y un box.', 'El policlínico y su box quedan seleccionables al definir bloques (CA-RF01-1).')
def _():
    s, r = admin.pedir('POST', '/api/policlinicos', {'nombre': 'Policlínico de prueba (evidencias)', 'direccion': 'Calle Ficticia 100',
                                                     'comuna': 'Santiago', 'boxes': ['Box prueba 1']})
    st['pol_prueba'] = r.get('id') if isinstance(r, dict) else None
    limpieza.append(lambda: admin.pedir('PUT', f'/api/policlinicos/{st["pol_prueba"]}', {'nombre': 'Policlínico de prueba (evidencias)',
                                        'direccion': 'Calle Ficticia 100', 'comuna': 'Santiago', 'activo': False}))
    _, lista = admin.pedir('GET', '/api/policlinicos')
    p = next((x for x in lista if x['id'] == st['pol_prueba']), None)
    boxes = [b['nombre'] for b in p['boxes']] if p else []
    return s == 201 and p is not None and boxes == ['Box prueba 1'], f'HTTP {s}: policlínico {st["pol_prueba"]} creado y listado con {boxes} para definir bloques (al final se desactivó).'


@caso('CP-02', 'RF-02', 'Registrar un profesional ficticio de Medicina en Casa Central.', 'Queda disponible para asignarle bloques y usa la duración de su profesión (CA-RF02-1).')
def _():
    s, r = admin.pedir('POST', '/api/profesionales', {'idProfesion': MEDICINA, 'nombres': 'Prueba', 'apellidos': 'Evidencias Medicina',
                                                      'esAlumnoPractica': False, 'tituloProfesional': 'Médico cirujano',
                                                      'casaEstudios': 'Universidad Ficticia', 'registroPrestador': '000001',
                                                      'idsPoliclinico': [CASA_CENTRAL]})
    st['med'] = r['id']
    limpieza.append(lambda: admin.pedir('POST', f'/api/profesionales/{st["med"]}/desactivar'))
    dur = next(p['duracionMin'] for p in PROFESIONES if p['id'] == MEDICINA)
    return s == 201 and r['duracionAtencion'] == dur, f'HTTP {s}: profesional {r["id"]} en {[x["nombre"] for x in r["policlinicos"]]}; duración de atención {r["duracionAtencion"]} min = Medicina ({dur} min).'


@caso('CP-03', 'RF-03', 'Consultar la credencial del profesional registrado en CP-02.', 'La ficha del profesional muestra los tres datos (CA-RF03-1).')
def _():
    s, r = admin.pedir('GET', f'/api/profesionales/{st["med"]}')
    c = r['credencial']
    ok = c['tituloProfesional'] == 'Médico cirujano' and c['casaEstudios'] == 'Universidad Ficticia' and c['registroPrestador'] == '000001'
    return ok, f'HTTP {s}: título «{c["tituloProfesional"]}», casa de estudios «{c["casaEstudios"]}», registro «{c["registroPrestador"]}».'


@caso('CP-06', 'RF-05', 'Configurar la duración de Psicología en 45 minutos.', 'La duración queda guardada y se usa en los bloques nuevos (CA-RF05-1).')
def _():
    original = (PSICOLOGIA['duracionMin'], PSICOLOGIA['duracionAlumnoMin'])
    s, r = admin.pedir('PUT', f'/api/profesiones/{PSICOLOGIA["id"]}/duracion', {'duracionMin': 45, 'duracionAlumnoMin': original[1]})
    if original[0] != 45:
        limpieza.append(lambda: admin.pedir('PUT', f'/api/profesiones/{PSICOLOGIA["id"]}/duracion', {'duracionMin': original[0], 'duracionAlumnoMin': original[1]}))
    _, lista = admin.pedir('GET', '/api/profesiones')
    guardada = next(p['duracionMin'] for p in lista if p['id'] == PSICOLOGIA['id'])
    return s == 200 and guardada == 45, f'HTTP {s}: Psicología quedó con {guardada} min (antes {original[0]} min); se usa en CP-08.'


s_box, r_box = admin.pedir('POST', f'/api/policlinicos/{CASA_CENTRAL}/box', {'nombre': 'Box prueba evidencias'})
BOX = next(b['id'] for b in r_box['boxes'] if b['nombre'] == 'Box prueba evidencias')
limpieza.insert(0, lambda: admin.pedir('PUT', f'/api/box/{BOX}', {'nombre': 'Box prueba evidencias', 'activo': False}))
_, r_psi = admin.pedir('POST', '/api/profesionales', {'idProfesion': PSICOLOGIA['id'], 'nombres': 'Prueba', 'apellidos': 'Evidencias Psicología',
                                                      'esAlumnoPractica': False, 'idsPoliclinico': [CASA_CENTRAL]})
st['psi'] = r_psi['id']
limpieza.append(lambda: admin.pedir('POST', f'/api/profesionales/{st["psi"]}/desactivar'))
DIA_A = hoy + dt.timedelta(days=2)          # dentro del plazo de 7 días


def bloque(prof, dia, ini, fin, desde=None, hasta=None):
    return {'idProfesional': prof, 'idBox': BOX, 'diaSemana': dia.isoweekday(), 'horaInicio': ini, 'horaTermino': fin,
            'vigenciaDesde': (desde or hoy).isoformat(), 'vigenciaHasta': (hasta or hoy + dt.timedelta(days=21)).isoformat()}


@caso('CP-07', 'RF-06', 'Vista previa de un bloque de Medicina (20 min) de 09:00 a 10:00.', 'Se generan 3 cupos: 09:00, 09:20 y 09:40 (CA-RF06-1).')
def _():
    s, r = admin.pedir('POST', '/api/bloques/previsualizar', bloque(st['med'], DIA_A, '09:00', '10:00'))
    horas = [h[:5] for h in r['horas']]
    return horas == ['09:00', '09:20', '09:40'], f'HTTP {s}: {r["cantidadPorDia"]} cupos por día de {r["duracion"]} min: {", ".join(horas)}.'


@caso('CP-08', 'RF-06', 'Vista previa de un bloque de Psicología (45 min) de 09:00 a 10:30.', 'Se generan 2 cupos: 09:00 y 09:45 (CA-RF06-2).')
def _():
    s, r = admin.pedir('POST', '/api/bloques/previsualizar', bloque(st['psi'], DIA_A, '09:00', '10:30'))
    horas = [h[:5] for h in r['horas']]
    return horas == ['09:00', '09:45'], f'HTTP {s}: {r["cantidadPorDia"]} cupos por día de {r["duracion"]} min: {", ".join(horas)}.'


@caso('CP-04', 'RF-04', 'Guardar un bloque semanal de 09:00 a 10:00 para el profesional de CP-02, vigente 3 semanas.', 'El bloque aparece en la agenda de esa semana (CA-RF04-1).')
def _():
    s, r = admin.pedir('POST', '/api/bloques', bloque(st['med'], DIA_A, '09:00', '10:00'))
    st['bloque_a'] = r['id']
    limpieza.append(lambda: admin.pedir('POST', f'/api/bloques/{st["bloque_a"]}/desactivar'))
    cupos = [c for c in semana(st['med'], DIA_A) if c['fecha'] == DIA_A.isoformat()]
    st['cupos_a'] = sorted(cupos, key=lambda c: c['horaInicio'])
    return s == 201 and len(cupos) == 3, f'HTTP {s}: bloque {r["id"]}; la agenda del {DIA_A:%d/%m} muestra {len(cupos)} cupos ({", ".join(c["horaInicio"][:5] for c in st["cupos_a"])}).'


@caso('CP-10', 'RF-08', 'Configurar un plazo de publicación de 7 días y consultar cupos dentro y fuera del plazo.', 'Fuera del plazo no aparece como disponible; aparece al entrar al plazo (CA-RF08-1).')
def _():
    s, _r = admin.pedir('PUT', f'/api/profesionales/{st["med"]}/publicacion', {'plazoPublicacionDias': 7})
    todos = semana(st['med'], DIA_A) + semana(st['med'], DIA_A + dt.timedelta(days=7)) + semana(st['med'], DIA_A + dt.timedelta(days=14))
    limite = hoy + dt.timedelta(days=7)
    dentro = {c['estado'] for c in todos if dt.date.fromisoformat(c['fecha']) <= limite}
    fuera = {c['estado'] for c in todos if dt.date.fromisoformat(c['fecha']) > limite}
    disp = disponibles(profesional=st['med'], desde=hoy, hasta=hoy + dt.timedelta(days=21))
    max_disp = max((c['fecha'] for c in disp), default='—')
    ok = s == 200 and 'NO_PUBLICADO' in fuera and fuera <= {'NO_PUBLICADO'} and 'DISPONIBLE' in dentro and max_disp <= limite.isoformat()
    return ok, f'HTTP {s}: hasta el {limite:%d/%m} los cupos están {sorted(dentro)}; después, {sorted(fuera)}. La búsqueda solo ofrece fechas hasta el {max_disp}.'


@caso('CP-09', 'RF-07', 'La Administración bloquea el cupo de las 09:00 indicando el motivo.', 'El cupo no aparece en la búsqueda y no puede reservarse (CA-RF07-1, D-12).')
def _():
    cupo = st['cupos_a'][0]
    s, r = admin.pedir('POST', '/api/bloqueos', {'idsCupo': [cupo['id']], 'motivo': MARCA})
    en_busqueda = any(c['id'] == cupo['id'] for c in disponibles(profesional=st['med'], desde=DIA_A, hasta=DIA_A))
    s2, r2 = tens.pedir('POST', '/api/citas', {'idPaciente': PEDRO, 'idCupo': cupo['id']})
    if s2 == 201:
        st.setdefault('citas', []).append(r2['cita']['id'])
    return s == 200 and not en_busqueda and s2 >= 400, f'HTTP {s}: cupo {cupo["id"]} en estado {r[0]["estado"] if r else "?"}; no aparece en la búsqueda; intento de reserva: HTTP {s2} «{(r2 or {}).get("mensaje", "")}».'


# ------------------------------------------------------------------ M3 · pacientes


@caso('CP-13', 'RF-10', 'Registrar un paciente ficticio con los datos obligatorios y sin correo.', 'Queda registrado y se encuentra por RUT (CA-RF10-1).')
def _():
    st['rut_a'] = rut_libre()
    s, r = tens.pedir('POST', '/api/pacientes', paciente(st['rut_a'], '+56912345671'))
    st['pac_a'] = r['id']
    s2, r2 = tens.pedir('GET', '/api/pacientes?documento=' + st['rut_a'])
    return s == 201 and s2 == 200 and r2['id'] == st['pac_a'], f'HTTP {s}: paciente {st["pac_a"]} ({st["rut_a"]}) sin correo; la búsqueda por RUT lo encuentra (HTTP {s2}).'


@caso('CP-14', 'RF-10', 'Registrar otro paciente con el mismo RUT de CP-13.', 'No se crea un duplicado y se muestra el paciente existente (CA-RF10-2).')
def _():
    s, r = tens.pedir('POST', '/api/pacientes', paciente(st['rut_a'], '+56912345672', nombres='Duplicado'))
    filas = sql(f"SELECT COUNT(*) FROM paciente WHERE numero_documento=REPLACE('{st['rut_a']}','.','');")
    det = r.get('detalles', {}) if isinstance(r, dict) else {}
    return s == 409, f'HTTP {s}: «{r.get("mensaje") if isinstance(r, dict) else r}»; detalles {det}; pacientes con ese RUT en la base: {filas[0][0] if filas else "?"}.'


@caso('CP-16', 'RF-12', 'Registrar un paciente ficticio menor de edad sin adulto responsable.', 'El sistema exige los datos del adulto responsable antes de guardar (CA-RF12-1).')
def _():
    r_menor = rut_libre()
    s, r = tens.pedir('POST', '/api/pacientes', paciente(r_menor, '+56912345673', nacimiento=(hoy.replace(year=hoy.year - 10)).isoformat()))
    s2, _ = tens.pedir('GET', '/api/pacientes?documento=' + r_menor)
    return s in (400, 422) and s2 == 404, f'HTTP {s}: «{r.get("mensaje") if isinstance(r, dict) else r}»; el menor no quedó registrado (búsqueda HTTP {s2}).'


@caso('CP-20', 'RF-15', 'La TENS/TONS intenta reservar para el paciente de CP-13, que no tiene verificación FONASA/CESFAM.', 'El sistema no permite la reserva e informa el motivo (CA-RF15-2).')
def _():
    s, r = tens.pedir('POST', '/api/citas', {'idPaciente': st['pac_a'], 'idCupo': st['cupos_a'][1]['id']})
    if s == 201:
        st.setdefault('citas', []).append(r['cita']['id'])
    return s in (409, 422), f'HTTP {s}: «{r.get("mensaje") if isinstance(r, dict) else r}».'


@caso('CP-17', 'RF-13', 'Registrar la verificación FONASA/CESFAM del paciente de CP-13.', 'El paciente queda habilitado para reservar y se guardan el CESFAM y la fecha (CA-RF13-1).')
def _():
    s, r = verificar(st['pac_a'])
    v = r.get('verificacionVigente') or {}
    return s == 200 and r.get('habilitado') is True, f'HTTP {s}: habilitado={r.get("habilitado")}; CESFAM «{(v or {}).get("cesfamInscripcion")}», fecha {(v or {}).get("fechaVerificacion")}.'


# ------------------------------------------------------------------ M4 · citas y M6 · confirmaciones


@caso('CP-05', 'RF-04', 'Reservar un cupo del bloque de CP-04 y luego intentar cambiar el horario del bloque.', 'Se muestran las reservas afectadas y se exige reasignarlas o anularlas; ninguna se borra en silencio (CA-RF04-2, D-10).')
def _():
    s, r = reservar(PEDRO, st['cupos_a'][1]['id'])
    st['cita_05'] = r['cita']['id']
    cambio = bloque(st['med'], DIA_A, '10:00', '11:00')
    s2, r2 = admin.pedir('PUT', f'/api/bloques/{st["bloque_a"]}', cambio)
    afectadas = (r2.get('detalles') or {}).get('afectadas') if isinstance(r2, dict) else None
    sigue = sql(f'SELECT estado FROM cita WHERE id_cita={st["cita_05"]};')
    n = len(afectadas) if isinstance(afectadas, list) else afectadas
    return s == 201 and s2 == 409 and sigue and sigue[0][0] == 'RESERVADA', f'Reserva HTTP {s}; cambio de horario HTTP {s2} «{r2.get("mensaje") if isinstance(r2, dict) else r2}» con {n} reserva(s) afectada(s); la cita sigue {sigue[0][0] if sigue else "?"}.'


@caso('CP-23', 'RF-17', 'Anular la cita reservada en CP-05.', 'La cita queda cancelada y el cupo vuelve a estar disponible (CA-RF17-1).')
def _():
    s, r = tens.pedir('POST', f'/api/citas/{st["cita_05"]}/anular', {'origen': 'TENS', 'motivo': MARCA})
    libre = any(c['id'] == st['cupos_a'][1]['id'] for c in disponibles(profesional=st['med'], desde=DIA_A, hasta=DIA_A))
    return s == 200 and r['estado'] == 'ANULADA' and libre, f'HTTP {s}: cita {st["cita_05"]} {r["estado"]}; el cupo volvió a la búsqueda: {libre}.'


@caso('CP-15', 'RF-11', 'Reservar para el paciente de CP-13, cambiar su teléfono y reenviar el recordatorio.', 'Las siguientes confirmaciones se envían al número nuevo (CA-RF11-1).')
def _():
    s, r = reservar(st['pac_a'], st['cupos_a'][2]['id'])
    st['cita_a'] = r['cita']['id']
    datos = paciente(st['rut_a'], '+56912345679')
    s2, r2 = tens.pedir('PUT', f'/api/pacientes/{st["pac_a"]}', datos)
    s3, _ = tens.pedir('POST', f'/api/citas/{st["cita_a"]}/reenviar-recordatorio')
    ult = mensajes_de(st['cita_a'])[-1]
    return s == 201 and s2 == 200 and ult[3].endswith('12345679'), f'Reserva HTTP {s}; teléfono actualizado HTTP {s2}; reenvío HTTP {s3}: el último mensaje ({ult[1]}) va a …{ult[3][-4:]} (número nuevo).'


@caso('CP-26', 'RF-19', 'Reservar para un paciente ficticio con número inválido (termina en 0000).', 'El mensaje queda FALLIDO y la cita aparece «por llamar» (CA-RF19-2, D-15).')
def _():
    r_b = rut_libre()
    s, r = tens.pedir('POST', '/api/pacientes', paciente(r_b, '+56912340000', nombres='Inválido'))
    st['pac_b'] = r['id']
    verificar(st['pac_b'])
    manana = hoy + dt.timedelta(days=1)          # «por llamar» se muestra para citas de hoy o mañana
    cup = disponibles(policlinico=CASA_CENTRAL, profesion=MEDICINA, desde=manana, hasta=manana) or \
        disponibles(policlinico=CASA_CENTRAL, profesion=KINESIOLOGIA, desde=manana, hasta=manana)
    s2, r2 = reservar(st['pac_b'], cup[-1]['id'])
    st['cita_b'] = r2['cita']['id']
    f = esperar_mensaje(st['cita_b'], 'CONFIRMACION')
    _, det = tens.pedir('GET', f'/api/citas/{st["cita_b"]}')
    fila = det['fila']
    return bool(f and f[-1][2] == 'FALLIDO' and fila['requiereLlamada']), f'Confirmación en estado {f[-1][2] if f else "—"}; la cita quedó «{fila["cita"]["estadoConfirmacion"]}» y requiere llamada = {fila["requiereLlamada"]}.'


@caso('CP-44', 'RF-39', 'Registrar la llamada de la TENS/TONS a la cita «por llamar» de CP-26 con resultado «No asistirá» y comentario.', 'La llamada queda en el historial de la cita y la cita se anula (CA-RF39-1, D-15).')
def _():
    s, r = tens.pedir('POST', f'/api/citas/{st["cita_b"]}/llamadas', {'resultado': 'NO_ASISTIRA', 'comentario': MARCA})
    est = sql(f'SELECT estado FROM cita WHERE id_cita={st["cita_b"]};')
    ll = sql(f'SELECT resultado, comentario FROM llamada_confirmacion WHERE id_cita={st["cita_b"]};')
    return bool(s in (200, 201) and est and est[0][0] == 'ANULADA' and ll), f'HTTP {s}: llamada registrada {ll[0] if ll else "—"}; la cita quedó {est[0][0] if est else "?"}.'


@caso('CP-22', 'RF-16', 'Reservar una serie de 3 sesiones de Kinesiología para el paciente ficticio habilitado.', 'Se crean N reservas; si faltan cupos, se muestran las fechas sin cupo y decide la TENS/TONS (CA-RF16-1, D-06).')
def _():
    cup = disponibles(policlinico=CASA_CENTRAL, profesion=KINESIOLOGIA, desde=hoy + dt.timedelta(days=1), hasta=hoy + dt.timedelta(days=7))
    c = cup[0]
    base = {'idPaciente': PEDRO, 'idProfesional': c['idProfesional'], 'idCupoPrimero': c['id'], 'cantidad': 3}
    s, pre = tens.pedir('POST', '/api/citas/serie/previsualizar', base)
    fechas = [x['fecha'] for x in pre['conCupo']]
    s2, r2 = tens.pedir('POST', '/api/citas/serie', {**base, 'fechasAceptadas': fechas})
    ids = [x['id'] for x in r2] if isinstance(r2, list) else []
    st.setdefault('citas', []).extend(ids)
    return s == 200 and s2 in (200, 201) and len(ids) == len(fechas) > 0, f'Vista previa: {len(fechas)} fechas con cupo {fechas}, {len(pre["sinCupo"])} sin cupo. Serie HTTP {s2}: {len(ids)} citas creadas.'


@caso('CP-30', 'RF-23', 'Consultar el historial de citas del paciente ficticio habilitado (15.432.109-8).', 'Se listan sus citas con fecha, profesional y estado (CA-RF23-1).')
def _():
    s, r = tens.pedir('GET', f'/api/pacientes/{PEDRO}/historial')
    estados = sorted({x['estadoTexto'] for x in r})
    completo = all(x.get('fecha') and x.get('profesional') and x.get('estadoTexto') for x in r)
    return bool(s == 200 and len(r) > 0 and completo), f'HTTP {s}: {len(r)} citas, todas con fecha, profesional y estado; estados: {", ".join(estados)}.'


# --- recordatorio del día antes y refuerzo, por el programador real (se ajusta la hora y se restaura)
ahora = ahora_servidor()


@caso('CP-28', 'RF-21', 'Con la hora del recordatorio fijada 3 minutos después, reservar una cita para mañana y esperar el envío programado.', 'Se envía el recordatorio el día antes de la cita (CA-RF21-1).')
def _():
    original = PARAM.get('RECORDATORIO_HORA') or '10:00'
    hora = (ahora_servidor() + dt.timedelta(minutes=3)).strftime('%H:%M')
    admin.pedir('PUT', '/api/parametros', {'RECORDATORIO_HORA': hora})
    limpieza.append(lambda: admin.pedir('PUT', '/api/parametros', {'RECORDATORIO_HORA': original}))
    manana = hoy + dt.timedelta(days=1)
    cup = disponibles(policlinico=CASA_CENTRAL, profesion=MEDICINA, desde=manana, hasta=manana) or \
        disponibles(policlinico=CASA_CENTRAL, profesion=KINESIOLOGIA, desde=manana, hasta=manana)
    s, r = reservar(PEDRO, cup[0]['id'])
    st['cita_28'] = r['cita']['id']
    admin.pedir('PUT', '/api/parametros', {'RECORDATORIO_HORA': original})
    f = esperar_mensaje(st['cita_28'], 'RECORDATORIO', maximo=330)
    ok = bool(f) and f[-1][2] in ('ENVIADO', 'ENTREGADO', 'LEIDO') and f[-1][6].startswith(hoy.isoformat())
    return ok, (f'Cita {st["cita_28"]} del {manana:%d/%m}: recordatorio programado para {f[-1][6] if f else "—"} (día antes, {hora}) '
                f'y enviado por el programador a las {f[-1][7][11:16] if f and f[-1][7] else "—"} (estado {f[-1][2] if f else "—"}).')


@caso('CP-43', 'RF-38', 'Con 1 refuerzo 1 hora antes, reservar una cita para dentro de 63 minutos; luego la TENS/TONS reenvía el recordatorio.', 'Se envían los refuerzos configurados y el reenvío queda registrado con la TENS/TONS (CA-RF38-1, D-04).')
def _():
    orig = {k: PARAM.get(k) or '' for k in ('REFUERZOS_CANTIDAD', 'REFUERZOS_HORAS_ANTES')}
    admin.pedir('PUT', '/api/parametros', {'REFUERZOS_CANTIDAD': '1', 'REFUERZOS_HORAS_ANTES': '1'})
    limpieza.append(lambda: admin.pedir('PUT', '/api/parametros', orig))
    ini = (ahora_servidor() + dt.timedelta(minutes=63)).replace(second=0, microsecond=0)
    if ini.date() != hoy or ini.hour >= 23:
        return 'Bloqueada', 'No se pudo ejecutar: quedan menos de 63 minutos del día en el servidor.'
    fin = ini + dt.timedelta(minutes=40)
    s, r = admin.pedir('POST', '/api/bloques', bloque(st['med'], hoy, ini.strftime('%H:%M'), fin.strftime('%H:%M'), hoy, hoy))
    st['bloque_43'] = r['id']
    limpieza.append(lambda: admin.pedir('POST', f'/api/bloques/{st["bloque_43"]}/desactivar'))
    cup = [c for c in semana(st['med'], hoy) if c['fecha'] == hoy.isoformat() and c['horaInicio'][:5] == ini.strftime('%H:%M')]
    s2, r2 = reservar(PEDRO, cup[0]['id'])
    st['cita_43'] = r2['cita']['id']
    admin.pedir('PUT', '/api/parametros', orig)
    f = esperar_mensaje(st['cita_43'], 'REFUERZO', maximo=330)
    s3, _ = tens.pedir('POST', f'/api/citas/{st["cita_43"]}/reenviar-recordatorio')
    time.sleep(3)
    ree = [m for m in mensajes_de(st['cita_43']) if m[4] == 'TENS']
    ok = bool(bool(f) and f[-1][2] in ('ENVIADO', 'ENTREGADO', 'LEIDO') and s3 == 200 and ree and ree[-1][5])
    return ok, (f'Refuerzo programado para {f[-1][6][11:16] if f else "—"} (1 h antes de las {ini:%H:%M}) y enviado a las {f[-1][7][11:16] if f and f[-1][7] else "—"} ({f[-1][2] if f else "—"}). '
                f'Reenvío HTTP {s3}: mensaje {ree[-1][1] if ree else "—"} con enviado_por=TENS y usuario {ree[-1][5] if ree else "—"}.')


# ------------------------------------------------------------------ M7 · asistencia (cita de hoy en Casa Central)


def asistencia_hoy():
    _, r = admin.pedir('GET', f'/api/reportes/asistencia?desde={hoy}&hasta={hoy}&policlinico={CASA_CENTRAL}')
    return r['total']


@caso('CP-29', 'RF-22', 'Marcar como asistida la cita de hoy creada en CP-43.', 'El estado queda guardado y se refleja en historial y reportes (CA-RF22-1).')
def _():
    antes = asistencia_hoy()
    s, r = tens.pedir('POST', f'/api/citas/{st["cita_43"]}/asistencia', {'asistio': True})
    _, hist = tens.pedir('GET', f'/api/pacientes/{PEDRO}/historial')
    h = next((x for x in hist if x['id'] == st['cita_43']), {})
    despues = asistencia_hoy()
    return s == 200 and h.get('estado') == 'ASISTIDA' and despues['asistidas'] == antes['asistidas'] + 1, \
        f'HTTP {s}: cita {st["cita_43"]} {h.get("estado")} en el historial; el reporte de hoy pasó de {antes["asistidas"]} a {despues["asistidas"]} asistidas.'



@caso('CP-61', 'RNF-12', 'Reservar otra cita de hoy, registrar su inasistencia y generar el reporte de inmediato.', 'El reporte ya incluye esa inasistencia (CA-RNF12-1).')
def _():
    cup = [c for c in semana(st['med'], hoy) if c['fecha'] == hoy.isoformat() and c['estado'] == 'DISPONIBLE']
    if not cup:   # bloque de 20 min: crear otro cupo justo después
        b = admin.pedir('GET', f'/api/bloques?profesional={st["med"]}')[1]
        return 'Bloqueada', f'Sin cupo libre hoy para la segunda cita ({len(b)} bloques).'
    s, r = reservar(st['pac_a'], cup[0]['id'])
    antes = asistencia_hoy()
    s2, _ = tens.pedir('POST', f'/api/citas/{r["cita"]["id"]}/asistencia', {'asistio': False})
    despues = asistencia_hoy()
    return s2 == 200 and despues['inasistencias'] == antes['inasistencias'] + 1, \
        f'HTTP {s2}: inasistencia registrada; el reporte de hoy pasó de {antes["inasistencias"]} a {despues["inasistencias"]} inasistencias ({despues["porcentajeInasistencia"]} %).'


# ------------------------------------------------------------------ M5 · lista de espera


@caso('CP-32', 'RF-25', 'Inscribir al paciente de CP-13 en la lista de espera de Medicina en Casa Central.', 'Aparece con su fecha de inscripción, en orden de llegada (CA-RF25-1).')
def _():
    s, r = tens.pedir('POST', '/api/lista-espera', {'idPaciente': st['pac_a'], 'idPoliclinico': CASA_CENTRAL, 'idProfesion': MEDICINA})
    st['insc_a'] = r['id']
    limpieza.append(lambda: tens.pedir('POST', f'/api/lista-espera/{st["insc_a"]}/retirar', {'motivo': 'OTRO', 'detalle': MARCA}))
    _, lista = tens.pedir('GET', f'/api/lista-espera?policlinico={CASA_CENTRAL}&profesion={MEDICINA}')
    pos = next((x['posicion'] for x in lista if x['id'] == st['insc_a']), None)
    ordenada = [x['fechaOrden'] for x in lista] == sorted(x['fechaOrden'] for x in lista)
    st['lista_n'] = len(lista)
    return s == 201 and pos == len(lista) and ordenada, f'HTTP {s}: inscrito el {r["fechaInscripcion"][:16].replace("T", " ")} en la posición {pos} de {len(lista)}; la lista está en orden de llegada: {ordenada}.'


@caso('CP-33', 'RF-26', 'Anular una reserva de Medicina en Casa Central, atención que tiene pacientes en espera.', 'La TENS/TONS del policlínico ve el aviso (CA-RF26-1).')
def _():
    cup = disponibles(policlinico=CASA_CENTRAL, profesion=MEDICINA, desde=hoy + dt.timedelta(days=2), hasta=hoy + dt.timedelta(days=7))
    s, r = reservar(PEDRO, cup[-1]['id'])
    tens.pedir('POST', f'/api/citas/{r["cita"]["id"]}/anular', {'origen': 'TENS', 'motivo': MARCA})
    _, avisos = tens.pedir('GET', f'/api/avisos-cupo?policlinico={CASA_CENTRAL}')
    av = next((a for a in avisos if a['idCupo'] == cup[-1]['id']), None)
    if av:
        tens.pedir('POST', f'/api/avisos-cupo/{av["id"]}/atender')
    return av is not None, f'Cupo {cup[-1]["id"]} liberado; aviso en la agenda de la TENS/TONS: {"sí" if av else "no"} ({av["profesion"] + ", " + str(av["fecha"]) + " " + str(av["hora"])[:5] if av else "—"}).'


@caso('CP-47', 'RF-42', 'Adelantar al paciente de CP-32 al primer lugar de la lista indicando el motivo.', 'Cambia el orden y queda el movimiento con usuario, fecha y motivo (CA-RF42-1).')
def _():
    s, r = tens.pedir('POST', f'/api/lista-espera/{st["insc_a"]}/adelantar', {'motivo': MARCA, 'posicion': 1})
    mov = (r.get('movimientos') or [{}])[-1] if isinstance(r, dict) else {}
    return s == 200 and r.get('posicion') == 1 and mov.get('motivo') == MARCA and mov.get('idUsuario'), \
        f'HTTP {s}: pasó a la posición {r.get("posicion")} de {st["lista_n"]}; movimiento {mov.get("tipo")} por usuario {mov.get("idUsuario")} el {str(mov.get("fechaHora"))[:16]} con motivo «{mov.get("motivo")}».'


@caso('CP-48', 'RF-43', 'Retirar al paciente de la lista de espera con motivo «hora asignada», ligado a su cita de CP-15.', 'La inscripción queda retirada con su motivo (CA-RF43-1, D-05).')
def _():
    s, r = tens.pedir('POST', f'/api/lista-espera/{st["insc_a"]}/retirar', {'motivo': 'HORA_ASIGNADA', 'detalle': MARCA, 'idCita': st['cita_a']})
    fila = sql(f'SELECT estado, motivo_retiro, IFNULL(id_cita_asignada,"") FROM inscripcion_espera WHERE id_inscripcion={st["insc_a"]};')
    return s == 200 and fila and fila[0][1] == 'HORA_ASIGNADA', f'HTTP {s}: inscripción {fila[0] if fila else "—"} (estado, motivo, cita).'


# ------------------------------------------------------------------ vistas y reportes


@caso('CP-45', 'RF-40', 'Elegir la vista por box en la agenda del día de Casa Central.', 'La agenda muestra las citas agrupadas por box (CA-RF40-1, D-16).')
def _():
    fecha = hoy + dt.timedelta(days=1)
    s, r = tens.pedir('GET', f'/api/agenda-dia?policlinico={CASA_CENTRAL}&fecha={fecha}&vista=POR_BOX')
    cols = r.get('columnas') or []
    titulos = [c['titulo'] for c in cols]
    con_citas = sum(1 for c in cols for x in c['cupos'] if x.get('cita'))
    return s == 200 and r.get('vista') == 'POR_BOX' and titulos and all('Box' in t or 'box' in t for t in titulos), \
        f'HTTP {s}: vista {r.get("vista")} del {fecha:%d/%m} con {len(cols)} columnas ({", ".join(titulos)}) y {con_citas} citas ubicadas en su box.'


MES_ANT_FIN = hoy.replace(day=1) - dt.timedelta(days=1)
MES_ANT_INI = MES_ANT_FIN.replace(day=1)
PERIODO = f'desde={MES_ANT_INI}&hasta={MES_ANT_FIN}&policlinico={CASA_CENTRAL}'
JOIN = 'JOIN cupo c ON c.id_cupo=ci.id_cupo JOIN bloque_horario b ON b.id_bloque=c.id_bloque JOIN box x ON x.id_box=b.id_box'
RANGO = f"c.fecha BETWEEN '{MES_ANT_INI}' AND '{MES_ANT_FIN}' AND x.id_policlinico={CASA_CENTRAL}"


@caso('CP-34', 'RF-27', f'Generar el reporte de ocupación de Casa Central de {MES_ANT_INI:%m/%Y} y compararlo con la base.', 'Muestra cupos disponibles, reservados y % de ocupación coincidentes con los registros (CA-RF27-1).')
def _():
    s, r = admin.pedir('GET', '/api/reportes/ocupacion?' + PERIODO)
    q = sql(f"SELECT COUNT(*), COUNT(ci.id_cita) FROM cupo c JOIN bloque_horario b ON b.id_bloque=c.id_bloque JOIN box x ON x.id_box=b.id_box "
            f"LEFT JOIN cita ci ON ci.id_cupo_activo=c.id_cupo WHERE {RANGO} AND c.id_bloqueo IS NULL;")
    ofr, res = int(q[0][0]), int(q[0][1])
    pct = round(res * 100 / ofr, 1) if ofr else 0
    ok = s == 200 and r['ofrecidos'] == ofr and r['reservados'] == res and abs(r['porcentajeOcupacion'] - pct) < 0.11
    return ok, f'Reporte: {r["ofrecidos"]} ofrecidos, {r["reservados"]} reservados, {r["disponibles"]} disponibles, {r["porcentajeOcupacion"]} %. Base: {ofr} y {res} ({pct} %).'


@caso('CP-35', 'RF-28', f'Generar el reporte de asistencia de Casa Central de {MES_ANT_INI:%m/%Y} y compararlo con la base.', 'El % de inasistencia es igual a inasistencias / citas del período (CA-RF28-1).')
def _():
    s, r = admin.pedir('GET', '/api/reportes/asistencia?' + PERIODO)
    t = r['total']
    q = sql(f"SELECT COUNT(*), SUM(ci.estado='ASISTIDA'), SUM(ci.estado='INASISTIDA') FROM cita ci {JOIN} WHERE ci.estado<>'ANULADA' AND {RANGO};")
    tot, asi, ina = map(int, q[0])
    pct = round(ina * 100 / tot, 1) if tot else 0
    ok = s == 200 and t['reservadas'] == tot and t['inasistencias'] == ina and abs(t['porcentajeInasistencia'] - pct) < 0.11
    return ok, f'Reporte: {t["reservadas"]} citas, {t["asistidas"]} asistidas, {t["inasistencias"]} inasistencias, {t["porcentajeInasistencia"]} %. Base: {tot}, {asi}, {ina} → {ina}/{tot} = {pct} %.'


@caso('CP-36', 'RF-29', f'Generar el reporte de demanda de Casa Central de {MES_ANT_INI:%m/%Y}.', 'Muestra, por profesional, reservas más pacientes en espera de su profesión (CA-RF29-1, D-13).')
def _():
    s, r = admin.pedir('GET', '/api/reportes/demanda?' + PERIODO)
    f = max(r['filas'], key=lambda x: x['reservas'])
    q = sql(f"SELECT COUNT(*) FROM cita ci {JOIN} WHERE b.id_profesional={f['idProfesional']} AND ci.estado<>'ANULADA' AND {RANGO};")
    e = sql(f"SELECT COUNT(*) FROM inscripcion_espera ie JOIN profesional p ON p.id_profesion=ie.id_profesion WHERE p.id_profesional={f['idProfesional']} "
            f"AND ie.estado='EN_ESPERA' AND ie.id_policlinico={CASA_CENTRAL};")
    ok = s == 200 and f['reservas'] == int(q[0][0]) and f['enEspera'] == int(e[0][0]) and f['demanda'] == f['reservas'] + f['enEspera']
    return ok, f'{len(r["filas"])} profesionales. Ejemplo {f["profesional"]} ({f["profesion"]}): {f["reservas"]} reservas + {f["enEspera"]} en espera = {f["demanda"]}. Base: {q[0][0]} reservas y {e[0][0]} en espera.'


@caso('CP-37', 'RF-30', f'Generar la caracterización de pacientes de Casa Central de {MES_ANT_INI:%m/%Y}.', 'Totales por grupo etario, género y nacionalidad que suman el total de atendidos (CA-RF30-1).')
def _():
    s, r = admin.pedir('GET', '/api/reportes/caracterizacion?' + PERIODO)
    sumas = {k: sum(c['cantidad'] for c in r[k]) for k in ('porEdad', 'porGenero', 'porNacionalidad')}
    q = sql(f"SELECT COUNT(DISTINCT ci.id_paciente) FROM cita ci {JOIN} WHERE ci.estado='ASISTIDA' AND {RANGO};")
    ok = s == 200 and all(v == r['total'] for v in sumas.values()) and r['total'] == int(q[0][0])
    return ok, f'Total atendidos {r["total"]} (base: {q[0][0]}); suma por edad {sumas["porEdad"]}, por género {sumas["porGenero"]}, por nacionalidad {sumas["porNacionalidad"]}.'


@caso('CP-31', 'RF-24', 'Generar el listado de inasistencia reiterada de Casa Central de los últimos 6 meses (umbral 3).', 'Aparecen solo los pacientes con 3 o más inasistencias (CA-RF24-1).')
def _():
    desde = (hoy - dt.timedelta(days=182))
    s, r = tens.pedir('GET', f'/api/reportes/inasistencia-reiterada?desde={desde}&hasta={hoy}&policlinico={CASA_CENTRAL}')
    filas = r.get('filas', [])
    rango = f"c.fecha BETWEEN '{desde}' AND '{hoy}' AND x.id_policlinico={CASA_CENTRAL}"
    q = sql(f"SELECT SUM(n>={r['umbral']}), SUM(n={r['umbral'] - 1}) FROM (SELECT ci.id_paciente, COUNT(*) n FROM cita ci {JOIN} "
            f"WHERE ci.estado='INASISTIDA' AND {rango} GROUP BY ci.id_paciente) t;")
    sobre, bajo = int(q[0][0] or 0), int(q[0][1] or 0)
    ok = s == 200 and all(f['inasistencias'] >= r['umbral'] for f in filas) and len(filas) == sobre
    return ok, f'Umbral {r["umbral"]}: {len(filas)} pacientes, todos con {r["umbral"]} o más (mínimo {min((f["inasistencias"] for f in filas), default="—")}). Base: {sobre} con ≥ {r["umbral"]} y {bajo} con {r["umbral"] - 1}, que no aparecen.'


# ------------------------------------------------------------------ CU-08 · reserva en línea


import collections
import math


@caso('CP-42', 'RF-37', 'Cambiar el porcentaje de cupos web de 50 % a 20 % y revisar la agenda de un profesional; luego restaurarlo.', 'En la web se ofrece solo esa parte de los cupos (CA-RF37-1).')
def _():
    orig = PARAM.get('PORCENTAJE_CUPOS_WEB') or '50'
    prof = disponibles(policlinico=CASA_CENTRAL, profesion=MEDICINA, desde=hoy + dt.timedelta(days=1), hasta=hoy + dt.timedelta(days=7))[0]['idProfesional']

    def leer(pct):
        cupos = [c for c in semana(prof, hoy + dt.timedelta(days=1)) if c['fecha'] > hoy.isoformat()]
        grupos = collections.defaultdict(list)
        for c in cupos:
            grupos[(c['fecha'], c['idBox'])].append(c)
        web_libres = esperado = 0
        for gr in grupos.values():
            gr.sort(key=lambda c: c['horaInicio'])
            n = len(gr); k = min(n, math.ceil(n * pct / 100))
            idx = {math.floor(i * n / k) for i in range(k)} if k else set()
            for i, c in enumerate(gr):
                if c['estado'] != 'RESERVADO':
                    esperado += i in idx
                    web_libres += c['habilitadoWeb']
        libres = sum(1 for c in cupos if c['estado'] != 'RESERVADO')
        return web_libres, esperado, libres, len(cupos), len(grupos)
    a = leer(int(orig))
    s, _ = admin.pedir('PUT', '/api/parametros', {'PORCENTAJE_CUPOS_WEB': '20'})
    time.sleep(3)
    b = leer(20)
    admin.pedir('PUT', '/api/parametros', {'PORCENTAJE_CUPOS_WEB': orig})
    time.sleep(3)
    c = leer(int(orig))
    return bool(s == 200 and a[0] == a[1] and b[0] == b[1] and c[0] == c[1]),             (f'Profesional {prof}, próxima semana: {b[3]} cupos en {b[4]} bloques-día, {b[2]} libres. Cupos web entre los libres: con {orig} % → {a[0]} (regla: {a[1]}); '
         f'con 20 % → {b[0]} (regla: {b[1]}); restaurado a {orig} % → {c[0]} (regla: {c[1]}). Los cupos ya reservados conservan su marca, como define el recálculo.')


@caso('CP-41', 'RF-36', 'Reserva en línea de un RUT ficticio sin ficha, completando los datos de ingreso.', 'Se registra al paciente sin verificación y la cita queda «pendiente de verificación» (CA-RF36-1).')
def _():
    publico = Cliente()
    st['rut_web'] = rut_libre()
    s, ide = publico.pedir('POST', '/api/publico/identificacion', {'tipoDocumento': 'RUT', 'numero': st['rut_web'], 'fechaNacimiento': '1975-03-15'})
    tok = {'X-Token-Reserva': ide['token']}
    s2, reg = publico.pedir('POST', '/api/publico/pacientes', {'nombres': 'Prueba', 'apellidos': 'Evidencias En Línea', 'telefonoCelular': '+56912345674',
                                                               'correo': None, 'direccion': 'Calle Ficticia 456, Santiago', 'genero': 'Masculino',
                                                               'nacionalidad': 'Chilena', 'adulto': None}, cabeceras=tok)
    tok = {'X-Token-Reserva': (reg or {}).get('token') or ide['token']}
    cup = publico.pedir('GET', f'/api/publico/cupos?policlinico={CASA_CENTRAL}&profesion={MEDICINA}&desde={hoy + dt.timedelta(days=1)}')[1]
    s3, comp = publico.pedir('POST', '/api/publico/reservas', {'idCupo': cup[0]['id']}, cabeceras=tok)
    st['web'] = (publico, tok, comp.get('idCita'))
    st.setdefault('citas', []).append(comp.get('idCita'))
    est = sql(f'SELECT ci.estado, IFNULL(v.resultado,"sin verificación") FROM cita ci LEFT JOIN verificacion_requisitos v ON v.id_paciente=ci.id_paciente WHERE ci.id_cita={comp.get("idCita")};')
    return s3 in (200, 201) and est and est[0][0] == 'PENDIENTE_VERIFICACION', f'Identificación {ide.get("estado")}; registro HTTP {s2}; reserva HTTP {s3}: cita {comp.get("idCita")} en estado {est[0][0] if est else "?"} ({est[0][1] if est else "—"}).'


@caso('CP-46', 'RF-41', 'Subir el certificado de FONASA (PDF ficticio) en la reserva en línea de CP-41.', 'El certificado queda asociado al paciente para la verificación de la TENS/TONS (CA-RF41-1).')
def _():
    publico, tok, cita = st['web']
    pdf = b'%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'
    lim = uuid.uuid4().hex
    cuerpo = (f'--{lim}\r\nContent-Disposition: form-data; name="tipo"\r\n\r\nCERTIFICADO_FONASA\r\n'
              f'--{lim}\r\nContent-Disposition: form-data; name="archivo"; filename="certificado_fonasa_ficticio.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode() + pdf + f'\r\n--{lim}--\r\n'.encode()
    s, r = publico.pedir('POST', f'/api/publico/reservas/{cita}/certificados', crudo=cuerpo, cabeceras=tok, tipo=f'multipart/form-data; boundary={lim}')
    _, p = tens.pedir('GET', '/api/pacientes?documento=' + st['rut_web'])
    _, docs = tens.pedir('GET', f'/api/pacientes/{p["id"]}/documentos')
    return s in (200, 201) and any(d['tipo'] == 'CERTIFICADO_FONASA' for d in docs), f'HTTP {s}; la ficha del paciente muestra {len(docs)} documento(s): {", ".join(d["tipo"] + " (" + d["tipoMime"] + ")" for d in docs)}.'


@caso('CP-39', 'RF-34', 'Reserva en línea del paciente ficticio habilitado (RUT y fecha de nacimiento correctos) en un cupo web.', 'El cupo deja de estar disponible en la web y en la agenda de la TENS/TONS, y se envía la confirmación (CA-RF34-1).')
def _():
    publico = Cliente()
    s, ide = publico.pedir('POST', '/api/publico/identificacion', {'tipoDocumento': 'RUT', 'numero': '15.432.109-8', 'fechaNacimiento': FICHA_PEDRO['fechaNacimiento']})
    tok = {'X-Token-Reserva': ide['token']}
    desde = hoy + dt.timedelta(days=2)
    cup = publico.pedir('GET', f'/api/publico/cupos?policlinico={CASA_CENTRAL}&profesion={KINESIOLOGIA}&desde={desde}')[1]
    c = cup[0]
    s2, comp = publico.pedir('POST', '/api/publico/reservas', {'idCupo': c['id']}, cabeceras=tok)
    cid = comp.get('idCita')
    st.setdefault('citas', []).append(cid)
    web = any(x['id'] == c['id'] for x in publico.pedir('GET', f'/api/publico/cupos?policlinico={CASA_CENTRAL}&profesion={KINESIOLOGIA}&desde={desde}')[1])
    interno = any(x['id'] == c['id'] for x in disponibles(policlinico=CASA_CENTRAL, profesion=KINESIOLOGIA, desde=c['fecha'], hasta=c['fecha']))
    f = esperar_mensaje(cid, 'CONFIRMACION')
    ok = bool(s2 in (200, 201) and not web and not interno and f and f[-1][2] in ('ENVIADO', 'ENTREGADO', 'LEIDO'))
    return ok, f'Identificación {ide.get("estado")}; reserva HTTP {s2}: cita {cid} ({comp.get("estado")}); ya no aparece en la web ({not web}) ni en la búsqueda de la TENS/TONS ({not interno}); confirmación {f[-1][2] if f else "—"}.'


# ------------------------------------------------------------------ revisión de plantillas (RNF-02 y RNF-06)


@caso('CP-55', 'RNF-06', 'Revisar el texto de las 6 plantillas guardadas en el sistema.', 'Solo contienen policlínico, dirección, profesional, fecha y hora (CA-RNF06-1).')
def _():
    filas = sql("SELECT nombre_proveedor, texto FROM plantilla_mensaje ORDER BY id_plantilla;")
    extra = sorted({v for _n, t in filas for v in __import__('re').findall(r'\{\{(\w+)}}', t.replace('\\n', ' ')) if v.lower() in ('profesion', 'box', 'nombre')})
    return (not extra), f'{len(filas)} plantillas revisadas. Además de policlínico, dirección, profesional, fecha y hora incluyen: {", ".join(extra) or "nada más"}. Ninguna incluye diagnóstico ni datos clínicos.'


# ------------------------------------------------------------------ limpieza
log('Limpieza…')
for cid in [c for c in st.get('citas', []) if c]:
    tens.pedir('POST', f'/api/citas/{cid}/anular', {'origen': 'TENS', 'motivo': MARCA})
for fn in reversed(limpieza):
    try:
        fn()
    except Exception as e:
        log('limpieza falló', e)
_, final = admin.pedir('GET', '/api/parametros')
log('Parámetros restaurados:', {p['clave']: p['valor'] for p in final if p['clave'] in PARAM and PARAM[p['clave']] != p['valor']} or 'sin diferencias')
_, pub = anon.pedir('GET', '/api/publico/policlinicos')
log('Policlínicos públicos:', len(pub))

commit = subprocess.run(['git', 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True).stdout.strip()
print('## Segunda tanda (scripts/evidencias_nube_2.py)\n')
print(f'- **Fecha:** {ahora_servidor():%d-%m-%Y %H:%M} (hora del servidor)  ·  **Entorno:** {BASE}  ·  **Versión:** commit `{commit}`')
print('- Datos ficticios. Lo creado se anuló o desactivó al final y los parámetros se restauraron.\n')
print('| Evidencia | CP | RF/RNF | Prueba | Resultado esperado | Estado | Resultado obtenido |')
print('|---|---|---|---|---|---|---|')
for cp, rf, prueba, esperado, estado, obtenido in sorted(resultados, key=lambda x: int(x[0][3:])):
    print(f'| EV-{cp} | {cp} | {rf} | {prueba} | {esperado} | **{estado}** | {obtenido.replace("|", "/")} |')
cuenta = {e: sum(1 for r in resultados if r[4] == e) for e in ('Aprobada', 'Fallida', 'Bloqueada')}
print(f'\n**Resumen:** {cuenta["Aprobada"]} aprobadas, {cuenta["Fallida"]} fallidas y {cuenta["Bloqueada"]} bloqueadas de {len(resultados)} casos.')
json.dump(resultados, open(os.environ.get('SALIDA_JSON', 'evidencias_nube_2.json'), 'w', encoding='utf8'), ensure_ascii=False, indent=1)
