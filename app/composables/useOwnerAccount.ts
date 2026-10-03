import type { IssuerVatCondition } from '~/types'

export interface WorkshopOwnerAccount {
  name: string
  email: string
  phone: string
  workshop: string
  legalName: string
  cuit: string
  address: string
  city: string
  province: string
  vat: IssuerVatCondition
  pointOfSale: number
  arcaStatus: 'pending' | 'demo-verified'
  passwordHash: string
  salt: string
}

const ACCOUNT_KEY = 'octano-owner-demo-v1'
const SESSION_KEY = 'octano-owner-session-demo-v1'

async function passwordDigest(password: string, salt: string) {
  const encoder = new TextEncoder()
  const key = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: encoder.encode(salt), iterations: 100000 }, key, 256)
  return Array.from(new Uint8Array(bits), byte => byte.toString(16).padStart(2, '0')).join('')
}

// Local prototype only. Production authentication and ARCA validation belong
// on the server; no fiscal credentials or private keys are collected here.
export function useOwnerAccount() {
  const account = useState<WorkshopOwnerAccount | null>('ownerDemoAccount', () => null)
  const session = useState<string | null>('ownerDemoSession', () => null)
  const initialized = useState('ownerDemoInitialized', () => false)

  function initialize() {
    if (!import.meta.client || initialized.value) return
    initialized.value = true
    try {
      const saved = JSON.parse(localStorage.getItem(ACCOUNT_KEY) || 'null')
      if (saved?.email && saved?.passwordHash && saved?.salt) account.value = saved
      const savedSession = sessionStorage.getItem(SESSION_KEY) || localStorage.getItem(SESSION_KEY)
      if (savedSession === 'demo' || savedSession === account.value?.email) session.value = savedSession
    } catch { account.value = null; session.value = null }
  }

  function startSession(value: string, remember: boolean) {
    session.value = value
    sessionStorage.setItem(SESSION_KEY, value)
    if (remember) localStorage.setItem(SESSION_KEY, value)
    else localStorage.removeItem(SESSION_KEY)
  }

  async function register(data: Omit<WorkshopOwnerAccount, 'passwordHash' | 'salt' | 'arcaStatus'>, password: string) {
    initialize()
    if (account.value) throw new Error('Ya hay una cuenta registrada en esta demo. Ingresá con ese correo.')
    const salt = Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('')
    const record: WorkshopOwnerAccount = { ...data, email: data.email.trim().toLowerCase(), arcaStatus: 'pending', salt, passwordHash: await passwordDigest(password, salt) }
    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(record))
    account.value = record
    startSession(record.email, false)
  }

  async function login(email: string, password: string, remember: boolean) {
    initialize()
    const record = account.value
    if (!record || record.email !== email.trim().toLowerCase()
      || await passwordDigest(password, record.salt) !== record.passwordHash) {
      throw new Error('El correo o la contraseña no coinciden. Revisalos e intentá de nuevo.')
    }
    startSession(record.email, remember)
  }

  function updateFiscal(data: Partial<Pick<WorkshopOwnerAccount, 'legalName' | 'cuit' | 'address' | 'city' | 'province' | 'vat' | 'pointOfSale' | 'arcaStatus'>>) {
    if (!account.value || session.value !== account.value.email) return
    const record = { ...account.value, ...data }
    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(record))
    account.value = record
  }

  function enterDemo() { initialize(); startSession('demo', false) }
  function logout() {
    session.value = null
    sessionStorage.removeItem(SESSION_KEY)
    localStorage.removeItem(SESSION_KEY)
    return navigateTo('/login')
  }

  const owner = computed(() => session.value && session.value !== 'demo' ? account.value : null)
  const fiscalIssuer = computed(() => owner.value ? {
    name: owner.value.legalName, cuit: owner.value.cuit, address: owner.value.address,
    city: owner.value.city, province: owner.value.province, phone: owner.value.phone,
  } : undefined)
  return { account, session, owner, fiscalIssuer, initialize, register, login, updateFiscal, enterDemo, logout }
}
