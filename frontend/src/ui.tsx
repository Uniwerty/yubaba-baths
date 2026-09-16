import {
    Children,
    cloneElement,
    createContext,
    isValidElement,
    ReactNode,
    useContext,
    useEffect,
    useId,
    useRef,
} from "react";
import {Role, Status} from "./types";

export const ErrorContext = createContext("");
export const roles: Record<Role, string> = {
    ADMIN: "Администратор",
    ATTENDANT: "Банщик",
    BOILER: "Оператор котельной",
    ACCOUNTANT: "Бухгалтер",
    MANAGER: "Управляющий",
};
export const statuses: Record<Status, string> = {
    CREATED: "Создан",
    IN_SERVICE: "В обслуживании",
    AWAITING_PAYMENT: "Ожидает оплаты",
    CLOSED: "Закрыт",
    CANCELLED: "Отменён",
};
export const methods: Record<string, string> = {
    CASH: "Наличные",
    CARD: "Карта",
    TRANSFER: "Перевод",
};
export const money = (n: number) =>
    new Intl.NumberFormat("ru-RU", {
        style: "currency",
        currency: "RUB",
        maximumFractionDigits: 2,
    }).format(n);
export const date = (s: string | null) =>
    s
        ? new Date(s).toLocaleString("ru-RU", {
            timeZone: "Europe/Moscow",
            day: "2-digit",
            month: "short",
            hour: "2-digit",
            minute: "2-digit",
        })
        : "–";
export const today = () =>
    new Date().toLocaleDateString("sv-SE", {timeZone: "Europe/Moscow"});

export function Badge({status}: { status: Status }) {
    return <span className={"badge " + status}>{statuses[status]}</span>;
}

export function Empty({children}: { children: ReactNode }) {
    return (
        <div className="empty">

            <p>{children}</p>
        </div>
    );
}

export function Loading() {
    return (
        <div className="empty">

            Загружаем данные…
        </div>
    );
}

export function Notice({error}: { error?: string }) {
    return error ? (
        <div role="alert" className="notice error">
            {error}
        </div>
    ) : null;
}

export function Field({
                          label,
                          children,
                      }: {
    label: string;
    children: ReactNode;
}) {
    const id = useId();
    return (
        <label className="field">
            <span id={id}>{label}</span>
            {Children.map(children, (child) =>
                isValidElement<{ "aria-label"?: string; "aria-labelledby"?: string }>(
                    child,
                ) &&
                ["input", "select", "textarea"].includes(String(child.type)) &&
                !child.props["aria-label"]
                    ? cloneElement(child, {"aria-labelledby": id})
                    : child,
            )}
        </label>
    );
}

export function Modal({
                          title,
                          children,
                          onClose,
                      }: {
    title: string;
    children: ReactNode;
    onClose: () => void;
}) {
    const error = useContext(ErrorContext);
    const ref = useRef<HTMLDialogElement>(null);
    useEffect(() => {
        ref.current?.showModal();
        const el = ref.current;
        return () => el?.close();
    }, []);
    return (
        <dialog ref={ref} onCancel={onClose} aria-label={title}>
            <header className="modal-head">
                <h2>{title}</h2>
                <button
                    type="button"
                    className="text-button"
                    onClick={onClose}
                    aria-label="Закрыть"
                >
                    Закрыть
                </button>
            </header>
            <Notice error={error}/>
            {children}
        </dialog>
    );
}

export function Stat({
                         label,
                         value,
                         hint,
                     }: {
    label: string;
    value: ReactNode;
    hint?: string;
}) {
    return (
        <div className="stat">
            <span>{label}</span>
            <strong>{value}</strong>
            {hint && <small>{hint}</small>}
        </div>
    );
}
