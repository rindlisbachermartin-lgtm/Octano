module.exports = {
  roots: ['<rootDir>/app', '<rootDir>/tests/unit'],
  cacheDirectory: '<rootDir>/node_modules/.cache/jest',
  testEnvironment: 'jsdom',
  testEnvironmentOptions: { customExportConditions: ['node', 'node-addons'] },
  testMatch: ['<rootDir>/tests/unit/**/*.test.js'],
  moduleNameMapper: { '^~/(.*)$': '<rootDir>/app/$1' },
  transform: {
    '^.+\\.vue$': '@vue/vue3-jest',
    '^.+\\.[jt]s$': ['babel-jest', {
      presets: [
        ['@babel/preset-env', { targets: { node: 'current' } }],
        '@babel/preset-typescript',
      ],
    }],
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
