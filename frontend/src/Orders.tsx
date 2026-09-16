import {useEffect, useState} from "react";
import {api, query, useData} from "./api";
import {Catalog, Client, Composition, Order, Role, Status} from "./types";
import {Badge, date, Empty, Field, Loading, methods, Modal, money, Notice, statuses,} from "./ui";
import {blankComposition, CompositionEditor, compositionOf,} from "./CompositionEditor";

export type Perform = (
    job: () => Promise<unknown>,
    message?: string,
) => Promise<boolean>;

export function Orders({
                           cat,
                           role,
                           perform,
                           busy,
                       }: {
    cat: Catalog;
    role: Role;
    perform: Perform;
    busy: boolean;
}) {
    const [q, setQ] = useState("");
    const [status, setStatus] = useState("");
    const [page, setPage] = useState(0);
    const [form, setForm] = useState<Order | null | undefined>();
    const [selected, setSelected] = useState<number>();
    const {data, error, loading} = useData<{
        items: Order[];
        total: number;
        pages: number;
    }>("/orders" + query({q, status, page}), 3000);
    const clients = useData<Client[]>(
        role === "ADMIN" || role === "MANAGER" ? "/clients" : null,
    );
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Заказы гостей</h2>
                </div>
                {role === "ADMIN" && (
                    <button onClick={() => setForm(null)}>
                         Новый заказ
                    </button>
                )}
            </div>
            <div className="panel">
                <div className="toolbar">
                    <div className="search">

                        <input
                            aria-label="Поиск заказов"
                            placeholder="Номер, гость, контакт или услуга"
                            value={q}
                            onChange={(e) => {
                                setQ(e.target.value);
                                setPage(0);
                            }}
                        />
                    </div>
                    <select
                        aria-label="Статус заказа"
                        value={status}
                        onChange={(e) => {
                            setStatus(e.target.value);
                            setPage(0);
                        }}
                    >
                        <option value="">Все статусы</option>
                        {Object.entries(statuses).map(([s, l]) => (
                            <option key={s} value={s}>
                                {l}
                            </option>
                        ))}
                    </select>
                    <span className="muted">{data?.total ?? "–"} заказов</span>
                </div>
                <Notice error={error}/>
                {loading ? (
                    <Loading/>
                ) : !data?.items.length ? (
                    <Empty>Заказов пока нет. Создайте первый или измените фильтры.</Empty>
                ) : (
                    <div className="table-scroll">
                        <table>
                            <thead>
                            <tr>
                                <th>Заказ / гость</th>
                                <th>Услуга</th>
                                <th>Статус</th>
                                <th>Стоимость</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            {data.items.map((o) => (
                                <tr key={o.id}>
                                    <td>
                                        <strong>
                                            № {String(o.id).padStart(4, "0")} ·{" "}
                                            {clients.data?.find((c) => c.id === o.clientId)?.name ||
                                                "Гость № " + o.clientId}
                                        </strong>
                                        <small>
                                            {date(o.createdAt)} · {o.visitors} гостей
                                        </small>
                                    </td>
                                    <td>
                                        {o.serviceName}
                                        <small>{o.bathType}</small>
                                    </td>
                                    <td>
                                        <Badge status={o.status}/>
                                    </td>
                                    <td className="amount">{money(o.total)}</td>
                                    <td>
                                        <button
                                            className="text-button"
                                            aria-label={"Открыть заказ " + o.id}
                                            onClick={() => setSelected(o.id)}
                                        >
                                            Открыть
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
                <div className="pagination">
                    <button
                        className="secondary"
                        disabled={page === 0}
                        onClick={() => setPage((p) => p - 1)}
                    >
                        Назад
                    </button>
                    <span>
            Страница {page + 1} из {Math.max(1, data?.pages || 0)}
          </span>
                    <button
                        className="secondary"
                        disabled={!data || page + 1 >= data.pages}
                        onClick={() => setPage((p) => p + 1)}
                    >
                        Далее
                    </button>
                </div>
            </div>
            {form !== undefined && (
                <OrderForm
                    cat={cat}
                    clients={clients.data || []}
                    order={form}
                    perform={perform}
                    busy={busy}
                    onClose={() => setForm(undefined)}
                />
            )}
            {selected !== undefined && (
                <OrderDetails
                    id={selected}
                    cat={cat}
                    role={role}
                    perform={perform}
                    busy={busy}
                    onClose={() => setSelected(undefined)}
                    onEdit={(o) => {
                        setSelected(undefined);
                        setForm(o);
                    }}
                />
            )}
        </>
    );
}

function OrderForm({
                       cat,
                       clients,
                       order,
                       perform,
                       busy,
                       onClose,
                   }: {
    cat: Catalog;
    clients: Client[];
    order: Order | null;
    perform: Perform;
    busy: boolean;
    onClose: () => void;
}) {
    const [clientId, setClient] = useState(order?.clientId || 0);
    const [templateId, setTemplate] = useState<number | null>(
        order?.templateId || cat.templates[0]?.id || null,
    );
    const [c, setC] = useState<Composition>(
        order
            ? compositionOf(order)
            : cat.templates[0]
                ? compositionOf(cat.templates[0])
                : blankComposition,
    );
    const [visitors, setVisitors] = useState(order?.visitors || 1);
    const [priority, setPriority] = useState(order?.priority || 0);
    const [preview, setPreview] = useState<{ total: number; issues: string[] }>();
    const [error, setError] = useState("");
    const [checking, setChecking] = useState(false);
    const input = {
        clientId,
        templateId,
        visitors,
        priority,
        composition: c,
        version: order?.version,
    };
    useEffect(() => {
        let live = true;
        setPreview(undefined);
        setError("");
        if (!clientId) return;
        setChecking(true);
        const timer = setTimeout(() => {
            api<{ total: number; issues: string[] }>("/orders/preview", "POST", input)
                .then((r) => {
                    if (live) setPreview(r);
                })
                .catch((e) => {
                    if (live) setError(e.message);
                })
                .finally(() => {
                    if (live) setChecking(false);
                });
        }, 350);
        return () => {
            live = false;
            clearTimeout(timer);
        };
    }, [clientId, templateId, visitors, priority, c]);
    const submit = async () => {
        setError("");
        const ok = await perform(
            () =>
                api(
                    order ? "/orders/" + order.id : "/orders",
                    order ? "PUT" : "POST",
                    input,
                ),
            "Заказ сохранён",
        );
        if (ok) onClose();
    };
    return (
        <Modal
            title={order ? "Редактирование заказа № " + order.id : "Новый заказ"}
            onClose={onClose}
        >
            <form
                onSubmit={(e) => {
                    e.preventDefault();
                    void submit();
                }}
            >
                <div className="form-grid">
                    <Field label="Гость">
                        <select
                            required
                            value={clientId || ""}
                            onChange={(e) => setClient(Number(e.target.value))}
                        >
                            <option value="">Выберите гостя</option>
                            {clients.map((c) => (
                                <option key={c.id} value={c.id}>
                                    {c.name} · {c.contact}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Шаблон услуги">
                        <select
                            value={templateId || ""}
                            onChange={(e) => {
                                const t = cat.templates.find(
                                    (t) => t.id === Number(e.target.value),
                                );
                                setTemplate(t?.id || null);
                                if (t) setC(compositionOf(t));
                            }}
                        >
                            <option value="">Индивидуальный состав</option>
                            {cat.templates.map((t) => (
                                <option key={t.id} value={t.id}>
                                    {t.name}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Число гостей">
                        <input
                            required
                            type="number"
                            min="1"
                            max="100"
                            value={visitors}
                            onChange={(e) => setVisitors(Number(e.target.value))}
                        />
                    </Field>
                    <Field label="Приоритет котельной">
                        <select
                            value={priority}
                            onChange={(e) => setPriority(Number(e.target.value))}
                        >
                            <option value="0">Обычный</option>
                            <option value="5">Повышенный</option>
                            <option value="10">Срочный</option>
                        </select>
                    </Field>
                </div>
                {!clients.length && (
                    <p className="notice">
                        Сначала зарегистрируйте гостя в разделе «Гости».
                    </p>
                )}
                <div className="recipe-summary">

                    <div>
                        <strong>{c.name || "Индивидуальная услуга"}</strong>
                        <p>
                            {c.durationMinutes} мин · {c.temperature} °C · {c.attendants}{" "}
                            банщик(а)
                        </p>
                    </div>
                </div>
                <details open={!templateId}>
                    <summary>Состав заказа и рецепт – изменить</summary>
                    <CompositionEditor value={c} onChange={setC} cat={cat}/>
                </details>
                <Notice error={error}/>
                {preview?.issues.map((issue) => (
                    <div className="notice error" key={issue}>
                        {issue}
                    </div>
                ))}
                <footer className="form-footer">
                    <div>
                        <small>Стоимость всего заказа</small>
                        <strong>
                            {checking
                                ? "Проверяем ресурсы…"
                                : preview
                                    ? money(preview.total)
                                    : "Выберите гостя"}
                        </strong>
                    </div>
                    <button
                        disabled={busy || checking || !preview || !!preview.issues.length}
                        type="submit"
                    >
                        {busy ? "Сохраняем…" : "Сохранить заказ"}
                    </button>
                </footer>
            </form>
        </Modal>
    );
}

export function Recipe({order: o}: { order: Order }) {
    return (
        <>
            <div className="mini-stats">
        <span>

            {o.durationMinutes} мин
        </span>
                <span>

                    {o.visitors} гостей
        </span>
                <span>

                    {o.temperature} °C
        </span>
            </div>
            <h3>Рецепт воды</h3>
            <ul className="recipe-lines">
                {o.lines.map((l) => (
                    <li key={l.ingredientId}>
                        <span>{l.name}</span>
                        <strong>
                            {l.quantity} {l.unit}
                        </strong>
                    </li>
                ))}
            </ul>
            <p className="steps">{o.steps}</p>
            {o.extraServices && (
                <p>
                    <strong>Дополнительно: </strong>
                    {o.extraServices}
                </p>
            )}
        </>
    );
}

function OrderDetails({
                          id,
                          cat,
                          role,
                          perform,
                          busy,
                          onClose,
                          onEdit,
                      }: {
    id: number;
    cat: Catalog;
    role: Role;
    perform: Perform;
    busy: boolean;
    onClose: () => void;
    onEdit: (o: Order) => void;
}) {
    const {data: o, error} = useData<Order>("/orders/" + id, 3000);
    const audit = useData<{ id: number; details: string; occurredAt: string }[]>(
        role === "ADMIN" || role === "MANAGER" ? "/orders/" + id + "/audit" : null,
        3000,
    );
    const [cancel, setCancel] = useState(false);
    const [reason, setReason] = useState("");
    const [pay, setPay] = useState(false);
    const [method, setMethod] = useState("CASH");
    const action = (name: string, extra: object = {}) =>
        o &&
        perform(
            () =>
                api("/orders/" + id + "/" + name, "POST", {
                    version: o.version,
                    ...extra,
                }),
            "Заказ обновлён",
        );
    return (
        <Modal title={"Заказ № " + String(id).padStart(4, "0")} onClose={onClose}>
            <Notice error={error}/>
            {!o ? (
                <Loading/>
            ) : (
                <>
                    <div className="detail-title">
                        <div>
                            <h2>{o.serviceName}</h2>
                            <p>
                                {cat.rooms.find((r) => r.id === o.roomId)?.name ||
                                    "Комната будет назначена при запуске"}
                            </p>
                        </div>
                        <Badge status={o.status}/>
                    </div>
                    <div className="lifecycle">
                        {["CREATED", "IN_SERVICE", "AWAITING_PAYMENT", "CLOSED"].map(
                            (s, i) => (
                                <span
                                    key={s}
                                    className={
                                        [
                                            "CREATED",
                                            "IN_SERVICE",
                                            "AWAITING_PAYMENT",
                                            "CLOSED",
                                        ].indexOf(o.status) >= i
                                            ? "done"
                                            : ""
                                    }
                                >

                                    {statuses[s as Status]}
                </span>
                            ),
                        )}
                    </div>
                    <Recipe order={o}/>
                    <p className="notice">
                        {o.status === "IN_SERVICE"
                            ? o.serviceStartedAt
                                ? "Услуга выполняется"
                                : o.waterReadyAt
                                    ? "Вода готова. Ожидается начало услуги."
                                    : "Котельная готовит воду. Начало услуги пока недоступно."
                            : statuses[o.status]}
                    </p>
                    {o.cancellationReason && (
                        <Notice error={"Причина отмены: " + o.cancellationReason}/>
                    )}
                    <div className="actions">
                        {role === "ADMIN" && o.status === "CREATED" && (
                            <>
                                <button disabled={busy} onClick={() => action("launch")}>
                                    Запустить обслуживание
                                </button>
                                <button className="secondary" onClick={() => onEdit(o)}>
                                    Изменить состав
                                </button>
                            </>
                        )}
                        {role === "ADMIN" && o.status === "AWAITING_PAYMENT" && (
                            <button onClick={() => setPay(true)}>
                                Принять оплату · {money(o.total)}
                            </button>
                        )}
                        {(role === "ADMIN" || role === "MANAGER") &&
                            ["CREATED", "IN_SERVICE"].includes(o.status) && (
                                <button
                                    className="secondary danger"
                                    onClick={() => setCancel(true)}
                                >
                                    Отменить заказ
                                </button>
                            )}
                    </div>
                    {cancel && (
                        <form
                            className="inset"
                            onSubmit={async (e) => {
                                e.preventDefault();
                                if (await action("cancel", {reason})) setCancel(false);
                            }}
                        >
                            <h3>Подтверждение отмены</h3>
                            <p>
                                Ресурсы будут освобождены. Уже использованные ингредиенты не
                                возвращаются на склад.
                            </p>
                            <Field label="Причина отмены">
                <textarea
                    required
                    maxLength={1000}
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                />
                            </Field>
                            <button className="danger-button" disabled={busy}>
                                Подтвердить отмену
                            </button>
                        </form>
                    )}
                    {pay && (
                        <form
                            className="inset"
                            onSubmit={async (e) => {
                                e.preventDefault();
                                if (await action("pay", {method})) setPay(false);
                            }}
                        >
                            <h3>Получено от гостя: {money(o.total)}</h3>
                            <Field label="Способ оплаты">
                                <select
                                    value={method}
                                    onChange={(e) => setMethod(e.target.value)}
                                >
                                    {Object.entries(methods).map(([v, l]) => (
                                        <option key={v} value={v}>
                                            {l}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                            <p>Подтвердите только после фактического получения оплаты.</p>
                            <button disabled={busy}>Подтвердить оплату и закрыть</button>
                        </form>
                    )}
                    {audit.data && (
                        <details>
                            <summary>История заказа</summary>
                            <ol className="audit">
                                {audit.data.map((e) => (
                                    <li key={e.id}>
                                        <small>{date(e.occurredAt)}</small>
                                        {e.details}
                                    </li>
                                ))}
                            </ol>
                        </details>
                    )}
                </>
            )}
        </Modal>
    );
}
