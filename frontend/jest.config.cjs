module.exports = {
  roots: ['<rootDir>/app', '<rootDir>/tests/unit'],
  cacheDirectory: '<rootDir>/node_modules/.cache/jest',
  testEnvironment: 'jsdom',
  testEnvironmentOptions: { customExportConditions: ['node', 'node-addons'] },
  testMatch: ['<rootDir>/tests/unit/**/*.test.js'],
  moduleNameMapper: { '^~/(.*)$': '<rootDir>/app/$1' },
  transform: {
    '^.+\\.(vue|[jt]s)$': '<rootDir>/tests/unit/transform.cjs',
  },
  setupFilesAfterEnv: ['<rootDir>/tests/unit/setup.js'],
  clearMocks: true,
  collectCoverageFrom: [
    'app/components/{clientes,vehiculos,ordenes,inventario}/*Formulario*.vue',
    'app/components/vehiculos/FormularioTitular.vue',
    'app/components/inventario/ModalRepuesto.vue',
    'app/pages/{login,registro}.vue',
  ],
  coverageDirectory: 'coverage',
}
