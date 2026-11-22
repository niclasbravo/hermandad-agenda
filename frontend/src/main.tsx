import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@fontsource/atkinson-hyperlegible-next/400.css';
import '@fontsource/atkinson-hyperlegible-next/500.css';
import '@fontsource/atkinson-hyperlegible-next/600.css';
import '@fontsource/atkinson-hyperlegible-next/700.css';
import '@fontsource/bitter/500.css';
import '@fontsource/bitter/600.css';
import './compartido/estilos.css';
import { App } from './App';
import { ProveedorSesion } from './compartido/sesion';
import { ErrorDeApi } from './compartido/api';
import { ProveedorAvisos } from './compartido/avisos';

const cliente = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (n, e) => !(e instanceof ErrorDeApi && e.estado >= 400 && e.estado < 500) && n < 2,
      refetchOnWindowFocus: true,
      staleTime: 15_000,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={cliente}>
      <BrowserRouter>
        <ProveedorSesion>
          <ProveedorAvisos>
            <App />
          </ProveedorAvisos>
        </ProveedorSesion>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
