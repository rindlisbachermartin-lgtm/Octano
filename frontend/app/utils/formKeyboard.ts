export function avanzarConEnter(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.defaultPrevented || event.isComposing
    || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey) return

  const actual = event.target
  if (!(actual instanceof HTMLInputElement || actual instanceof HTMLSelectElement)) return
  // Estos controles usan Enter para abrir su selector o activar una acción.
  if (actual instanceof HTMLInputElement && ['button', 'submit', 'reset', 'image', 'file', 'color'].includes(actual.type)) return
  const form = actual.form
  if (!form) return

  event.preventDefault()
  if (event.repeat) return
  const campos = Array.from(form.querySelectorAll<HTMLElement>(
    'input, select, textarea, button[aria-haspopup], [role="combobox"]',
  )).filter(campo => campo.tabIndex >= 0
    && !campo.matches(':disabled, [readonly], [type="hidden"], [aria-disabled="true"]')
    && !campo.matches('input:is([type="button"], [type="submit"], [type="reset"], [type="image"])')
    && !campo.closest('[hidden], [inert], [aria-hidden="true"]')
    && campo.getClientRects().length > 0
    && getComputedStyle(campo).visibility === 'visible')

  const indice = campos.indexOf(actual)
  if (indice >= 0) campos[indice + 1]?.focus()
}
