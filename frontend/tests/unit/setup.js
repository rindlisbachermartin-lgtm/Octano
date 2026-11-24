import { config, enableAutoUnmount } from '@vue/test-utils'
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useHelpers } from '../../app/composables/useHelpers'
import { useDebouncedValue } from '../../app/composables/useDebouncedValue'
import * as identity from '../../app/utils/identityDocument'
import * as tax from '../../app/utils/invoiceTax'
import { isValidCuit } from '../../app/utils/ownerValidation'
import DependentFields from '../../app/components/common/DependentFields.vue'
import * as vehicleDisplay from '../../app/utils/vehicleDisplay'
import * as names from '../../app/utils/inicialMayuscula'
import * as vehicleApi from '../../app/utils/vehiculoApi'
import * as partApi from '../../app/utils/repuestoApi'
import { authError } from '../../app/composables/useAuthApi'
import { useAliquotasIva } from '../../app/composables/useAliquotasIva'

// La aplicación ya conoce estas funciones. Acá las agregamos para poder probarla sin iniciar Nuxt.
Object.assign(globalThis, {
  ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount,
  useHelpers, useDebouncedValue, ...identity, ...tax, ...vehicleDisplay, ...names, ...vehicleApi, ...partApi, authError, isValidCuit,
  useDatabase: jest.fn(),
  useOwnerAccount: jest.fn(),
  useWorkshopToast: () => ({ notify: jest.fn() }),
  useAliquotasIva: () => ({ aliquotas: ref([{ id: 1, porcentaje: 0 }, { id: 2, porcentaje: 10.5 }, { id: 3, porcentaje: 21 }]), error: ref(''), cargar: jest.fn() }),
  useModalEscape: jest.fn(),
  definePageMeta: jest.fn(),
  navigateTo: jest.fn(),
})
config.global.config.globalProperties = { ...identity, ...tax, ...vehicleDisplay }
config.global.components.CommonDependentFields = DependentFields
// Usamos una caja simple en lugar del contenedor visual del formulario.
// Los campos y sus validaciones siguen siendo los de la aplicación.
config.global.stubs.CommonFormPage = { template: '<section><slot /></section>' }
config.global.stubs.NuxtLink = { template: '<a><slot /></a>' }
config.global.stubs.OrdenesEditorRepuestos = true
config.global.stubs.OrdenesDetalleRepuestos = true
// Al terminar cada prueba, quitamos el formulario y volvemos al reloj normal.
enableAutoUnmount(afterEach)
afterEach(() => {
  jest.useRealTimers()
})
