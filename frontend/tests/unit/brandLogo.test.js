import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { mount } from '@vue/test-utils'

const BrandLogo = require('../../app/components/common/BrandLogo.vue').default

test('cada marca del catálogo tiene un logo local', () => {
  const marcas = ['ALFA ROMEO', 'AUDI', 'BAIC', 'BMW', 'CHERY', 'CHEVROLET', 'CHRYSLER', 'CITROEN', 'DAEWOO', 'DAIHATSU', 'DODGE', 'DS', 'FIAT', 'FORD', 'GMC CHEVETTE', 'HONDA', 'HUMMER', 'HYUNDAI', 'ISUZU', 'IVECO', 'JEEP', 'KIA', 'LAND ROVER', 'MAZDA', 'MERCEDES BENZ', 'MG', 'MINI', 'MITSUBISHI', 'NISSAN', 'OPEL', 'PEUGEOT', 'PORSCHE', 'RAM', 'RENAULT', 'ROVER', 'SCANIA', 'SEAT', 'SMART', 'SUBARU', 'SUZUKI', 'TOYOTA', 'VOLKSWAGEN', 'VOLVO']
  for (const brand of marcas) {
    const wrapper = mount(BrandLogo, { props: { brand } })
    const img = wrapper.find('img')
    const url = img.exists() ? img.attributes('src') : wrapper.get('.brand-logo-symbol').element.style.maskImage.match(/url\(["']?(.*?)["']?\)/)?.[1]
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe(`Logo de ${brand}`)
    expect(url).toBeTruthy()
    expect(existsSync(join(process.cwd(), 'public', url))).toBe(true)
  }
})
