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
  arcaModel?: 'delegation' | 'certificates'
  activityStartDate?: string
  passwordHash: string
  salt: string
}

const ACCOUNT_KEY = 'octano-owner-demo-v1'
const SESSION_KEY = 'octano-owner-session-demo-v1'
const MECHANIC_DEMO_ACCOUNTS = {
  'Nicolás': { email: 'nicolas@taller.demo', salt: 'octano-demo-nicolas', passwordHash: '301c90db65ddfe40a8d6d210fa8e9ec11b7d12e4ce6719a01c6dcfc0be5e9caa' },
  'Santiago': { email: 'santiago@taller.demo', salt: 'octano-demo-santiago', passwordHash: '60bfaedd2b17b3c22cb9d639424bd924cfc586de7437517f07ffcec2cefc5dd5' },
}
const DEMO_FISCAL_KEY = 'octano-fiscal-demo-v1'
type FiscalProfile = Pick<WorkshopOwnerAccount, 'legalName' | 'cuit' | 'address' | 'city' | 'province' | 'vat' | 'pointOfSale' | 'arcaStatus' | 'arcaModel' | 'activityStartDate'>

async function passwordDigest(password: string, salt: string) {
  const encoder = new TextEncoder()
  const key = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: encoder.encode(salt), iterations: 100000 }, key, 256)
  return Array.from(new Uint8Array(bits), byte => byte.toString(16).padStart(2, '0')).join('')
}

// Local prototype only. Production authentication and ARCA validation belong
// on the server. Selected certificates and keys never enter account storage.
export function useOwnerAccount() {
  const account = useState<WorkshopOwnerAccount | null>('ownerDemoAccount', () => null)
  const session = useState<string | null>('ownerDemoSession', () => null)
  const initialized = useState('ownerDemoInitialized', () => false)
  const demoFiscal = useState<FiscalProfile>('ownerDemoFiscal', () => ({ legalName: 'Taller Central', cuit: '20-12345678-6', address: 'Av. García Salinas 1450', city: 'Trenque Lauquen', province: 'Buenos Aires', vat: 'IVA Responsable Inscripto', pointOfSale: 3, arcaStatus: 'pending', arcaModel: 'delegation', activityStartDate: '' }))

  function initialize() {
    if (!import.meta.client || initialized.value) return
    initialized.value = true
    try {
      const saved = JSON.parse(localStorage.getItem(ACCOUNT_KEY) || 'null')
      if (saved?.email && saved?.passwordHash && saved?.salt) account.value = saved
      const savedSession = sessionStorage.getItem(SESSION_KEY) || localStorage.getItem(SESSION_KEY)
      if (savedSession === 'demo' || savedSession === 'mechanic:Nicolás' || savedSession === 'mechanic:Santiago' || savedSession === 'mechanic-preview:Nicolás' || savedSession === 'mechanic-preview:Santiago' || savedSession === account.value?.email) session.value = savedSession
      const savedFiscal = JSON.parse(localStorage.getItem(DEMO_FISCAL_KEY) || 'null')
      if (savedFiscal?.cuit) demoFiscal.value = { ...demoFiscal.value, ...savedFiscal }
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

  function updateFiscal(data: Partial<FiscalProfile>) {
    if (session.value === 'demo') {
      const record = { ...demoFiscal.value, ...data }
      localStorage.setItem(DEMO_FISCAL_KEY, JSON.stringify(record))
      demoFiscal.value = record
      return
    }
    if (!account.value || session.value !== account.value.email) throw new Error('Iniciá sesión para guardar la configuración fiscal.')
    const record = { ...account.value, ...data }
    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(record))
    account.value = record
  }

  async function loginMechanic(email: string, password: string, remember = false) {
    initialize()
    const entry = Object.entries(MECHANIC_DEMO_ACCOUNTS).find(([, account]) => account.email === email.trim().toLowerCase())
    const record = entry?.[1]
    if (!record || await passwordDigest(password, record.salt) !== record.passwordHash) {
      throw new Error('El correo o la contraseña no coinciden. Revisalos e intentá de nuevo.')
    }
    startSession(`mechanic:${entry![0]}`, remember)
  }

  function enterMechanicPreview(name: 'Nicolás' | 'Santiago') {
    initialize()
    if (!['Nicolás', 'Santiago'].includes(name)) throw new Error('Seleccioná un mecánico válido.')
    startSession(`mechanic-preview:${name}`, false)
  }

  function enterDemo() { initialize(); startSession('demo', false) }
  function logout() {
    session.value = null
    sessionStorage.removeItem(SESSION_KEY)
    localStorage.removeItem(SESSION_KEY)
    return navigateTo('/login')
  }

  const mechanic = computed(() => ['mechanic:Nicolás', 'mechanic-preview:Nicolás'].includes(session.value || '') ? 'Nicolás' : ['mechanic:Santiago', 'mechanic-preview:Santiago'].includes(session.value || '') ? 'Santiago' : null)
  const isMechanicPreview = computed(() => !!session.value?.startsWith('mechanic-preview:'))
  const isMechanic = computed(() => mechanic.value !== null)
  const homePath = computed(() => mechanic.value === 'Santiago' ? '/mecanico/santiago' : mechanic.value ? '/mecanico' : '/')
  const owner = computed(() => session.value && session.value === account.value?.email ? account.value : null)
  const fiscalProfile = computed(() => session.value === 'demo' ? demoFiscal.value : owner.value)
  const fiscalIssuer = computed(() => owner.value ? {
    name: owner.value.legalName, cuit: owner.value.cuit, address: owner.value.address,
    city: owner.value.city, province: owner.value.province, phone: owner.value.phone,
    activityStartDate: owner.value.activityStartDate,
  } : undefined)
  return { account, session, mechanic, isMechanic, homePath, loginMechanic, enterMechanicPreview, isMechanicPreview, owner, fiscalProfile, fiscalIssuer, initialize, register, login, updateFiscal, enterDemo, logout }
}
