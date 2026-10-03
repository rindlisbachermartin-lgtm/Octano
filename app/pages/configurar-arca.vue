<script setup lang="ts">
import { ShieldCheck, ArrowRight, ExternalLink, Check, Circle, LoaderCircle } from 'lucide-vue-next'
definePageMeta({ layout: 'auth' })
const auth = useOwnerAccount()
const { db } = useDatabase()
const checks = reactive({ certificate: false, service: false, point: false })
const pointOfSale = ref(3)
const busy = ref(false)
const verified = ref(false)
const error = ref('')
onMounted(async () => {
  auth.initialize()
  pointOfSale.value = auth.owner.value?.pointOfSale || 3
  const wasVerified = auth.owner.value?.arcaStatus === 'demo-verified'
  if (wasVerified) { checks.certificate = true; checks.service = true; checks.point = true }
  await nextTick()
  verified.value = wasVerified
})
const allChecked = computed(() => checks.certificate && checks.service && checks.point)
watch([pointOfSale, () => checks.certificate, () => checks.service, () => checks.point], () => { verified.value = false })
function save(status: 'pending' | 'demo-verified') {
  auth.updateFiscal({ pointOfSale: Number(pointOfSale.value), arcaStatus: status })
  if (auth.owner.value) db.value.issuerVatCondition = auth.owner.value.vat
}
async function verify() {
  error.value = ''
  if (!allChecked.value) { error.value = 'Completá los tres pasos antes de verificar la configuración.'; return }
  if (!auth.owner.value || !isValidCuit(auth.owner.value.cuit) || !Number.isInteger(Number(pointOfSale.value)) || Number(pointOfSale.value) < 1 || Number(pointOfSale.value) > 99999) { error.value = 'Revisá el CUIT y el punto de venta (de 1 a 99999).'; return }
  busy.value = true
  try { save('demo-verified'); verified.value = true }
  catch { error.value = 'No pudimos guardar la configuración. Intentá de nuevo.' }
  finally { busy.value = false }
}
async function continueToWorkshop() {
  error.value = ''
  try { if (!verified.value) save('pending'); await navigateTo('/') }
  catch { error.value = 'No pudimos guardar la configuración. Intentá de nuevo.' }
}
</script>

