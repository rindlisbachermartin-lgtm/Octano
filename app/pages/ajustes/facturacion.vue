<script setup lang="ts">
import { ArrowLeft, ShieldCheck, Save, PlugZap } from 'lucide-vue-next'
import type { IssuerVatCondition } from '~/types'

const auth = useOwnerAccount()
const { db } = useDatabase()
const { today } = useWorkshopDay()
const form = reactive({ cuit: '', pointOfSale: '00001', vat: 'IVA Responsable Inscripto' as IssuerVatCondition, activityStartDate: '', address: '', city: '', province: '', arcaModel: 'delegation' as 'delegation' | 'certificates' })
const certificate = shallowRef<File | null>(null)
const privateKey = shallowRef<File | null>(null)
const error = ref('')
const message = ref('')
const busy = ref(false)
const verifiedSnapshot = ref('')
const snapshot = computed(() => JSON.stringify(form))
const verified = computed(() => !!verifiedSnapshot.value && verifiedSnapshot.value === snapshot.value)

onMounted(() => {
  auth.initialize()
  const profile = auth.fiscalProfile.value
  if (!profile) return
  Object.assign(form, { cuit: profile.cuit, pointOfSale: String(profile.pointOfSale).padStart(5, '0'), vat: profile.vat, activityStartDate: profile.activityStartDate || '', address: profile.address, city: profile.city, province: profile.province, arcaModel: profile.arcaModel || 'delegation' })
  if (profile.arcaStatus === 'demo-verified') verifiedSnapshot.value = snapshot.value
})

watch(snapshot, () => { error.value = ''; message.value = '' })
watch(() => form.arcaModel, () => { certificate.value = null; privateKey.value = null; verifiedSnapshot.value = '' }, { flush: 'sync' })

function selectFile(event: Event, kind: 'certificate' | 'key') {
  error.value = ''; message.value = ''; verifiedSnapshot.value = ''
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] || null
  const allowed = kind === 'certificate' ? /\.(crt|pem)$/i : /\.key$/i
  if (file && (!allowed.test(file.name) || file.size === 0)) {
    error.value = kind === 'certificate' ? 'Seleccioná un certificado .crt o .pem que no esté vacío.' : 'Seleccioná una clave privada .key que no esté vacía.'
    input.value = ''
    if (kind === 'certificate') certificate.value = null
    else privateKey.value = null
    return
  }
  if (kind === 'certificate') certificate.value = file
  else privateKey.value = file
}

function validate(testConnection = false) {
  error.value = ''; message.value = ''
  if (!isValidCuit(form.cuit)) error.value = 'El CUIT debe tener 11 números y un dígito verificador válido.'
  else if (!/^\d{4,5}$/.test(form.pointOfSale) || Number(form.pointOfSale) < 1) error.value = 'El punto de venta debe tener 4 o 5 dígitos, entre 00001 y 99999.'
  else if (!ISSUER_VAT_CONDITIONS.includes(form.vat)) error.value = 'Seleccioná la condición frente al IVA.'
  else if (!form.activityStartDate || !/^\d{4}-\d{2}-\d{2}$/.test(form.activityStartDate) || !Number.isFinite(Date.parse(form.activityStartDate)) || new Date(form.activityStartDate).toISOString().slice(0, 10) !== form.activityStartDate || form.activityStartDate > today.value) error.value = 'Ingresá una fecha de inicio de actividades válida, hasta hoy.'
  else if (!form.address.trim() || !form.city.trim() || !form.province.trim()) error.value = 'Completá el domicilio fiscal, la localidad y la provincia.'
  else if (testConnection && form.arcaModel === 'certificates' && (!certificate.value || !privateKey.value)) error.value = 'Seleccioná el certificado y la clave privada para probar la conexión.'
  return !error.value
}

function persist(status: 'pending' | 'demo-verified') {
  auth.updateFiscal({ ...form, pointOfSale: Number(form.pointOfSale), address: form.address.trim(), city: form.city.trim(), province: form.province.trim(), arcaStatus: status })
  db.value.issuerVatCondition = form.vat
}

function save() {
  if (!validate()) return
  try {
    persist(verified.value ? 'demo-verified' : 'pending')
    message.value = 'Configuración fiscal guardada.'
  } catch (err) { error.value = err instanceof Error ? err.message : 'No pudimos guardar la configuración.' }
}

async function testConnection() {
  if (!validate(true)) return
  busy.value = true
  try {
    await nextTick()
    persist('demo-verified')
    verifiedSnapshot.value = snapshot.value
    message.value = 'Prueba simulada correcta. La configuración quedó guardada para la maqueta.'
  } catch (err) { error.value = err instanceof Error ? err.message : 'No pudimos probar la configuración.' }
  finally { busy.value = false }
}
</script>

