import {useState} from "react";
import {api, useData} from "./api";
import {Catalog, Client, Composition, Template} from "./types";
import {Empty, Field, Loading, Modal, money, Notice} from "./ui";
import {blankComposition, CompositionEditor, compositionOf,} from "./CompositionEditor";
import {Perform} from "./Orders";

export function Clients({
                            perform,
                            busy,
                        }: {
    perform: Perform;
    busy: boolean;
}) {
    const [q, setQ] = useState("");
    const {data, error, loading} = useData<Client[]>(
        "/clients?q=" + encodeURIComponent(q),
    );
    const [edit, setEdit] = useState<Client | null | undefined>();
    const [name, setName] = useState("");
    const [contact, setContact] = useState("");
    const [notes, setNotes] = useState("");
    const open = (c: Client | null) => {
        setEdit(c);
        setName(c?.name || "");
        setContact(c?.contact || "");
        setNotes(c?.notes || "");
    };
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Гости купален</h2>
                </div>
                <button onClick={() => open(null)}>
                     Новый гость
                </button>
            </div>
            <div className="panel">
                <div className="toolbar">
                    <div className="search">

                        <input
                            aria-label="Поиск гостей"
                            placeholder="Имя или контакт"
                            value={q}
                            onChange={(e) => setQ(e.target.value)}
                        />
                    </div>
                </div>
                <Notice error={error}/>
                {loading ? (
                    <Loading/>
                ) : !data?.length ? (
                    <Empty>Гость не найден. Создайте новую карточку.</Empty>
                ) : (
                    <div className="table-scroll">
                        <table>
                            <thead>
                            <tr>
                                <th>Гость</th>
                                <th>Контакт</th>
                                <th>Примечания</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            {data.map((c) => (
                                <tr key={c.id}>
                                    <td>
                                        <strong>{c.name}</strong>
                                    </td>
                                    <td>{c.contact}</td>
                                    <td>{c.notes || "–"}</td>
                                    <td>
                                        <button
                                            className="text-button"
                                            aria-label={"Редактировать " + c.name}
                                            onClick={() => open(c)}
                                        >
                                            Изменить
</button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
            {edit !== undefined && (
                <Modal
                    title={edit ? "Карточка гостя" : "Новый гость"}
                    onClose={() => setEdit(undefined)}
                >
                    <form
                        onSubmit={async (e) => {
                            e.preventDefault();
                            if (
                                await perform(() =>
                                    api(
                                        edit ? "/clients/" + edit.id : "/clients",
                                        edit ? "PUT" : "POST",
                                        {name, contact, notes, version: edit?.version},
                                    ),
                                )
                            )
                                setEdit(undefined);
                        }}
                    >
                        <Field label="Имя гостя">
                            <input
                                required
                                maxLength={255}
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                            />
                        </Field>
                        <Field label="Телефон, почта или другой контакт">
                            <input
                                required
                                maxLength={255}
                                value={contact}
                                onChange={(e) => setContact(e.target.value)}
                            />
                        </Field>
                        <Field label="Предпочтения и примечания">
              <textarea
                  maxLength={255}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
              />
                        </Field>
                        <footer className="form-footer">
                            <button disabled={busy}>Сохранить гостя</button>
                        </footer>
                    </form>
                </Modal>
            )}
        </>
    );
}

export function Templates({
                              cat,
                              perform,
                              busy,
                          }: {
    cat: Catalog;
    perform: Perform;
    busy: boolean;
}) {
    const [edit, setEdit] = useState<Template | null | undefined>();
    const [c, setC] = useState<Composition>(blankComposition);
    const open = (t: Template | null) => {
        setEdit(t);
        setC(t ? compositionOf(t) : blankComposition);
    };
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Шаблоны услуг</h2>
                </div>
                <button onClick={() => open(null)}>
                     Создать шаблон
                </button>
            </div>
            <div className="card-grid">
                {cat.templates.map((t) => (
                    <article key={t.id} className="work-card template-card">
                        <span className="eyebrow">{t.bathType}</span>
                        <h2>{t.name}</h2>
                        <p>
                            {t.durationMinutes} мин · {t.temperature} °C · {t.attendants}{" "}
                            банщик(а)
                        </p>
                        <div className="tags">
                            {t.lines.map((l) => (
                                <span key={l.ingredientId}>
                  {l.name} {l.quantity} {l.unit}
                </span>
                            ))}
                        </div>
                        <p>{t.extraServices}</p>
                        <footer>
                            <strong>
                                {money(
                                    t.basePrice +
                                    t.lines.reduce(
                                        (sum, l) =>
                                            sum +
                                            l.quantity *
                                            (cat.ingredients.find((i) => i.id === l.ingredientId)
                                                ?.price || 0),
                                        0,
                                    ),
                                )}
                            </strong>
                            <button className="secondary" onClick={() => open(t)}>
                                 Изменить
                            </button>
                        </footer>
                    </article>
                ))}
            </div>
            {!cat.templates.length && (
                <Empty>Создайте шаблон, чтобы ускорить оформление заказов.</Empty>
            )}
            {edit !== undefined && (
                <Modal
                    title={edit ? "Редактирование шаблона" : "Новый шаблон услуги"}
                    onClose={() => setEdit(undefined)}
                >
                    <form
                        onSubmit={async (e) => {
                            e.preventDefault();
                            if (
                                await perform(() =>
                                    api(
                                        edit ? "/templates/" + edit.id : "/templates",
                                        edit ? "PUT" : "POST",
                                        {composition: c, version: edit?.version},
                                    ),
                                )
                            )
                                setEdit(undefined);
                        }}
                    >
                        <CompositionEditor value={c} onChange={setC} cat={cat}/>
                        <footer className="form-footer">
                            <button disabled={busy}>Сохранить шаблон</button>
                        </footer>
                    </form>
                </Modal>
            )}
        </>
    );
}
