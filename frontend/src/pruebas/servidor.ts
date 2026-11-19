import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { POLICLINICOS, PROFESIONALES, PROFESIONES } from './utilidades';

/** Servidor MSW base: los tests agregan sus propios manejadores con servidor.use(...). */
export const servidor = setupServer(
  http.get('/api/auth/csrf', () => {
    document.cookie = 'XSRF-TOKEN=token-prueba; path=/';
    return HttpResponse.json({ cabecera: 'X-XSRF-TOKEN' });
  }),
  http.get('/api/policlinicos', () => HttpResponse.json(POLICLINICOS)),
  http.get('/api/profesiones', () => HttpResponse.json(PROFESIONES)),
  http.get('/api/profesionales', () => HttpResponse.json(PROFESIONALES)),
);
