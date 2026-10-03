<script setup lang="ts">
const route = useRoute()
useHead({ htmlAttrs: { 'data-auth-login': computed(() => route.path === '/login' ? 'true' : null) } })
</script>

<template>
  <div class="auth-shell" :class="{ 'auth-login': route.path === '/login' }">
    <header class="auth-topbar">
      <NuxtLink to="/login" class="auth-brand" aria-label="Octano, iniciar sesión"><CommonOctanoLogo />octano</NuxtLink>
      <div class="auth-topbar-actions">
        <span class="auth-demo-label">MAQUETA INTERACTIVA</span>
      </div>
    </header>
    <main class="auth-content">
      <aside class="auth-story" aria-label="Sobre Octano">
        <span class="auth-kicker">TU TALLER, EN ORDEN.</span>
        <h1>Menos vueltas.<br />Más taller.</h1>
        <p>De la primera revisión a la última factura.<br />Todo conectado, en un solo lugar.</p>
        <AuthSystemPreview />
        <div class="auth-story-foot"><span>Diseñado para tu día a día.</span><span>Hecho para talleres.</span></div>
      </aside>
      <section class="auth-form-side"><slot /></section>
    </main>
    <footer class="auth-bottom"><span>© {{ new Date().getFullYear() }} Octano</span><span>Gestión simple. Trabajo bien hecho.</span></footer>
  </div>
</template>

