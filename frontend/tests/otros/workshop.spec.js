import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.evaluate(() => localStorage.clear());
  await page.reload();
});

test("work order consumes compatible stock, saves tasks, finishes and releases its bay", async ({
  page,
}) => {
  await page.getByRole("button", { name: "PUESTO 01" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await page.getByLabel("Repuesto compatible").selectOption("1");
  await dialog.getByRole("button", { name: "Imputar", exact: true }).click();
  await expect(
    dialog.getByText("Filtro de aceite", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("Nueva tarea").fill("Prueba de ruta");
  await dialog.getByRole("button", { name: "Agregar", exact: true }).click();
  await page.getByLabel("Prueba de ruta", { exact: true }).check();
  await page
    .getByLabel("Observaciones", { exact: true })
    .fill("Control final realizado.");
  await dialog.getByRole("button", { name: "Finalizar trabajo" }).click();
  await expect(dialog.getByText("Finalizado", { exact: true })).toBeVisible();
  await dialog.getByRole("button", { name: "Registrar entrega" }).click();
  const data = await page.evaluate(() =>
    JSON.parse(localStorage.getItem("punto-motor-v1")),
  );
  expect(data.parts.find((p) => p.id === 1).stock).toBe(2);
  expect(data.orders.find((o) => o.id === 1048).bay).toBeNull();
  expect(data.notifications[0].detail).toContain("simulado");
  await page.reload();
  await expect(page.getByRole("button", { name: /PUESTO 01/ })).toContainText(
    "Puesto disponible",
  );
});

test("appointment prevents overlap and check-in creates an order", async ({
  page,
}) => {
  await page.getByRole("button", { name: "Agenda", exact: true }).click();
  await page.getByRole("button", { name: "Nuevo turno", exact: true }).click();
  await page
    .getByRole("combobox", { name: "Hora", exact: true })
    .selectOption("14:00");
  await page.getByLabel("Motivo de la visita").fill("Revisión de luces");
  await page.getByRole("button", { name: "Confirmar turno" }).click();
  await expect(page.getByRole("alert")).toContainText("ya está ocupado");
  await page
    .getByRole("combobox", { name: "Hora", exact: true })
    .selectOption("11:00");
  await page.getByRole("button", { name: "Confirmar turno" }).click();
  const appointment = page
    .locator(".appointment")
    .filter({ hasText: "Revisión de luces" });
  await appointment.getByRole("button", { name: "Registrar ingreso" }).click();
  await expect(appointment).toContainText("En Taller");
  const data = await page.evaluate(() =>
    JSON.parse(localStorage.getItem("punto-motor-v1")),
  );
  expect(data.orders.some((o) => o.service === "Revisión de luces")).toBe(true);
});

test("client, vehicle, quote conversion and payment are persisted", async ({
  page,
}) => {
  await page.getByRole("button", { name: "Clientes", exact: true }).click();
  await page.getByRole("button", { name: "Nuevo cliente" }).click();
  await page.getByLabel("Nombre o razón social").fill("Ana Torres");
  await page.getByLabel("DNI / CUIT / CUIL").fill("40123456");
  await page.getByRole("button", { name: "Guardar", exact: true }).click();
  await expect(page.getByText("Ana Torres", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Vehículos", exact: true }).click();
  await page.getByRole("button", { name: "Nuevo vehículo" }).click();
  await page
    .getByRole("combobox", { name: "Cliente", exact: true })
    .selectOption({ label: "Ana Torres" });
  await page.getByLabel("Patente", { exact: true }).fill("AG 123 TT");
  await page.getByLabel("Marca", { exact: true }).fill("Fiat");
  await page.getByLabel("Modelo", { exact: true }).fill("Cronos");
  await page.getByRole("button", { name: "Guardar", exact: true }).click();
  await expect(page.getByText("Fiat Cronos", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Presupuestos", exact: true }).click();
  await page
    .getByRole("button", { name: "Aprobar y crear orden" })
    .first()
    .click();
  await expect(
    page.getByRole("button", { name: "Orden de trabajo creada" }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Facturación", exact: true }).click();
  await page.getByRole("button", { name: "Registrar cobro" }).click();
  const data = await page.evaluate(() =>
    JSON.parse(localStorage.getItem("punto-motor-v1")),
  );
  expect(data.invoices[0].status).toBe("Cobrado");
  expect(data.quotes[0].status).toBe("Convertido");
});

test("mobile navigation, QR view and all screens fit the viewport", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  for (const name of [
    "Resumen",
    "Agenda",
    "Órdenes de trabajo",
    "Clientes",
    "Vehículos",
    "Inventario",
    "Presupuestos",
    "Facturación",
    "Reportes",
  ]) {
    await page.getByRole("button", { name: "Abrir menú" }).click();
    await page
      .locator("nav")
      .getByRole("button", {
        name: name === "Órdenes de trabajo" ? /Órdenes de trabajo/ : name,
        exact: name !== "Órdenes de trabajo",
      })
      .click();
    await expect(page.locator("h1")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  }
  await page.getByRole("button", { name: "Abrir menú" }).click();
  await page.getByRole("button", { name: "Vehículos", exact: true }).click();
  await page.locator(".vehicle-card").first().click();
  await expect(page.getByAltText("QR de la ficha técnica")).toBeVisible();
  await page.getByRole("link", { name: "Ver ficha pública" }).click();
  await expect(
    page.getByRole("heading", { name: "Historial de servicios" }),
  ).toBeVisible();
  await expect(page.getByText("Lucía Fernández")).toHaveCount(0);
});

test("all views and forms render without browser errors; Escape restores focus", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.getByRole("button", { name: "Nueva orden" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByRole("button", { name: "Nueva orden" })).toBeFocused();
  for (const [name, action] of [
    ["Inventario", "Nuevo repuesto"],
    ["Presupuestos", "Nuevo presupuesto"],
    ["Facturación", "Nuevo comprobante"],
  ]) {
    await page.getByRole("button", { name, exact: true }).click();
    await page.getByRole("button", { name: action, exact: true }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.keyboard.press("Escape");
  }
  expect(errors).toEqual([]);
});

test("primary screens and order dialog have no detected WCAG A/AA violations", async ({
  page,
}) => {
  test.setTimeout(60000);
  for (const name of [
    "Resumen",
    "Agenda",
    "Órdenes de trabajo",
    "Clientes",
    "Vehículos",
    "Inventario",
    "Presupuestos",
    "Facturación",
    "Reportes",
  ]) {
    await page
      .locator("nav")
      .getByRole("button", { name: new RegExp(`^${name}`) })
      .click();
    const result = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa"])
      .analyze();
    expect(
      result.violations,
      `${name}: ${JSON.stringify(result.violations.map((v) => ({ id: v.id, targets: v.nodes.map((n) => n.target) })))}`,
    ).toEqual([]);
  }
  await page
    .locator("nav")
    .getByRole("button", { name: "Resumen", exact: true })
    .click();
  await page.getByRole("button", { name: "PUESTO 01" }).click();
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa"])
    .analyze();
  expect(result.violations).toEqual([]);
});
