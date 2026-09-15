import {expect, Page, test} from "@playwright/test";

const password = process.env.DEMO_PASSWORD || "Yubaba-demo-2026";
let createdOrder: { id: number; serviceName: string } | undefined;
test.afterEach(async ({request}) => {
    if (!createdOrder) return;
    const auth = await request.post('/api/auth/login', {data: {login: 'lin', password}});
    const {token} = await auth.json();
    const headers = {Authorization: 'Bearer ' + token};
    const response = await request.get('/api/orders/' + createdOrder.id, {headers});
    const order = await response.json();
    if (order.serviceName === createdOrder.serviceName) {
        if (['CREATED', 'IN_SERVICE'].includes(order.status))
            await request.post('/api/orders/' + order.id + '/cancel', {
                headers,
                data: {version: order.version, reason: 'Завершение прерванного браузерного теста'}
            });
        if (order.status === 'AWAITING_PAYMENT')
            await request.post('/api/orders/' + order.id + '/pay', {
                headers,
                data: {version: order.version, method: 'CARD'}
            });
    }
    createdOrder = undefined;
});

async function login(page: Page, login: string) {
    await page.goto("/");
    await page.getByLabel("Логин", {exact: true}).fill(login);
    await page.getByLabel("Пароль", {exact: true}).fill(password);
    await page
        .getByRole("button", {name: "Войти в систему", exact: true})
        .click();
    await expect(
        page.getByRole("button", {name: "Выйти", exact: true}),
    ).toBeVisible();
}

