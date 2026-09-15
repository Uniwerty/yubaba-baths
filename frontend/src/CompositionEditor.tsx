import {Catalog, Composition, Order, Template} from "./types";
import {Field, money} from "./ui";
import {Plus, Trash2} from "lucide-react";

export function compositionOf(t: Template | Order): Composition {
    return {
        name: "serviceName" in t ? t.serviceName : t.name,
        bathType: t.bathType,
        preferredRoomId: t.preferredRoomId,
        attendants: t.attendants,
        durationMinutes: t.durationMinutes,
        preparationMinutes: t.preparationMinutes,
        breakMinutes: t.breakMinutes,
        temperature: t.temperature,
        steps: t.steps,
        extraServices: t.extraServices,
        basePrice: t.basePrice,
        lines: t.lines.map((l) => ({
            ingredientId: l.ingredientId,
            quantity: l.quantity,
        })),
    };
}

export const blankComposition: Composition = {
    name: "",
    bathType: "",
    preferredRoomId: null,
    attendants: 1,
    durationMinutes: 30,
    preparationMinutes: 10,
    breakMinutes: 10,
    temperature: 40,
    steps: "",
    extraServices: "",
    basePrice: 0,
    lines: [],
};

export function CompositionEditor({
                                      value: c,
                                      onChange,
                                      cat,
                                  }: {
    value: Composition;
    onChange: (c: Composition) => void;
    cat: Catalog;
}) {
    const set = <K extends keyof Composition>(k: K, v: Composition[K]) =>
        onChange({...c, [k]: v});
    return (
        <div className="composition">
            <div className="form-grid">
                <Field label="Название услуги">
                    <input
                        required
                        maxLength={255}
                        value={c.name}
                        onChange={(e) => set("name", e.target.value)}
                    />
                </Field>
                <Field label="Тип купальни">
                    <select
                        required
                        value={c.bathType}
                        onChange={(e) =>
                            onChange({
                                ...c,
                                bathType: e.target.value,
                                preferredRoomId: null,
                            })
                        }
                    >
                        <option value="">Выберите тип</option>
                        {[...new Set(cat.rooms.map((r) => r.bathType))].map((t) => (
                            <option key={t}>{t}</option>
                        ))}
                    </select>
                </Field>
                <Field label="Комната">
                    <select
                        value={c.preferredRoomId || ""}
                        onChange={(e) =>
                            set("preferredRoomId", Number(e.target.value) || null)
                        }
                    >
                        <option value="">Любая свободная</option>
                        {cat.rooms
                            .filter((r) => r.bathType === c.bathType)
                            .map((r) => (
                                <option key={r.id} value={r.id}>
                                    {r.name} · до {r.capacity} гостей
                                </option>
                            ))}
                    </select>
                </Field>
                <Field label="Число банщиков">
                    <input
                        required
                        type="number"
                        min="1"
                        max="20"
                        value={c.attendants}
                        onChange={(e) => set("attendants", Number(e.target.value))}
                    />
                </Field>
                <Field label="Длительность услуги, мин">
                    <input
                        required
                        type="number"
                        min="1"
                        max="480"
                        value={c.durationMinutes}
                        onChange={(e) => set("durationMinutes", Number(e.target.value))}
                    />
                </Field>
                <Field label="Подготовка воды, мин">
                    <input
                        required
                        type="number"
                        min="1"
                        max="120"
                        value={c.preparationMinutes}
                        onChange={(e) => set("preparationMinutes", Number(e.target.value))}
                    />
                </Field>
                <Field label="Перерыв банщика, мин">
                    <input
                        required
                        type="number"
                        min="0"
                        max="120"
                        value={c.breakMinutes}
                        onChange={(e) => set("breakMinutes", Number(e.target.value))}
                    />
                </Field>
                <Field label="Температура воды, °C">
                    <input
                        required
                        type="number"
                        min="20"
                        max="100"
                        value={c.temperature}
                        onChange={(e) => set("temperature", Number(e.target.value))}
                    />
                </Field>
                <Field label="Стоимость услуг, ₽">
                    <input
                        required
                        type="number"
                        min="0"
                        max="99999999"
                        step="0.01"
                        value={c.basePrice}
                        onChange={(e) => set("basePrice", Number(e.target.value))}
                    />
                </Field>
                <Field label="Дополнительные услуги (включены в стоимость)">
                    <input
                        maxLength={2000}
                        value={c.extraServices}
                        onChange={(e) => set("extraServices", e.target.value)}
                    />
                </Field>
            </div>
            <h3>Ингредиенты на весь заказ</h3>
            {c.lines.map((line, index) => (
                <div className="ingredient-row" key={index}>
                    <Field label="Ингредиент">
                        <select
                            required
                            aria-label={"Ингредиент " + (index + 1)}
                            value={line.ingredientId || ""}
                            onChange={(e) =>
                                set(
                                    "lines",
                                    c.lines.map((l, j) =>
                                        j === index
                                            ? {...l, ingredientId: Number(e.target.value)}
                                            : l,
                                    ),
                                )
                            }
                        >
                            <option value="">Выберите</option>
                            {cat.ingredients.map((i) => (
                                <option key={i.id} value={i.id}>
                                    {i.name} · {money(i.price)}/{i.unit}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Количество">
                        <input
                            required
                            type="number"
                            min="0.001"
                            max="99999999"
                            step="0.001"
                            value={line.quantity}
                            onChange={(e) =>
                                set(
                                    "lines",
                                    c.lines.map((l, j) =>
                                        j === index
                                            ? {...l, quantity: Number(e.target.value)}
                                            : l,
                                    ),
                                )
                            }
                        />
                    </Field>
                    <button
                        type="button"
                        className="icon-button danger"
                        aria-label="Удалить ингредиент"
                        onClick={() =>
                            set(
                                "lines",
                                c.lines.filter((_, j) => j !== index),
                            )
                        }
                    >
                        <Trash2 size={18}/>
                    </button>
                </div>
            ))}
            <button
                type="button"
                className="secondary"
                onClick={() =>
                    set("lines", [
                        ...c.lines,
                        {
                            ingredientId:
                                cat.ingredients.find(
                                    (i) => !c.lines.some((l) => l.ingredientId === i.id),
                                )?.id || 0,
                            quantity: 1,
                        },
                    ])
                }
            >
                <Plus size={16}/> Добавить ингредиент
            </button>
            <Field label="Пошаговый рецепт воды">
        <textarea
            required
            rows={4}
            maxLength={4000}
            value={c.steps}
            onChange={(e) => set("steps", e.target.value)}
        />
            </Field>
        </div>
    );
}
