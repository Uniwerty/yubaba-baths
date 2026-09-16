import {useState} from "react";
import {api, query, useData} from "./api";
import {Catalog, Dashboard, Filter, Report, SavedReport} from "./types";
import {Badge, date, Empty, Field, Loading, methods, money, Notice, Stat, statuses, today,} from "./ui";
import {Perform} from "./Orders";

const initial = (): Filter => ({
    from: today(),
    to: today(),
    templateId: null,
    method: null,
    visitors: null,
});

function Period({
                    filter: f,
                    set,
                }: {
    filter: Filter;
    set: (f: Filter) => void;
}) {
    return (
        <>
            <Field label="С">
                <input
                    required
                    type="date"
                    value={f.from}
                    onChange={(e) => set({...f, from: e.target.value})}
                />
            </Field>
            <Field label="По">
                <input
                    required
                    type="date"
                    min={f.from}
                    value={f.to}
                    onChange={(e) => set({...f, to: e.target.value})}
                />
            </Field>
        </>
    );
}

export function DashboardView() {
    const [f, set] = useState(initial);
    const {
        data: d,
        error,
        loading,
    } = useData<Dashboard>("/dashboard" + query(f), 3000);
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Купальни сегодня</h2>
                </div>
            </div>
            <div className="period">
                <Period filter={f} set={set}/>
            </div>
            <Notice error={error}/>
            {loading ? (
                <Loading/>
            ) : (
                d && (
                    <>
                        <div className="stats">
                            <Stat
                                label="Выручка"
                                value={money(d.revenue)}
                            />
                            <Stat
                                label="Заказы"
                                value={d.orderCount}
                            />
                            <Stat
                                label="Среднее обслуживание"
                                value={d.averageMinutes.toFixed(1) + " мин"}
                            />
                            <Stat
                                label="В очереди котельной"
                                value={d.queue.length}
                            />
                        </div>
                        {d.empty && (
                            <p className="notice">
                                За выбранный период заказов нет. Состояние ресурсов показано на
                                текущий момент.
                            </p>
                        )}
                        <div className="dashboard-grid">
                            <section className="panel">
                                <div className="panel-title">
                                    <h3>Комнаты</h3>
                                    <span>
                    {d.rooms.filter((r) => r.orders.length).length} /{" "}
                                        {d.rooms.length} занято
                  </span>
                                </div>
                                <div className="resource-grid">
                                    {d.rooms.map(({room: r, orders}) => (
                                        <div
                                            className={
                                                "resource " + (orders.length ? "occupied" : "")
                                            }
                                            key={r.id}
                                        >
                                            <h3>{r.name}</h3>
                                            <p>{r.bathType}</p>
                                            <span
                                                className={
                                                    "badge " + (orders.length ? "IN_SERVICE" : "CLOSED")
                                                }
                                            >
                        {orders.length
                            ? "Заказ № " + orders.join(", ")
                            : "Свободна"}
                      </span>
                                            <small>До {r.capacity} гостей</small>
                                        </div>
                                    ))}
                                </div>
                                {!d.rooms.length && (
                                    <Empty>Добавьте комнаты в разделе «Склад и ресурсы».</Empty>
                                )}
                            </section>
                            <section className="panel">
                                <div className="panel-title">
                                    <h3>Команда банщиков</h3>
                                    <span>{d.attendants.length} сотрудников</span>
                                </div>
                                {d.attendants.map(({account: a, orders}) => (
                                    <div className="list-row" key={a.id}>
                                        <div className="grow">
                                            <strong>{a.name}</strong>
                                            <small>
                                                {orders.length
                                                    ? "Заказ № " + orders.join(", ")
                                                    : a.restUntil && new Date(a.restUntil) > new Date()
                                                        ? "Перерыв до " + date(a.restUntil)
                                                        : "Готов к назначению"}
                                            </small>
                                        </div>

                                    </div>
                                ))}
                                {!d.attendants.length && (
                                    <Empty>Создайте учётные записи банщиков.</Empty>
                                )}
                            </section>
                        </div>
                        <section className="panel">
                            <div className="panel-title">
                                <h3>Котельная</h3>
                            </div>
                            {d.queue.length ? (
                                d.queue.map((o) => (
                                    <div className="list-row" key={o.id}>
                                        <span className="number">{o.id}</span>
                                        <div className="grow">
                                            <strong>{o.serviceName}</strong>
                                            <small>
                                                Подготовка {o.preparationMinutes} мин ·{" "}
                                                {date(o.launchedAt)}
                                            </small>
                                        </div>
                                        <span>Приоритет {o.priority}</span>

                                    </div>
                                ))
                            ) : (
                                <Empty>
                                    Очередь свободна – котельная готова к новым гостям.
                                </Empty>
                            )}
                        </section>
                        <p className="muted small">Данные обновлены {date(d.updatedAt)}</p>
                    </>
                )
            )}
        </>
    );
}