test("UC-1 → UC-2 → UC-3 через интерфейсы пяти ролей", async ({
                                                                  browser,
                                                              }, testInfo) => {
    const errors: string[] = [];
    const contexts = [];
    const pages: Record<string, Page> = {};
    for (const role of ["haku", "lin", "kamaji", "chihiro", "zeniba"]) {
        const context = await browser.newContext({
            viewport: {width: 1440, height: 1000},
            baseURL: process.env.BASE_URL || "http://localhost:8080",
        });
        contexts.push(context);
        const p = await context.newPage();
        p.on("pageerror", (e) => errors.push(e.message));
        p.on("response", (r) => {
            if (r.status() >= 500) errors.push(`${r.status()} ${r.url()}`);
        });
        pages[role] = p;
        await login(p, role);
    }
    const manager = pages.haku;
    await expect(
        manager.getByRole("heading", {name: "Купальни сегодня"}),
    ).toBeVisible();
    await manager.screenshot({
        path: testInfo.outputPath("manager.png"),
        fullPage: true,
    });
    const suffix = Date.now().toString();
    const service = "Лесное парение " + suffix;
    await manager
        .getByRole("button", {name: "Шаблоны услуг", exact: true})
        .click();
    await manager
        .getByRole("button", {name: "Создать шаблон", exact: true})
        .click();
    const editor = manager.getByRole("dialog");
    await editor.getByLabel("Название услуги", {exact: true}).fill(service);
    await editor
        .getByLabel("Тип купальни", {exact: true})
        .selectOption({label: "Травяная купальня"});
    await editor.getByLabel("Перерыв банщика, мин").fill("0");
    await editor.getByLabel("Стоимость услуг, ₽").fill("500");
    await editor
        .getByLabel("Пошаговый рецепт воды")
        .fill("1. Нагреть воду до 40 °C.\n2. Добавить полынь и перемешать.");
    await editor.getByRole("button", {name: "Добавить ингредиент"}).click();
    const ingredient = editor.getByLabel("Ингредиент 1", {exact: true});
    const ingredientId = await ingredient.locator('option').filter({hasText: 'Полынь'}).getAttribute('value');
    await ingredient.selectOption(ingredientId!);
    await editor.getByLabel("Количество", {exact: true}).fill("10");
    await editor.getByRole("button", {name: "Сохранить шаблон"}).click();
    await expect(editor).not.toBeVisible();
    const admin = pages.lin;
    const guest = "Лесной гость " + suffix;
    await admin.getByRole("button", {name: "Гости", exact: true}).click();
    await admin.getByRole("button", {name: "Новый гость", exact: true}).click();
    await admin.getByLabel("Имя гостя").fill(guest);
    await admin
        .getByLabel("Телефон, почта или другой контакт")
        .fill("guest-" + suffix + "@example.test");
    await admin
        .getByLabel("Предпочтения и примечания")
        .fill("Тест полного сценария");
    await admin.getByRole("button", {name: "Сохранить гостя"}).click();
    await expect(admin.getByRole("dialog")).not.toBeVisible();
    await admin.getByRole("button", {name: "Заказы", exact: true}).click();
    await admin.getByRole("button", {name: "Новый заказ", exact: true}).click();
    const form = admin.getByRole("dialog");
    await form
        .getByLabel("Гость", {exact: true})
        .selectOption({label: guest + " · guest-" + suffix + "@example.test"});
    await form
        .getByLabel("Шаблон услуги", {exact: true})
        .selectOption({label: service});
    await expect(
        form.getByRole("button", {name: "Сохранить заказ", exact: true}),
    ).toBeEnabled();
    await form.screenshot({path: testInfo.outputPath("order-form.png")});
    await form
        .getByRole("button", {name: "Сохранить заказ", exact: true})
        .click();
    await expect(form).not.toBeVisible();
    const row = admin.getByRole("row").filter({hasText: guest});
    await expect(row).toBeVisible();
    await row.getByRole("button").click();
    const detail = admin.getByRole("dialog");
    const title = await detail
        .getByRole("heading", {name: /Заказ №/})
        .textContent();
    const id = Number(title!.match(/\d+/)![0]);
    createdOrder = {id, serviceName: service};
    await detail.getByRole("button", {name: "Запустить обслуживание"}).click();
    // Refresh may replace the dialog after a successful mutation; order state remains in the table.
    await expect(admin.getByRole("row").filter({hasText: guest})).toContainText(
        "В обслуживании",
    );
    const bath = pages.chihiro;
    const card = bath.locator("article").filter({hasText: service});
    await expect(card).toBeVisible();
    await expect(
        card.getByRole("button", {name: "Ожидаем готовность воды"}),
    ).toBeDisabled();
    const boiler = pages.kamaji;
    const task = boiler.locator("article").filter({hasText: service});
    await expect(task).toContainText("Полынь");
    await boiler.screenshot({
        path: testInfo.outputPath("boiler.png"),
        fullPage: true,
    });
    await task.getByRole("button", {name: "Вода готова"}).click();
    await expect(
        card.getByRole("button", {name: "Начать услугу"}),
    ).toBeEnabled();
    await card.getByRole("button", {name: "Начать услугу"}).click();
    await expect(
        card.getByRole("button", {name: "Завершить услугу"}),
    ).toBeVisible();
    await bath.setViewportSize({width: 390, height: 844});
    await bath.screenshot({
        path: testInfo.outputPath("attendant-mobile.png"),
        fullPage: true,
        animations: "disabled",
    });
    expect(
        await bath.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
        ),
    ).toBeTruthy();
    await card.getByRole("button", {name: "Завершить услугу"}).click();
    await expect(admin.getByRole("row").filter({hasText: guest})).toContainText(
        "Ожидает оплаты",
    );
    if (await admin.getByRole("dialog").isVisible())
        await admin
            .getByRole("dialog")
            .getByRole("button", {name: "Закрыть", exact: true})
            .click();
    await admin
        .getByRole("row")
        .filter({hasText: guest})
        .getByRole("button")
        .click();
    await admin.getByRole("button", {name: /Принять оплату/}).click();
    await admin.getByLabel("Способ оплаты", {exact: true}).selectOption("CARD");
    await admin
        .getByRole("button", {name: "Подтвердить оплату и закрыть"})
        .click();
    await expect(admin.getByRole("row").filter({hasText: guest})).toContainText(
        "Закрыт",
    );
    const accountant = pages.zeniba;
    await accountant
        .getByLabel("Услуга", {exact: true})
        .selectOption({label: service});
    await accountant
        .getByLabel("Способ оплаты", {exact: true})
        .selectOption("CARD");
    await accountant.getByRole("button", {name: "Сформировать отчёт"}).click();
    await expect(
        accountant.getByRole("row").filter({hasText: service}),
    ).toContainText("Карта");
    await accountant
        .getByLabel("Сохранить параметры сформированного отчёта")
        .fill("Карточные платежи " + suffix);
    await accountant.getByRole("button", {name: "Сохранить шаблон"}).click();
    await accountant
        .getByLabel("Сохранённый шаблон · запуск одним выбором")
        .selectOption({label: "Карточные платежи " + suffix});
    await expect(
        accountant.getByRole("row").filter({hasText: service}),
    ).toContainText("№ " + id);
    await accountant.screenshot({
        path: testInfo.outputPath("accountant.png"),
        fullPage: true,
    });
    const downloading = accountant.waitForEvent("download");
    await accountant.getByRole("button", {name: "Скачать CSV"}).click();
    expect((await downloading).suggestedFilename()).toBe("yubaba-report.csv");
    expect(errors).toEqual([]);
    for (const context of contexts) await context.close();
});

test("Вход и адаптация страницы на узком экране", async ({
                                                             page,
                                                         }, testInfo) => {
    await page.setViewportSize({width: 390, height: 844});
    await page.goto("/");
    await expect(
        page.getByRole("heading", {name: "Добро пожаловать"}),
    ).toBeVisible();
    expect(
        await page.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
        ),
    ).toBeTruthy();
    await page.getByLabel("Логин", {exact: true}).fill("invalid");
    await page.getByLabel("Пароль", {exact: true}).fill("incorrect-password");
    await page.getByRole("button", {name: "Войти в систему"}).click();
    await expect(page.getByRole("alert")).toContainText(
        "Неверный логин или пароль",
    );
    await page.screenshot({
        path: testInfo.outputPath("login-mobile.png"),
        fullPage: true,
    });
});
