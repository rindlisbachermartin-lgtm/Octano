import { config, enableAutoUnmount } from '@vue/test-utils'
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useHelpers } from '../../app/composables/useHelpers'
import { useDebouncedValue } from '../../app/composables/useDebouncedValue'
import * as identity from '../../app/utils/identityDocument'
import * as tax from '../../app/utils/invoiceTax'
import * as catalog from '../../app/utils/vehicleCatalog'
import { isValidCuit } from '../../app/utils/ownerValidation'
import DependentFields from '../../app/components/common/DependentFields.vue'

// Nuxt agrega estos autoimports al compilar. Jest monta los componentes sin Nuxt.
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
// Se aísla el contenedor con Teleport; los campos y sus validaciones son reales.
config.global.stubs.CommonFormPage = { template: '<section><slot /></section>' }
config.global.stubs.NuxtLink = { template: '<a><slot /></a>' }
config.global.stubs.OrdenesEditorRepuestos = true
config.global.stubs.OrdenesDetalleRepuestos = true
enableAutoUnmount(afterEach)
afterEach(() => { jest.useRealTimers() })