<template>
  <div class="auth-form arca-onboarding">
    <div class="auth-steps" aria-label="Progreso del registro"><span class="active"></span><span class="active"></span><span class="active"></span></div>
    <span class="auth-eyebrow">PASO 3 DE 3 · FACTURACIÓN</span>
    <h2>{{ verified ? 'Todo listo para la demo.' : 'Conectá tu facturación.' }}</h2>
    <p class="auth-description">{{ verified ? 'Tu configuración quedó guardada. Ya podés explorar cómo se emiten las facturas de tu taller.' : 'Antes de emitir, el taller necesita un certificado y un punto de venta habilitado para facturación electrónica.' }}</p>
    <div class="arca-owner"><ShieldCheck :size="21" /><div><strong>{{ auth.owner.value?.legalName }}</strong><small>CUIT {{ auth.owner.value?.cuit }} · {{ auth.owner.value?.vat }}</small></div></div>
    <div class="arca-checklist">
      <label class="arca-check"><input v-model="checks.certificate" type="checkbox" :disabled="busy" /><span class="arca-check-icon"><Check v-if="checks.certificate" :size="16" /><Circle v-else :size="16" /></span><span><strong>Certificado digital</strong><small>Creado para identificar al taller ante ARCA.</small></span></label>
      <label class="arca-check"><input v-model="checks.service" type="checkbox" :disabled="busy" /><span class="arca-check-icon"><Check v-if="checks.service" :size="16" /><Circle v-else :size="16" /></span><span><strong>Servicio autorizado</strong><small>Certificado asociado a Facturación Electrónica.</small></span></label>
      <label class="arca-check"><input v-model="checks.point" type="checkbox" :disabled="busy" /><span class="arca-check-icon"><Check v-if="checks.point" :size="16" /><Circle v-else :size="16" /></span><span><strong>Punto de venta habilitado</strong><small>Específico para emitir por web services.</small></span></label>
    </div>
    <label class="auth-field arca-point">Punto de venta<input v-model.number="pointOfSale" type="number" min="1" max="99999" step="1" required :disabled="busy" /><small>Se usará en la numeración de tus facturas.</small></label>
    <div class="arca-demo-note"><span class="arca-demo-chip">DEMO</span><p>Marcá los pasos para probar el recorrido. La verificación es simulada: no consulta ARCA ni autoriza facturas reales.</p></div>
    <a class="auth-link arca-guide" href="https://www.arca.gob.ar/ws/documentacion/wsaa.asp" target="_blank" rel="noopener noreferrer">Ver guía oficial de ARCA<ExternalLink :size="12" /></a>
    <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
    <div v-if="verified" class="arca-verified" role="status"><ShieldCheck :size="18" /><span>Configuración verificada en demo</span></div>
    <button v-if="!verified" class="auth-submit arca-verify-button" type="button" :disabled="busy" @click="verify"><LoaderCircle v-if="busy" :size="16" />{{ busy ? 'Verificando…' : 'Verificar configuración en demo' }}<ArrowRight v-if="!busy" :size="16" /></button>
    <button v-else class="auth-submit arca-verify-button" type="button" @click="continueToWorkshop">Ir a mi taller<ArrowRight :size="16" /></button>
    <button v-if="!verified" class="arca-later" type="button" :disabled="busy" @click="continueToWorkshop">Configurar después</button>
    <p class="auth-disclosure">Nunca te vamos a pedir tu clave fiscal de ARCA.</p>
  </div>
</template>

<style scoped>
.arca-owner { display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid var(--auth-line); border-radius: 11px; background: var(--auth-surface); color: #6683b1; }
.arca-owner strong { display: block; font-size: 12px; color: var(--auth-ink); }
.arca-owner small { display: block; font-size: 9px; color: var(--auth-muted); margin-top: 5px; line-height: 1.5; }
.arca-checklist { margin: 19px 0; }
.arca-check { display: flex; align-items: center; gap: 10px; padding: 13px 0; position: relative; cursor: pointer; border-bottom: 1px solid var(--auth-line); }
.arca-check input { width: 17px; height: 17px; position: absolute; opacity: 0; left: 0; }
.arca-check:focus-within { outline: 2px solid #7798ea; outline-offset: 3px; border-radius: 4px; }
.arca-check-icon { color: #8a95a2; display: grid; place-items: center; width: 24px; height: 24px; flex-shrink: 0; }
.arca-check:has(input:checked) .arca-check-icon { color: #3c7ae9; background: #3c7ae915; border-radius: 50%; }
.arca-check strong { font-size: 11px; display: block; color: var(--auth-ink); font-weight: 550; }
.arca-check small { font-size: 10px; color: var(--auth-muted); display: block; margin-top: 4px; font-weight: 400; line-height: 1.5; }
.arca-demo-note { display: flex; gap: 9px; align-items: flex-start; margin-top: 20px; }
.arca-demo-note p { margin: 0; font-size: 10px; color: var(--auth-muted); line-height: 1.6; }
.arca-demo-chip { font-size: 8px; padding: 3px 5px; background: #3b7bea12; color: #6687bd; border-radius: 4px; flex-shrink: 0; }
.arca-guide { margin-top: 10px; display: inline-flex; align-items: center; gap: 6px; font-size: 10px; }
.arca-verify-button { margin-top: 23px; }
.arca-later { display: block; margin: 15px auto 0; padding: 5px; background: transparent; border: 0; color: var(--auth-muted); font-size: 11px; cursor: pointer; }
.arca-verified { display: flex; align-items: center; gap: 8px; font-size: 11px; color: #4278bc; margin-top: 20px; }
</style>
