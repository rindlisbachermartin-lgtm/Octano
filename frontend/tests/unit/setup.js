import { config, enableAutoUnmount } from '@vue/test-utils'
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useHelpers } from '../../app/composables/useHelpers'
import { useDebouncedValue } from '../../app/composables/useDebouncedValue'
import * as identity from '../../app/utils/identityDocument'
import * as tax from '../../app/utils/invoiceTax'
import * as catalog from '../../app/utils/vehicleCatalog'
import { isValidCuit } from '../../app/utils/ownerValidation'
import DependentFields from '../../app/components/common/DependentFields.vue'

// La aplicación ya conoce estas funciones. Acá las agregamos para poder probarla sin iniciar Nuxt.
Object.assign(globalThis, {
  ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount,
  useHelpers, useDebouncedValue, ...identity, ...tax, ...catalog, isValidCuit,
  useDatabase: jest.fn(),
  useOwnerAccount: jest.fn(),
  useWorkshopToast: () => ({ notify: jest.fn() }),
  useModalEscape: jest.fn(),
  definePageMeta: jest.fn(),
  navigateTo: jest.fn(),
})
config.global.config.globalProperties = { ...identity, ...tax, ...catalog }
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