function exportCsv(r: Report) {
    const safe = (v: unknown) =>
        '"' +
        String(v ?? "")
            .replace(/^[=+@-]/, "'$&")
            .replaceAll('"', '""') +
        '"';
    const content = [
        [
            "Заказ",
            "Услуга",
            "Статус",
            "Гостей",
            "Сумма",
            "Оплачено",
            "Способ оплаты",
        ],
        ...r.orders.map((o) => {
            const p = r.payments.find((p) => p.orderId === o.id);
            return [
                o.id,
                o.serviceName,
                statuses[o.status],
                o.visitors,
                o.total,
                p?.amount || 0,
                p ? methods[p.method] : "",
            ];
        }),
    ]
        .map((row) => row.map(safe).join(";"))
        .join("\r\n");
    const url = URL.createObjectURL(
        new Blob(["\ufeff" + content], {type: "text/csv;charset=utf-8"}),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "yubaba-report.csv";
    a.click();
    URL.revokeObjectURL(url);
}

export function Finance({
                            cat,
                            perform,
                            busy,
                        }: {
    cat: Catalog;
    perform: Perform;
    busy: boolean;
}) {
    const [f, set] = useState(initial);
    const [applied, setApplied] = useState(initial);
    const [name, setName] = useState("");
    const [saved, setSaved] = useState("");
    const {
        data: r,
        error,
        loading,
    } = useData<Report>("/reports" + query(applied));
    const ts = useData<SavedReport[]>("/report-templates");
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Финансовая отчётность</h2>
                </div>
                <button
                    className="secondary"
                    disabled={!r}
                    onClick={() => r && exportCsv(r)}
                >
                     Скачать CSV
                </button>
            </div>
            <section className="panel filters">
                <Field label="">
                    <select
                        value={saved}
                        onChange={(e) => {
                            setSaved(e.target.value);
                            const t = ts.data?.find((t) => t.id === Number(e.target.value));
                            if (t) {
                                const p = JSON.parse(t.parameters) as Filter;
                                set(p);
                                setApplied(p);
                            }
                        }}
                    >
                        <option value="">Выберите шаблон</option>
                        {ts.data?.map((t) => (
                            <option key={t.id} value={t.id}>
                                {t.name}
                            </option>
                        ))}
                    </select>
                </Field>
                <form
                    onSubmit={(e) => {
                        e.preventDefault();
                        setApplied({...f});
                        setSaved("");
                    }}
                >
                    <div className="filter-grid">
                        <Period filter={f} set={set}/>
                        <Field label="Услуга">
                            <select
                                value={f.templateId || ""}
                                onChange={(e) =>
                                    set({...f, templateId: Number(e.target.value) || null})
                                }
                            >
                                <option value="">Все услуги</option>
                                {cat.templates.map((t) => (
                                    <option key={t.id} value={t.id}>
                                        {t.name}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Field label="Способ оплаты">
                            <select
                                value={f.method || ""}
                                onChange={(e) => set({...f, method: e.target.value || null})}
                            >
                                <option value="">Все способы</option>
                                {Object.entries(methods).map(([v, l]) => (
                                    <option key={v} value={v}>
                                        {l}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Field label="Гостей в заказе">
                            <input
                                type="number"
                                min="1"
                                max="100"
                                placeholder="Любое число"
                                value={f.visitors || ""}
                                onChange={(e) =>
                                    set({...f, visitors: Number(e.target.value) || null})
                                }
                            />
                        </Field>
                    </div>
                    <button>Сформировать отчёт</button>
                </form>
            </section>
            <Notice error={error}/>
            {loading ? (
                <Loading/>
            ) : (
                r && (
                    <>
                        <div className="stats three">
                            <Stat label="Получено по заказам" value={money(r.revenue)}/>
                            <Stat label="Заказов" value={r.orderCount}/>
                            <Stat
                                label="Гостей"
                                value={r.visitors}
                            />
                        </div>
                        <div className="panel">
                            {r.empty ? (
                                <Empty>
                                    За выбранный период данных нет. Измените параметры отчёта.
                                </Empty>
                            ) : (
                                <div className="table-scroll">
                                    <table>
                                        <thead>
                                        <tr>
                                            <th>Заказ</th>
                                            <th>Услуга</th>
                                            <th>Статус</th>
                                            <th>Стоимость</th>
                                            <th>Оплата</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {r.orders.map((o) => {
                                            const p = r.payments.find((p) => p.orderId === o.id);
                                            return (
                                                <tr key={o.id}>
                                                    <td>
                                                        № {o.id}
                                                        <small>{date(o.createdAt)}</small>
                                                    </td>
                                                    <td>
                                                        {o.serviceName}
                                                        <small>{o.visitors} гостей</small>
                                                    </td>
                                                    <td>
                                                        <Badge status={o.status}/>
                                                    </td>
                                                    <td>{money(o.total)}</td>
                                                    <td>
                                                        {p ? (
                                                            <>
                                                                {money(p.amount)} · {methods[p.method]}
                                                                <small>{date(p.paidAt)}</small>
                                                            </>
                                                        ) : (
                                                            "–"
                                                        )}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                        <form
                            className="save-report"
                            onSubmit={async (e) => {
                                e.preventDefault();
                                if (
                                    await perform(
                                        () =>
                                            api("/report-templates", "POST", {
                                                name,
                                                parameters: applied,
                                            }),
                                        "Шаблон отчёта сохранён",
                                    )
                                )
                                    setName("");
                            }}
                        >
                            <Field label="Сохранить параметры сформированного отчёта">
                                <input
                                    required
                                    maxLength={255}
                                    placeholder="Название шаблона"
                                    value={name}
                                    onChange={(e) => setName(e.target.value)}
                                />
                            </Field>
                            <button className="secondary" disabled={busy}>
                                 Сохранить шаблон
                            </button>
                        </form>
                    </>
                )
            )}
        </>
    );
}
