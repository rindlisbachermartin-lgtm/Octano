# Parches de seguridad locales

Revisión: 2026-10-09. Estos son forks privados para Octano, no versiones oficiales
publicadas ni parches aprobados por los mantenedores de los paquetes.

Se conserva el código de ejecución de `braces@3.0.3` y `node-forge@1.4.0`, sus
licencias y autores. Se excluyen distribuciones precompiladas y herramientas de
desarrollo de los paquetes originales para evitar ejecutar copias sin parche.
Los forks tienen nombres privados `@octano/braces` y `@octano/node-forge` y
versiones con sufijo `-octano.1`; no se presentan como releases oficiales.
`octanoPatch.upstreamVersion` conserva la versión de origen y `revision` identifica
nuestro parche. Los cambios de código están en `patches/*.patch`. Los hashes SHA-256 de
los archivos originales están en `upstream-sha256.json`.

## Cambios

- **simple-git:** se utiliza la versión oficial 4.0.2, que corrige las alertas
  críticas. Nuxt DevTools 3 todavía importa su factory como exportación default;
  la versión 4 la exporta por nombre. `simple-git-compat` restituye esa exportación
  en ESM y CommonJS, sin modificar el código de seguridad de la versión oficial.
  Esta se instala con el alias `simple-git-modern` y sigue cubierta por npm audit.
- **braces / GHSA-vfj7-8cjw-p6xm:** límite fijo de 100 niveles de llaves y
  paréntesis durante el parseo, incluyendo estructuras mixtas. También se limita
  la recursión de compile, expand y stringify cuando reciben un AST directamente.
  Se rechazan los patrones demasiado profundos con un `SyntaxError` explícito,
  antes de agotar la pila. Los consumidores deben seguir tratando los errores de
  validación como entradas inválidas; esto no hace seguros todos los globs posibles.
  Fuente: https://github.com/micromatch/braces/issues/70
- **node-forge / GHSA-86w9-cpqp-85rv:** backport del control de cantidad de hijos
  del DigestAlgorithm anidado de la PR #1152. Solo se admite el OID y un NULL
  opcional; los elementos adicionales provocan un rechazo de la firma. Se
  conserva la validación previa del DigestInfo exterior.
  Fuente: https://github.com/digitalbazaar/forge/pull/1152/files

## Instalación y comprobación

`package.json` referencia estos forks desde `devDependencies` y los impone a
todos los consumidores mediante `overrides`. `.npmrc` activa `install-links` para
instalarlos como paquetes completos, con sus dependencias. No dependen de rutas
absolutas, modificaciones manuales de node_modules ni scripts descargados.
El postinstall ejecuta las pruebas de seguridad y detiene la instalación si una
regresión impide verificar estos controles.

```sh
npm ci
npm run test:security
npm audit
npm run build
```

Las pruebas resuelven los paquetes desde micromatch y listhen, sus consumidores
reales. Comprueban los ataques, los límites, ASTs externos, la expansión normal,
las firmas válidas, la compatibilidad con Node crypto y los certificados HTTPS.
El control negativo reconstruye las fuentes originales, verifica sus hashes y
reproduce ambos defectos (con una pila explícita de 512 KB para que la prueba de
recursión sea consistente entre sistemas operativos).

**Alcance de npm audit:** la auditoría compara nombres y versiones con avisos del registro; no analiza
el código de estos forks privados. Mantener el nombre y versión originales
produce las mismas alertas incluso después de aplicar el parche. Cero alertas del registro no certifica estos forks ni garantiza
ausencia de otras vulnerabilidades. La evidencia de estos dos arreglos son los
diffs, su procedencia y las pruebas de regresión. Mantenerlos requiere revisar
también futuras alertas publicadas para sus paquetes originales.

## Retirar los forks

Cuando existan versiones oficiales que corrijan estas alertas, quitar las dos
dependencias locales y sus overrides, actualizar Nuxt/Nitro, regenerar el lockfile
y repetir las pruebas de seguridad, TypeScript, la compilación y npm audit.
Solo después retirar vendor y su configuración de instalación.
El adaptador de simple-git se retira cuando la versión instalada de DevTools
admita sus exportaciones nuevas; no se debe volver a una versión vulnerable
para recuperar el import default.
