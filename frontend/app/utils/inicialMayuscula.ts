export function inicialMayuscula(valor: string) {
  return valor.trim().replace(/^\p{L}/u, letra => letra.toLocaleUpperCase('es-AR'))
}

export function inicialesNombre(valor: string) {
  return valor.trim().replace(/(^|\s)(\p{L})/gu, (_, espacio, letra) =>
    espacio + letra.toLocaleUpperCase('es-AR'))
}
