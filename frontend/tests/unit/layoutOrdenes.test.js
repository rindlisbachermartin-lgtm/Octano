import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import Layout from '../../app/layouts/default.vue'

test.each(['/ordenes', '/ordenes/', '/mecanico'])('la sesión real puede abrir %s a través del layout', (path) => {
  Object.assign(globalThis, {
    useRoute: () => ({ path }),
    useAuthApi: () => ({ user: ref({ rol: 'ADMIN', email: 'admin@example.test' }), workshopName: ref('Taller'), puedeAdministrar: ref(true), homePath: ref('/') }),
    useTheme: () => ({ isDark: ref(false) }),
    ownerInitials: () => 'AD',
  })
  useDatabase.mockReturnValue({ db: ref({ notifications: [], issuerVatCondition: '' }), activeOrders: ref([]), lowStock: ref([]), expireAppointmentOrders: jest.fn() })
  useOwnerAccount.mockReturnValue({ owner: ref(null), mechanic: ref(null), isMechanic: ref(false) })
  const layout = mount(Layout, {
    slots: { default: '<div data-testid="pagina">Órdenes conectadas</div>' },
    global: { mocks: { ownerInitials: () => 'AD' }, stubs: { ClientOnly: { template: '<div><slot /></div>' }, CommonThemeToggle: true } },
  })
  expect(layout.find('[data-testid="pagina"]').exists()).toBe(true)
  expect(layout.text()).not.toContain('cuando se conecte el módulo')
})
