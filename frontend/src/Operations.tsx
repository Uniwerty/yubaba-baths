import {useEffect, useRef, useState} from "react";
import {api, useData} from "./api";
import {Catalog, Order} from "./types";
import {Perform, Recipe} from "./Orders";
import {Badge, date, Empty, Loading, Notice} from "./ui";

export function Boiler({perform, busy}: { perform: Perform; busy: boolean }) {
    const {data, error, loading} = useData<Order[]>("/boiler", 2000);
    const [mode, setMode] = useState(
        localStorage.getItem("yubaba-sound") || "silent",
    );
    const seen = useRef<Set<number>>(new Set());
    const [newIds, setNewIds] = useState<number[]>([]);
    useEffect(() => {
        if (!data) return;
        const incoming = data.filter((o) => !seen.current.has(o.id));
        if (incoming.length) {
            setNewIds(incoming.map((o) => o.id));
            if (mode !== "silent" && "speechSynthesis" in window) {
                const utterance = new SpeechSynthesisUtterance(
                    mode === "full"
                        ? incoming
                            .map(
                                (o) =>
                                    `Заказ ${o.id}. ${o.serviceName}. Температура ${o.temperature} градусов. ${o.lines.map((l) => `${l.name} ${l.quantity} ${l.unit}`).join(". ")}. ${o.steps}`,
                            )
                            .join(". ")
                        : "Поступил новый заказ",
                );
                utterance.lang = "ru-RU";
                window.speechSynthesis.speak(utterance);
            }
        }
        seen.current = new Set(data.map((o) => o.id));
    }, [data, mode]);
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Очередь котельной</h2>
                </div>
                <label className="sound">

                    <select
                        aria-label="Оповещения котельной"
                        value={mode}
                        onChange={(e) => {
                            setMode(e.target.value);
                            localStorage.setItem("yubaba-sound", e.target.value);
                            if (e.target.value !== "silent" && "speechSynthesis" in window) {
                                const s = new SpeechSynthesisUtterance("Оповещения включены");
                                s.lang = "ru-RU";
                                speechSynthesis.speak(s);
                            }
                        }}
                    >
                        <option value="silent">Визуальные оповещения</option>
                        <option value="short">Короткое оповещение</option>
                        <option value="full">Зачитывать рецепт</option>
                    </select>
                </label>
            </div>
            <Notice error={error}/>
            {loading ? (
                <Loading/>
            ) : !data?.length ? (
                <div className="panel">
                    <Empty>Вся вода готова. Новые задания появятся автоматически.</Empty>
                </div>
            ) : (
                <div className="card-grid">
                    {data.map((o, index) => (
                        <article
                            className={
                                "work-card " + (newIds.includes(o.id) ? "new-task" : "")
                            }
                            key={o.id}
                        >
                            <header>
                <span className="eyebrow">
                  ОЧЕРЕДЬ {index + 1} · ЗАКАЗ № {o.id}
                </span>
                                {newIds.includes(o.id) && (
                                    <span className="badge CREATED">Новое задание</span>
                                )}
                            </header>
                            <h2>{o.serviceName}</h2>
                            <p>
                                {date(o.launchedAt)} ·{" "}
                                {o.priority ? "Приоритет " + o.priority : "Обычный приоритет"}
                            </p>
                            <Recipe order={o}/>
                            <p className="muted">
                                Подготовка: около {o.preparationMinutes} мин
                            </p>
                            <button
                                className="wide big"
                                disabled={busy}
                                onClick={() =>
                                    perform(
                                        () =>
                                            api("/orders/" + o.id + "/water-ready", "POST", {
                                                version: o.version,
                                            }),
                                        "Готовность воды передана банщикам",
                                    )
                                }
                            >
                                 Вода готова
                            </button>
                        </article>
                    ))}
                </div>
            )}
        </>
    );
}

export function Schedule({
                             cat,
                             perform,
                             busy,
                         }: {
    cat: Catalog;
    perform: Perform;
    busy: boolean;
}) {
    const {data, error, loading} = useData<{
        orders: Order[];
        active: Order[];
        restUntil: string;
    }>("/schedule", 2000);
    const active = data?.active || [];
    const history =
        data?.orders.filter((o) => !active.some((a) => a.id === o.id)) || [];
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Моя смена</h2>
                </div>
                <span className="pill">

                    {active.length} активных заказов
        </span>
            </div>
            <Notice error={error}/>
            {data?.restUntil && new Date(data.restUntil) > new Date() && (
                <div className="notice">
                    Перерыв до {date(data.restUntil)}. Новые заказы будут назначаться
                    после отдыха.
                </div>
            )}
            {loading ? (
                <Loading/>
            ) : !active.length ? (
                <div className="panel">
                    <Empty>
                        Сейчас нет назначенных заказов
                    </Empty>
                </div>
            ) : (
                <div className="card-grid">
                    {active.map((o) => (
                        <article className="work-card" key={o.id}>
                            <header>
                                <span className="eyebrow">ЗАКАЗ № {o.id}</span>
                                <Badge status={o.status}/>
                            </header>
                            <h2>{o.serviceName}</h2>
                            <p>
                                {cat.rooms.find((r) => r.id === o.roomId)?.name} · назначен{" "}
                                {date(o.launchedAt)}
                            </p>
                            <Recipe order={o}/>
                            <div className="inset">
                                <strong>
                                    {o.serviceStartedAt
                                        ? "Начало: " + date(o.serviceStartedAt)
                                        : "Ориентировочное начало: " +
                                        date(
                                            new Date(
                                                new Date(o.launchedAt!).getTime() +
                                                o.preparationMinutes * 60000,
                                            ).toISOString(),
                                        )}
                                </strong>
                            </div>
                            {o.status === "IN_SERVICE" && !o.serviceStartedAt && (
                                <button
                                    className="wide big"
                                    disabled={busy}
                                    onClick={() =>
                                        perform(
                                            () =>
                                                api("/orders/" + o.id + "/start", "POST", {
                                                    version: o.version,
                                                }),
                                            "Услуга начата",
                                        )
                                    }
                                >

                                    Начать услугу
                                </button>
                            )}
                            {o.status === "IN_SERVICE" && o.serviceStartedAt && (
                                <button
                                    className="wide big"
                                    disabled={busy}
                                    onClick={() =>
                                        perform(
                                            () =>
                                                api("/orders/" + o.id + "/complete", "POST", {
                                                    version: o.version,
                                                }),
                                            "Услуга завершена. Администратор примет оплату.",
                                        )
                                    }
                                >
                                     Завершить услугу
                                </button>
                            )}
                            {o.status === "AWAITING_PAYMENT" && (
                                <p className="notice">
                                    Услуга завершена
                                </p>
                            )}
                        </article>
                    ))}
                </div>
            )}
            <h3 className="subheading">Завершённые заказы за сегодня</h3>
            <div className="panel">
                {history.length ? (
                    history.map((o) => (
                        <div className="list-row" key={o.id}>
              <span>
                № {o.id} · {o.serviceName}
                  <small>
                  {date(o.serviceStartedAt)} – {date(o.completedAt)}
                </small>
              </span>
                            <Badge status={o.status}/>
                        </div>
                    ))
                ) : (
                    <Empty>Здесь появится история вашей смены.</Empty>
                )}
            </div>
        </>
    );
}
