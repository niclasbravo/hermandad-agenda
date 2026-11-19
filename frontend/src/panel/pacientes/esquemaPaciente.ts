import { z } from 'zod';
import { rutValido } from '../../compartido/rut';
import { edad } from '../../compartido/formato';

export const GENEROS = ['Femenino', 'Masculino', 'Otro', 'Prefiere no decir'];
export const RELACIONES = ['madre', 'padre', 'hijo', 'hija', 'abuela', 'abuelo', 'hermana', 'hermano', 'tutor legal', 'otro familiar'];

const telefono = z.string().trim().min(1, 'Escribe el número de celular.')
  .refine((t) => t.replace(/[^\d]/g, '').length >= 8, 'Escribe un celular válido, por ejemplo +56 9 1234 5678.');

/** Validación en el cliente (zod); el servidor vuelve a validar todo. RN-05: menor de edad exige adulto responsable. */
export const esquemaPaciente = z.object({
  tipoDocumento: z.enum(['RUT', 'PASAPORTE', 'OTRO']),
  numeroDocumento: z.string().trim().min(1, 'Escribe el número de documento.'),
  nombres: z.string().trim().min(1, 'Escribe los nombres.').max(80),
  apellidos: z.string().trim().min(1, 'Escribe los apellidos.').max(80),
  fechaNacimiento: z.string().min(1, 'Escribe la fecha de nacimiento.'),
  telefonoCelular: telefono,
  correo: z.string().trim().max(120).refine((c) => !c || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(c), 'Escribe un correo válido o déjalo vacío.'),
  direccion: z.string().trim().min(1, 'Escribe la dirección.').max(150),
  genero: z.string().min(1, 'Elige el género.'),
  nacionalidad: z.string().trim().min(1, 'Escribe la nacionalidad.').max(60),
  conAdulto: z.boolean(),
  adulto: z.object({
    tipoDocumento: z.enum(['RUT', 'PASAPORTE', 'OTRO']),
    numeroDocumento: z.string().trim(),
    nombreCompleto: z.string().trim(),
    telefono: z.string().trim(),
    relacion: z.string().trim(),
  }),
}).superRefine((d, ctx) => {
  if (d.tipoDocumento === 'RUT' && d.numeroDocumento && !rutValido(d.numeroDocumento)) {
    ctx.addIssue({ code: 'custom', path: ['numeroDocumento'], message: 'El RUT no es válido. Revisa el número y el dígito verificador.' });
  }
  if (d.fechaNacimiento) {
    const f = new Date(d.fechaNacimiento + 'T00:00:00');
    if (isNaN(f.getTime()) || f > new Date()) ctx.addIssue({ code: 'custom', path: ['fechaNacimiento'], message: 'Escribe una fecha de nacimiento válida.' });
  }
  const menor = d.fechaNacimiento ? edad(d.fechaNacimiento) < 18 : false;
  if (menor || d.conAdulto) {
    const a = d.adulto;
    const req = (campo: keyof typeof a, msj: string) => { if (!a[campo]) ctx.addIssue({ code: 'custom', path: ['adulto', campo], message: msj }); };
    req('numeroDocumento', 'Escribe el documento del adulto responsable.');
    req('nombreCompleto', 'Escribe el nombre del adulto responsable.');
    req('telefono', 'Escribe el teléfono del adulto responsable.');
    req('relacion', 'Indica la relación con el paciente.');
    if (a.tipoDocumento === 'RUT' && a.numeroDocumento && !rutValido(a.numeroDocumento)) {
      ctx.addIssue({ code: 'custom', path: ['adulto', 'numeroDocumento'], message: 'El RUT no es válido. Revisa el número y el dígito verificador.' });
    }
  }
});

export type DatosFormulario = z.infer<typeof esquemaPaciente>;

export function aPeticion(d: DatosFormulario) {
  const menor = d.fechaNacimiento ? edad(d.fechaNacimiento) < 18 : false;
  return {
    tipoDocumento: d.tipoDocumento, numeroDocumento: d.numeroDocumento, nombres: d.nombres, apellidos: d.apellidos,
    fechaNacimiento: d.fechaNacimiento, telefonoCelular: d.telefonoCelular, correo: d.correo || null, direccion: d.direccion,
    genero: d.genero, nacionalidad: d.nacionalidad,
    adulto: menor || d.conAdulto ? d.adulto : null,
  };
}
