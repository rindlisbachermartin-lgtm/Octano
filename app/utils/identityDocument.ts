export function formatIdentityDocument(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 11)
  if (digits.length <= 8) return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  return `${digits.slice(0, 2)}-${digits.slice(2, 10)}${digits.length > 10 ? `-${digits.slice(10)}` : ''}`
}

export function formatIdentityDocumentInput(event: Event, previousValue = '') {
  const input = event.target as HTMLInputElement
  const cursor = input.selectionStart ?? input.value.length
  let digits = input.value.replace(/\D/g, '')
  let digitsBeforeCursor = input.value.slice(0, cursor).replace(/\D/g, '').length
  const inputType = (event as InputEvent).inputType
  // Delete a digit when deleting a separator, so the caret never gets stuck
  // on a dot or dash that formatting would immediately restore.
  if (digits.length === previousValue.replace(/\D/g, '').length) {
    if (inputType === 'deleteContentBackward' && digitsBeforeCursor > 0) {
      digits = digits.slice(0, digitsBeforeCursor - 1) + digits.slice(digitsBeforeCursor)
      digitsBeforeCursor--
    } else if (inputType === 'deleteContentForward') {
      digits = digits.slice(0, digitsBeforeCursor) + digits.slice(digitsBeforeCursor + 1)
    }
  }
  const formatted = formatIdentityDocument(digits)
  let position = 0
  let seen = 0
  while (position < formatted.length && seen < digitsBeforeCursor) {
    if (/\d/.test(formatted[position]!)) seen++
    position++
  }
  input.value = formatted
  input.setSelectionRange(position, position)
  return formatted
}
