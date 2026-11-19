import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, siSePierdeLaSesion } from './api';
import type { Perfil, Usuario } from './tipos';

interface ContextoSesion {
  usuario: Usuario | null;
  cargando: boolean;
  ingresar: (usuario: string, contrasena: string, perfil: Perfil) => Promise<Usuario>;
  salir: () => Promise<void>;
}

const Contexto = createContext<ContextoSesion | null>(null);

export function ProveedorSesion({ children, inicial }: { children: ReactNode; inicial?: Usuario | null }) {
  const [usuario, setUsuario] = useState<Usuario | null>(inicial ?? null);
  const [cargando, setCargando] = useState(inicial === undefined);
  const qc = useQueryClient();

  useEffect(() => {
    if (inicial !== undefined) return;
    api.get<Usuario>('/api/auth/yo').then(setUsuario).catch(() => setUsuario(null)).finally(() => setCargando(false));
  }, [inicial]);

  useEffect(() => {
    siSePierdeLaSesion(() => {
      setUsuario(null);
      qc.clear();
    });
  }, [qc]);

  const ingresar = useCallback(async (u: string, c: string, perfil: Perfil) => {
    const yo = await api.post<Usuario>('/api/auth/ingresar', { usuario: u, contrasena: c, perfil });
    qc.clear();
    setUsuario(yo);
    return yo;
  }, [qc]);

  const salir = useCallback(async () => {
    try {
      await api.post('/api/auth/salir');
    } finally {
      qc.clear();
      setUsuario(null);
    }
  }, [qc]);

  return <Contexto.Provider value={{ usuario, cargando, ingresar, salir }}>{children}</Contexto.Provider>;
}

export function useSesion() {
  const c = useContext(Contexto);
  if (!c) throw new Error('useSesion fuera de ProveedorSesion');
  return c;
}