<style>
.auth-shell { --auth-ink: #f2f4f7; --auth-muted: #a1a7b0; --auth-line: #34383e; --auth-surface: #212429; min-height: 100dvh; background: #17191d; color: var(--auth-ink); color-scheme: dark; font-family: 'Public Sans', system-ui, sans-serif; }
.auth-topbar { max-width: 1280px; margin: auto; padding: 28px 48px; display: flex; justify-content: space-between; align-items: center; }
.auth-brand { display: inline-flex; align-items: center; gap: 12px; font-size: 25px; font-weight: 750; letter-spacing: -.9px; color: var(--auth-ink); }
.auth-topbar-actions { display: flex; align-items: center; gap: 20px; }
.auth-demo-label { color: var(--auth-muted); font-size: 10px; letter-spacing: 1.2px; }
.auth-content { max-width: 1184px; margin: 12px auto 0; display: grid; grid-template-columns: 1fr 1fr; min-height: 650px; }
.auth-story { background: #262626; color: #f7f9fb; border-radius: 26px; padding: 48px; display: flex; flex-direction: column; position: relative; overflow: hidden; }
.auth-story::after { content: ''; position: absolute; width: 450px; height: 450px; border: 1px solid #ffffff06; border-radius: 50%; right: -220px; bottom: -230px; pointer-events: none; }
.auth-kicker { display: flex; align-items: center; gap: 9px; color: #b3c1c9; font-size: 10px; letter-spacing: 1.7px; font-weight: 600; }
.auth-story h1 { margin: 30px 0 20px; font-size: clamp(38px, 4vw, 54px); letter-spacing: -2.2px; line-height: 1.08; font-weight: 650; }
.auth-story > p { color: #b3bfc7; font-size: 14px; line-height: 1.85; margin: 0; }
.auth-story-foot { margin-top: auto; padding-top: 28px; display: flex; justify-content: space-between; color: #7e929f; font-size: 9px; }
.auth-form-side { display: flex; align-items: center; justify-content: center; padding: 44px 64px; }
.auth-form { width: 100%; max-width: 390px; }
.auth-form .auth-eyebrow { font-size: 10px; letter-spacing: 1.5px; color: #4872bc; font-weight: 650; display: block; margin-bottom: 14px; }
.auth-form h2 { font-size: 30px; letter-spacing: -1.1px; line-height: 1.15; margin: 0 0 12px; font-weight: 650; color: var(--auth-ink) !important; }
.auth-description { font-size: 13px; line-height: 1.7; color: var(--auth-muted); margin: 0 0 28px; }
.auth-fields { display: flex; flex-direction: column; gap: 17px; }
.auth-field { display: flex; flex-direction: column; gap: 8px; font-size: 11px; color: var(--auth-ink); font-weight: 550; }
.auth-shell .auth-field input, .auth-shell .auth-field select { margin: 0; padding: 12px 13px; border: 1px solid var(--auth-line) !important; background: var(--auth-surface) !important; color: var(--auth-ink) !important; font-size: 13px; min-height: 44px; border-radius: 9px; }
.auth-field small { font-size: 10px; color: var(--auth-muted); line-height: 1.6; font-weight: 400; }
.auth-shell .auth-field input:focus, .auth-shell .auth-field select:focus { border-color: #7798ea !important; box-shadow: 0 0 0 3px #4b7cf415; }
.auth-password { position: relative; }
.auth-password input { padding-right: 44px !important; }
.auth-password > button { position: absolute; right: 5px; top: 4px; height: 36px; width: 36px; border: 0; background: transparent; display: grid; place-items: center; cursor: pointer; color: var(--auth-muted); }
.auth-options { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 17px 0 22px; font-size: 11px; }
.auth-checkbox { display: flex; align-items: center; gap: 8px; font-size: 11px; font-weight: 400; color: var(--auth-muted); }
.auth-checkbox input { margin: 0; width: 15px; height: 15px; }
.auth-submit { width: 100%; min-height: 45px; display: flex; justify-content: center; align-items: center; gap: 9px; background: #2865e8; border: 1px solid #2865e8; border-radius: 9px; color: white; font-size: 12px; font-weight: 600; cursor: pointer; transition: background 150ms ease, transform 130ms cubic-bezier(.23,1,.32,1); }
.auth-submit:active:not(:disabled) { transform: scale(.98); }
.auth-submit:disabled { opacity: .6; cursor: wait; }
.auth-shell a:focus-visible, .auth-shell button:focus-visible { outline: 3px solid #7199ef; outline-offset: 3px; }
.auth-secondary { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 44px; padding: 10px; border: 1px solid var(--auth-line); background: var(--auth-surface); border-radius: 9px; font-size: 12px; color: var(--auth-ink); cursor: pointer; width: 100%; }
.auth-link { color: #3872df; font-weight: 550; font-size: 11px; }
.auth-switch { text-align: center; color: var(--auth-muted); font-size: 11px; margin: 22px 0; }
.auth-divider { display: flex; align-items: center; gap: 12px; font-size: 9px; color: var(--auth-muted); margin: 25px 0 18px; }
.auth-divider::before, .auth-divider::after { content: ''; height: 1px; background: var(--auth-line); flex: 1; }
.auth-disclosure { font-size: 10px; line-height: 1.65; color: var(--auth-muted); margin: 19px 0 0; text-align: center; }
.auth-error { border: 1px solid #e88b8b44; background: #e55e5e0b; color: #f1a6a6; padding: 11px 12px; border-radius: 8px; font-size: 11px; line-height: 1.5; margin: 15px 0; }
.auth-bottom { display: flex; justify-content: space-between; margin: 0 auto; max-width: 1280px; padding: 24px 48px; font-size: 9px; color: var(--auth-muted); }
.auth-two-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.auth-steps { display: flex; gap: 7px; margin-bottom: 25px; }
.auth-steps span { flex: 1; height: 3px; border-radius: 3px; background: var(--auth-line); }
.auth-steps .active { background: #477bea; }
@media (hover: hover) and (pointer: fine) { .auth-submit:hover:not(:disabled) { background: #1e58d2; } .auth-link:hover { text-decoration: underline; } }
@media (max-width: 1000px) { .auth-content { margin: 12px 24px 0; } .auth-story { padding: 32px; } .auth-form-side { padding: 32px; } }
@media (max-width: 760px) { .auth-topbar { padding: 22px 24px; } .auth-content { display: block; margin: 0; min-height: auto; } .auth-story { display: none; } .auth-form-side { padding: 38px 24px 44px; } .auth-bottom { padding: 20px 24px; } .auth-demo-label { font-size: 8px; letter-spacing: .7px; } }
@media (prefers-reduced-motion: reduce) { .auth-submit { transition: background 150ms ease; } .auth-submit:active:not(:disabled) { transform: none; } }
html[data-keyboard] .auth-submit { transition: none; }
.auth-login { height: 100dvh; min-height: 0; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; }
html[data-auth-login="true"] { overflow-y: auto; }
.auth-login .auth-topbar, .auth-login .auth-bottom { width: 100%; }
.auth-login .auth-content { min-height: 0; width: calc(100% - 96px); margin: 0 auto; }
.auth-login .auth-story { min-height: 0; padding: 32px; }
.auth-login .auth-story h1 { font-size: clamp(32px, 5.5vh, 48px); margin: 22px 0 16px; }
.auth-login .auth-system-preview { display: flex; flex-direction: column; flex: 1; min-height: 0; margin: 24px -8px 0; }
.auth-login .preview-frame { flex: 1; min-height: 0; aspect-ratio: auto; }
.auth-login .preview-frame img { object-fit: contain; }
.auth-login .auth-story-foot { padding-top: 20px; }
.auth-login .auth-form-side { min-width: 0; min-height: 0; padding: 20px 64px; }
@media (max-width: 1000px) { .auth-login .auth-content { width: calc(100% - 48px); } .auth-login .auth-form-side { padding: 20px 32px; } }
@media (max-width: 760px) { .auth-login .auth-content { display: flex; width: 100%; } .auth-login .auth-form-side { width: 100%; padding: 16px 24px; } }
@media (max-height: 740px) {
  .auth-login .auth-topbar { padding-top: 16px; padding-bottom: 16px; }
  .auth-login .auth-bottom { padding-top: 12px; padding-bottom: 12px; }
  .auth-login .auth-description { margin-bottom: 16px; }
  .auth-login .auth-fields { gap: 12px; }
  .auth-login .auth-options { margin: 12px 0; }
  .auth-login .auth-switch { margin: 14px 0; }
  .auth-login .auth-divider { margin: 14px 0 12px; }
  .auth-login .auth-disclosure { margin-top: 12px; }
  .auth-login .auth-error { margin: 10px 0; padding: 8px 12px; }
}
@media (max-height: 640px) {
  .auth-login .auth-topbar { padding-top: 10px; padding-bottom: 10px; }
  .auth-login .auth-bottom, .auth-login .auth-description { display: none; }
  .auth-login .auth-form .auth-eyebrow { margin-bottom: 8px; }
  .auth-login .auth-form-side { padding-top: 12px; padding-bottom: 12px; }
  .auth-login .auth-story { padding: 24px; }
  .auth-login .auth-story h1 { margin-top: 14px; }
}
</style>