<template>
  <div class="page-content fiscal-settings">
    <section class="page-heading">
      <div><div class="eyebrow">CONFIGURACIÓN / FACTURACIÓN</div><h1>Configuración fiscal / ARCA</h1></div>
      <NuxtLink to="/facturacion" class="button outlined"><ArrowLeft :size="16" />Volver a facturación</NuxtLink>
    </section>
    <form class="panel fiscal-panel" @submit.prevent="save">
      <div class="section-heading fiscal-panel-heading"><div><h2>{{ auth.fiscalProfile.value?.legalName || 'Tu taller' }}</h2><p class="muted">Datos del emisor para la facturación electrónica.</p></div><span class="badge neutral">{{ verified ? 'Verificada en maqueta' : 'Pendiente de conexión' }}</span></div>
      <fieldset :disabled="busy" class="fiscal-fields">
        <legend>Modelo de integración</legend>
        <div class="fiscal-models">
          <label class="fiscal-model" :class="{ selected: form.arcaModel === 'delegation' }"><input v-model="form.arcaModel" type="radio" value="delegation" /><span><strong>Delegación <small>Recomendada</small></strong><span>El taller delega el servicio de facturación en ARCA.</span></span></label>
          <label class="fiscal-model" :class="{ selected: form.arcaModel === 'certificates' }"><input v-model="form.arcaModel" type="radio" value="certificates" /><span><strong>Certificados propios</strong><span>El taller selecciona su certificado y su clave privada.</span></span></label>
        </div>
        <div class="fiscal-grid">
          <label>CUIT emisor<input :value="form.cuit" @input="form.cuit = formatIdentityDocumentInput($event, form.cuit)" inputmode="numeric" placeholder="30-12345678-1" required /></label>
          <label>Punto de venta<input :value="form.pointOfSale" @input="form.pointOfSale = ($event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 5)" inputmode="numeric" maxlength="5" minlength="4" pattern="[0-9]{4,5}" placeholder="00001" required /></label>
          <label>Condición frente al IVA<select v-model="form.vat" required><option value="Responsable Monotributo">Monotributo</option><option value="IVA Responsable Inscripto">Responsable Inscripto</option><option value="IVA Sujeto Exento">Exento</option></select></label>
          <label>Inicio de actividades<input v-model="form.activityStartDate" type="date" :max="today" required /></label>
          <label class="fiscal-address">Domicilio fiscal<input v-model="form.address" autocomplete="street-address" placeholder="Calle y número" required /></label>
          <label>Localidad<input v-model="form.city" autocomplete="address-level2" required /></label>
          <label>Provincia<input v-model="form.province" autocomplete="address-level1" required /></label>
        </div>
        <div v-if="form.arcaModel === 'certificates'" class="fiscal-grid fiscal-files">
          <label>Certificado X.509<input type="file" accept=".crt,.pem" @change="selectFile($event, 'certificate')" /><small>Archivo .crt o .pem</small></label>
          <label>Clave privada<input type="file" accept=".key" @change="selectFile($event, 'key')" /><small>Archivo .key</small></label>
          <p class="fiscal-file-note">En esta maqueta, los archivos solo se seleccionan para probar el formulario. No se leen, suben ni guardan; al salir de la pantalla tendrás que seleccionarlos de nuevo.</p>
        </div>
      </fieldset>
      <p class="fiscal-demo-note">La prueba de conexión es simulada. No consulta ARCA ni habilita la emisión de comprobantes reales.</p>
      <p v-if="error" role="alert" class="fiscal-error">{{ error }}</p>
      <p v-if="message" role="status" class="fiscal-success"><ShieldCheck :size="16" />{{ message }}</p>
      <div class="fiscal-actions"><button type="submit" class="button outlined" :disabled="busy"><Save :size="16" />Guardar configuración</button><button type="button" class="button primary" :disabled="busy" @click="testConnection"><PlugZap :size="16" />{{ busy ? 'Probando conexión…' : 'Probar conexión con ARCA' }}</button></div>
    </form>
  </div>
</template>

<style scoped>
.fiscal-settings { max-width: 1060px; }
.fiscal-panel { padding: 24px; }
.fiscal-panel-heading { align-items: flex-start; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.fiscal-panel-heading h2 { font-size: 18px; margin: 0 0 6px; }
.fiscal-panel-heading p { margin: 0; font-size: 12px; }
.fiscal-fields { border: 0; padding: 0; margin: 0; min-width: 0; }
.fiscal-fields legend { font-size: 13px; font-weight: 600; margin-bottom: 12px; }
.fiscal-models, .fiscal-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.fiscal-models { margin-bottom: 24px; }
.fiscal-model { display: flex; align-items: flex-start; gap: 10px; padding: 16px; border: 1px solid var(--line); border-radius: 12px; cursor: pointer; }
.fiscal-model.selected { border-color: #487cdb; }
.fiscal-model input { width: 16px; height: 16px; margin: 2px 0 0; flex-shrink: 0; }
.fiscal-model strong { display: block; margin-bottom: 6px; font-size: 13px; }
.fiscal-model strong small { font-size: 10px; color: #487cdb; margin-left: 5px; }
.fiscal-model > span > span { display: block; color: var(--muted); font-size: 12px; line-height: 1.5; }
.fiscal-grid > label { display: flex; flex-direction: column; gap: 8px; font-size: 12px; font-weight: 550; min-width: 0; }
.fiscal-grid input, .fiscal-grid select { width: 100%; margin: 0; min-height: 42px; }
.fiscal-grid small { font-size: 11px; font-weight: 400; color: var(--muted); }
.fiscal-address, .fiscal-file-note { grid-column: 1 / -1; }
.fiscal-files { padding-top: 20px; margin-top: 20px; border-top: 1px solid var(--line); }
.fiscal-file-note, .fiscal-demo-note { color: var(--muted); font-size: 12px; line-height: 1.6; }
.fiscal-file-note { margin: 0; }
.fiscal-demo-note { margin: 24px 0 16px; }
.fiscal-error { padding: 12px; border: 1px solid #e88b8b66; border-radius: 8px; color: #b42318; font-size: 12px; }
:global(html.dark .fiscal-error) { color: #f1a6a6; }
.fiscal-success { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text); }
.fiscal-actions { display: flex; justify-content: flex-end; gap: 12px; flex-wrap: wrap; }
@media (max-width: 600px) { .fiscal-panel { padding: 16px; } .fiscal-models, .fiscal-grid { grid-template-columns: 1fr; } .fiscal-actions .button { width: 100%; justify-content: center; } }
</style>
