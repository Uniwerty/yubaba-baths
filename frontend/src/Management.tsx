import {useState} from "react";
import {api, download, useData} from "./api";
import {Account, Catalog, Role, Supply} from "./types";
import {date, Empty, Field, Loading, Modal, money, Notice, roles} from "./ui";
import {Perform} from "./Orders";

export function Accounts({
                             perform,
                             busy,
                         }: {
    perform: Perform;
    busy: boolean;
}) {
    const {data, error, loading} = useData<Account[]>("/accounts");
    const [open, setOpen] = useState(false);
    const [input, setInput] = useState({
        login: "",
        password: "",
        name: "",
        role: "ATTENDANT" as Role,
    });
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Сотрудники и доступ</h2>
                </div>
                <button onClick={() => setOpen(true)}>
                     Добавить сотрудника
                </button>
            </div>
            <div className="panel">
                <Notice error={error}/>
                {loading ? (
                    <Loading/>
                ) : (
                    <div className="table-scroll">
                        <table>
                            <thead>
                            <tr>
                                <th>Сотрудник</th>
                                <th>Логин</th>
                                <th>Роль</th>
                                <th>Доступ</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            {data?.map((a) => (
                                <tr key={a.id}>
                                    <td>
                                        <strong>{a.name}</strong>
                                    </td>
                                    <td>{a.login}</td>
                                    <td>{roles[a.role]}</td>
                                    <td>
                      <span
                          className={
                              "badge " + (a.blocked ? "CANCELLED" : "CLOSED")
                          }
                      >
                        {a.blocked ? "Заблокирован" : "Активен"}
                      </span>
                                    </td>
                                    <td>
                                        <button
                                            className="secondary"
                                            disabled={busy}
                                            onClick={() =>
                                                perform(() =>
                                                    api("/accounts/" + a.id, "PATCH", {
                                                        blocked: !a.blocked,
                                                    }),
                                                )
                                            }
                                        >

                                            {a.blocked ? "Разблокировать" : "Заблокировать"}
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
            {open && (
                <Modal title="Новый сотрудник" onClose={() => setOpen(false)}>
                    <form
                        onSubmit={async (e) => {
                            e.preventDefault();
                            if (await perform(() => api("/accounts", "POST", input))) {
                                setOpen(false);
                                setInput({
                                    login: "",
                                    password: "",
                                    name: "",
                                    role: "ATTENDANT",
                                });
                            }
                        }}
                    >
                        <Field label="Имя">
                            <input
                                required
                                maxLength={255}
                                value={input.name}
                                onChange={(e) => setInput({...input, name: e.target.value})}
                            />
                        </Field>
                        <Field label="Логин · латинские буквы, цифры, точка, дефис">
                            <input
                                required
                                pattern="[a-zA-Z0-9._\-]{3,100}"
                                value={input.login}
                                autoComplete="off"
                                onChange={(e) => setInput({...input, login: e.target.value})}
                            />
                        </Field>
                        <Field label="Пароль · от 10 до 72 символов">
                            <input
                                required
                                type="password"
                                minLength={10}
                                maxLength={72}
                                autoComplete="new-password"
                                value={input.password}
                                onChange={(e) =>
                                    setInput({...input, password: e.target.value})
                                }
                            />
                        </Field>
                        <Field label="Роль">
                            <select
                                value={input.role}
                                onChange={(e) =>
                                    setInput({...input, role: e.target.value as Role})
                                }
                            >
                                {Object.entries(roles).map(([v, l]) => (
                                    <option key={v} value={v}>
                                        {l}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <footer className="form-footer">
                            <button disabled={busy}>Создать учётную запись</button>
                        </footer>
                    </form>
                </Modal>
            )}
        </>
    );
}

export function Inventory({
                              cat,
                              perform,
                              busy,
                          }: {
    cat: Catalog;
    perform: Perform;
    busy: boolean;
}) {
    const {data, error} = useData<Supply[]>("/supplies", 5000);
    const [mode, setMode] = useState("");
    const [ingredient, setIngredient] = useState({
        name: "",
        unit: "г",
        stock: 0,
        threshold: 10,
        price: 0,
    });
    const [room, setRoom] = useState({
        name: "",
        bathType: "Травяная купальня",
        capacity: 4,
    });
    return (
        <>
            <div className="section-heading">
                <div>
                    <h2>Склад и ресурсы</h2>
                </div>
                <button onClick={() => setMode("ingredient")}>
                     Новый ингредиент
                </button>
            </div>
            <div className="panel">
                <div className="table-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>Ингредиент</th>
                            <th>Остаток</th>
                            <th>В резерве</th>
                            <th>Доступно</th>
                            <th>Нижний порог</th>
                            <th>Цена за единицу</th>
                        </tr>
                        </thead>
                        <tbody>
                        {cat.ingredients.map((i) => (
                            <tr key={i.id}>
                                <td>
                                    <strong>{i.name}</strong>
                                </td>
                                <td>
                                    {i.stock} {i.unit}
                                </td>
                                <td>
                                    {i.reserved} {i.unit}
                                </td>
                                <td>
                    <span
                        className={
                            "badge " +
                            (i.stock - i.reserved <= i.threshold
                                ? "AWAITING_PAYMENT"
                                : "CLOSED")
                        }
                    >
                      {Number((i.stock - i.reserved).toFixed(3))} {i.unit}
                    </span>
                                </td>
                                <td>
                                    {i.threshold} {i.unit}
                                </td>
                                <td>{money(i.price)}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
                {!cat.ingredients.length && (
                    <Empty>Добавьте ингредиенты и начальные остатки.</Empty>
                )}
            </div>
            <div className="section-heading subheading">
                <div>
                    <h2>Заявки поставщику</h2>
                </div>
            </div>
            <Notice error={error}/>
            <div className="panel">
                {!data?.length ? (
                    <Empty>Запасов достаточно. Заявок на поставку нет.</Empty>
                ) : (
                    data.map((s) => (
                        <div className="list-row" key={s.id}>
                            <div className="grow">
                                <strong>
                                    № {s.id} · {s.ingredientName}
                                </strong>
                                <small>
                                    {s.quantity} {s.unit} · {date(s.createdAt)} ·{" "}
                                    {s.status === "OPEN" ? "Ожидает поставки" : "Получено"}
                                </small>
                            </div>
                            <button
                                className="secondary"
                                disabled={busy}
                                onClick={() =>
                                    perform(
                                        () =>
                                            download(
                                                "/supplies/" + s.id + "/export",
                                                "supply-" + s.id + ".txt",
                                            ),
                                        "Заявка выгружена",
                                    )
                                }
                            >
                                 Скачать
                            </button>
                            {s.status === "OPEN" && (
                                <button
                                    disabled={busy}
                                    onClick={() => {
                                        if (
                                            window.confirm(
                                                "Подтвердить получение " +
                                                s.quantity +
                                                " " +
                                                s.unit +
                                                " – " +
                                                s.ingredientName +
                                                "?",
                                            )
                                        )
                                            void perform(
                                                () => api("/supplies/" + s.id + "/receive", "POST"),
                                                "Поставка принята, остатки обновлены",
                                            );
                                    }}
                                >
                                    Принять поставку
                                </button>
                            )}
                        </div>
                    ))
                )}
            </div>
            <div className="section-heading subheading">
                <div>
                    <h2>Комнаты</h2>
                </div>
                <button className="secondary" onClick={() => setMode("room")}>
                     Добавить комнату
                </button>
            </div>
            <div className="panel">
                {cat.rooms.map((r) => (
                    <div className="list-row" key={r.id}>
                        <strong>{r.name}</strong>
                        <span className="grow">{r.bathType}</span>
                        <span>До {r.capacity} гостей</span>
                    </div>
                ))}
            </div>
            {mode && (
                <Modal
                    title={mode === "room" ? "Новая комната" : "Новый ингредиент"}
                    onClose={() => setMode("")}
                >
                    <form
                        onSubmit={async (e) => {
                            e.preventDefault();
                            if (
                                await perform(() =>
                                    api(
                                        mode === "room" ? "/rooms" : "/ingredients",
                                        "POST",
                                        mode === "room" ? room : ingredient,
                                    ),
                                )
                            ) {
                                setMode("");
                                setIngredient({
                                    name: "",
                                    unit: "г",
                                    stock: 0,
                                    threshold: 10,
                                    price: 0,
                                });
                                setRoom({
                                    name: "",
                                    bathType: "Травяная купальня",
                                    capacity: 4,
                                });
                            }
                        }}
                    >
                        {mode === "room" ? (
                            <>
                                <Field label="Название комнаты">
                                    <input
                                        required
                                        maxLength={255}
                                        value={room.name}
                                        onChange={(e) => setRoom({...room, name: e.target.value})}
                                    />
                                </Field>
                                <Field label="Тип купальни">
                                    <input
                                        required
                                        maxLength={255}
                                        list="bath-types"
                                        value={room.bathType}
                                        onChange={(e) =>
                                            setRoom({...room, bathType: e.target.value})
                                        }
                                    />
                                    <datalist id="bath-types">
                                        {[...new Set(cat.rooms.map((r) => r.bathType))].map((t) => (
                                            <option key={t}>{t}</option>
                                        ))}
                                    </datalist>
                                </Field>
                                <Field label="Вместимость">
                                    <input
                                        required
                                        type="number"
                                        min="1"
                                        max="100"
                                        value={room.capacity}
                                        onChange={(e) =>
                                            setRoom({...room, capacity: Number(e.target.value)})
                                        }
                                    />
                                </Field>
                            </>
                        ) : (
                            <>
                                <Field label="Название ингредиента">
                                    <input
                                        required
                                        maxLength={255}
                                        value={ingredient.name}
                                        onChange={(e) =>
                                            setIngredient({...ingredient, name: e.target.value})
                                        }
                                    />
                                </Field>
                                <Field label="Единица измерения">
                                    <input
                                        required
                                        maxLength={255}
                                        value={ingredient.unit}
                                        onChange={(e) =>
                                            setIngredient({...ingredient, unit: e.target.value})
                                        }
                                    />
                                </Field>
                                {(
                                    [
                                        ["stock", "Начальный остаток"],
                                        ["threshold", "Нижний порог"],
                                        ["price", "Цена за единицу, ₽"],
                                    ] as const
                                ).map(([key, label]) => (
                                    <Field label={label} key={key}>
                                        <input
                                            required
                                            type="number"
                                            min="0"
                                            max="99999999"
                                            step={key === "price" ? "0.01" : "0.001"}
                                            value={ingredient[key]}
                                            onChange={(e) =>
                                                setIngredient({
                                                    ...ingredient,
                                                    [key]: Number(e.target.value),
                                                })
                                            }
                                        />
                                    </Field>
                                ))}
                            </>
                        )}
                        <footer className="form-footer">
                            <button disabled={busy}>Сохранить</button>
                        </footer>
                    </form>
                </Modal>
            )}
        </>
    );
}
