import { config, enableAutoUnmount } from '@vue/test-utils'
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount, onScopeDispose } from 'vue'
import { TextEncoder } from 'node:util'
import { authError } from '../../app/composables/useAuthApi'
import { useCatalogoVehiculos } from '../../app/composables/useCatalogoVehiculos'
import { useBusquedaTitulares } from '../../app/composables/useBusquedaTitulares'
import { inicialMayuscula, inicialesNombre } from '../../app/utils/inicialMayuscula'
import FormatoInvalido from '../../app/components/common/FormatoInvalido.vue'
import { prepararSesionDePrueba } from './helpers'
import { useHelpers } from '../../app/composables/useHelpers'
import { useDebouncedValue } from '../../app/composables/useDebouncedValue'
import * as identity from '../../app/utils/identityDocument'
import * as tax from '../../app/utils/invoiceTax'
import * as catalog from '../../app/utils/vehicleCatalog'
import { isValidCuit } from '../../app/utils/ownerValidation'
import DependentFields from '../../app/components/common/DependentFields.vue'
import * as vehicleDisplay from '../../app/utils/vehicleDisplay'

// La aplicación ya conoce estas funciones. Acá las agregamos para poder probarla sin iniciar Nuxt.
Object.assign(globalThis, {
  ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount, onScopeDispose, TextEncoder,
  authError, useCatalogoVehiculos, useBusquedaTitulares, inicialMayuscula, inicialesNombre,
  useHelpers, useDebouncedValue, ...identity, ...tax, ...catalog, ...vehicleDisplay, isValidCuit,
  useDatabase: jest.fn(),
  useAuthApi: jest.fn(),
  useOwnerAccount: jest.fn(),
  useWorkshopToast: () => ({ notify: jest.fn() }),
  useModalEscape: jest.fn(),
  definePageMeta: jest.fn(),
  navigateTo: jest.fn(),
})
config.global.config.globalProperties = { ...identity, ...tax, ...catalog, ...vehicleDisplay }
config.global.components.CommonDependentFields = DependentFields
config.global.components.CommonFormatoInvalido = FormatoInvalido
// Usamos una caja simple en lugar del contenedor visual del formulario.
// Los campos y sus validaciones siguen siendo los de la aplicación.
config.global.stubs.CommonFormPage = { template: '<section><slot /></section>' }
config.global.stubs.NuxtLink = { template: '<a><slot /></a>' }
config.global.stubs.OrdenesEditorRepuestos = true
config.global.stubs.OrdenesDetalleRepuestos = true
// Al terminar cada prueba, quitamos el formulario y volvemos al reloj normal.
enableAutoUnmount(afterEach)

beforeEach(() => {
  globalThis.useAuthApi = jest.fn()
  prepararSesionDePrueba()
})
afterEach(() => {
  jest.useRealTimers()
})
