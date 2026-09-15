import {useCallback, useEffect, useState} from "react";
import {LogOut, Menu,} from "lucide-react";
import {api, RefreshContext, setToken, useAction, useData} from "./api";
import {Account, Catalog} from "./types";
import {ErrorContext, Field, Loading, Notice, roles} from "./ui";
import {Orders} from "./Orders";
import {Clients, Templates} from "./Directory";
import {Boiler, Schedule} from "./Operations";
import {DashboardView, Finance} from "./Finance";
import {Accounts, Inventory} from "./Management";

const nav = [
    {
        id: "dashboard",
        label: "Обзор комплекса",
        roles: ["MANAGER"],
    },
    {
        id: "orders",
        label: "Заказы",
        roles: ["ADMIN", "MANAGER"],
    },
    {id: "clients", label: "Гости", roles: ["ADMIN"]},
    {
        id: "schedule",
        label: "Моя смена",
        roles: ["ATTENDANT"],
    },
    {id: "boiler", label: "Котельная", roles: ["BOILER"]},
    {
        id: "templates",
        label: "Шаблоны услуг",
        roles: ["ADMIN", "MANAGER"],
    },
    {
        id: "finance",
        label: "Отчёты и платежи",
        roles: ["ACCOUNTANT"],
    },
    {
        id: "inventory",
        label: "Склад и ресурсы",
        roles: ["MANAGER"],
    },
    {
        id: "accounts",
        label: "Сотрудники",
        roles: ["MANAGER"],
    },
];
export default function App() {
    const [user, setUser] = useState<Account>();
    const [starting, setStarting] = useState(true);
    useEffect(() => {
        if (sessionStorage.getItem("yubaba-token"))
            api<Account>("/auth/me")
                .then(setUser)
                .catch(() => setToken(""))
                .finally(() => setStarting(false));
        else setStarting(false);
        const expired = () => {
            setToken("");
            setUser(undefined);
        };
        window.addEventListener("session-expired", expired);
        return () => window.removeEventListener("session-expired", expired);
    }, []);
    if (starting) return <Loading/>;
    return user ? (
        <Workspace
            key={user.id}
            user={user}
            logout={() => {
                setToken("");
                setUser(undefined);
            }}
        />
    ) : (
        <Login onLogin={setUser}/>
    );
}

function Login({onLogin}: { onLogin: (a: Account) => void }) {
    const [login, setLogin] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    return (
        <main className="login">
            <section className="login-form">
                <h1>Купальни Юбабы</h1>
                <h2>Добро пожаловать</h2>
                <p>Вход в систему управления купальнями</p>
                <form
                    onSubmit={async (e) => {
                        e.preventDefault();
                        setBusy(true);
                        setError("");
                        try {
                            const r = await api<{ token: string; user: Account }>(
                                "/auth/login",
                                "POST",
                                {login, password},
                            );
                            setToken(r.token);
                            onLogin(r.user);
                        } catch (e) {
                            setError((e as Error).message);
                        } finally {
                            setBusy(false);
                        }
                    }}
                >
                    <Field label="Логин">
                        <input
                            autoFocus
                            required
                            autoComplete="username"
                            value={login}
                            onChange={(e) => setLogin(e.target.value)}
                            placeholder="Ваш логин"
                        />
                    </Field>
                    <Field label="Пароль">
                        <input
                            required
                            type="password"
                            autoComplete="current-password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="Ваш пароль"
                        />
                    </Field>
                    <Notice error={error}/>
                    <button className="wide" disabled={busy}>
                        {busy ? "Входим…" : "Войти в систему"}
                    </button>
                </form>
                <small>Нет доступа? Обратитесь к управляющему.</small>
            </section>
        </main>
    );
}

function Workspace({user, logout}: { user: Account; logout: () => void }) {
    const items = nav.filter((n) => n.roles.includes(user.role));
    const [page, setPage] = useState(items[0].id);
    const [revision, setRevision] = useState(0);
    const [mobile, setMobile] = useState(false);
    const refresh = useCallback(() => setRevision((n) => n + 1), []);
    const action = useAction(refresh);
    useEffect(() => {
        if (!action.message) return;
        const t = setTimeout(() => action.setMessage(""), 4500);
        return () => clearTimeout(t);
    }, [action.message]);
    return (
        <RefreshContext.Provider value={revision}>
            <ErrorContext.Provider value={action.error}>
                <div className="app-shell">
                    <aside className={mobile ? "sidebar expanded" : "sidebar"}>
                        <div className="brand"><strong>Купальни Юбабы</strong></div>
                        <nav>
                            {items.map((n) => (
                                <button
                                    className={page === n.id ? "active" : ""}
                                    key={n.id}
                                    onClick={() => {
                                        setPage(n.id);
                                        setMobile(false);
                                        action.setError("");
                                    }}
                                >
                                    {n.label}
                                </button>
                            ))}
                        </nav>
                        <div className="sidebar-bottom">
                            <div className="profile">
                                <div>
                                    <strong>{user.name}</strong>
                                    <small>{roles[user.role]}</small>
                                </div>
                                <button
                                    className="icon-button"
                                    aria-label="Выйти"
                                    onClick={logout}
                                >
                                    <LogOut size={18}/>
                                </button>
                            </div>
                        </div>
                    </aside>
                    <div className="main">
                        <header className="topbar">
                            <button
                                className="icon-button mobile-menu"
                                aria-label="Меню"
                                onClick={() => setMobile(!mobile)}
                            >
                                <Menu/>
                            </button>
                            <span>
                Купальни Юбабы <span className="slash">/</span>{" "}
                                <strong>{items.find((i) => i.id === page)?.label}</strong>
              </span>
                            <span className="top-date">
                {new Date().toLocaleDateString("ru-RU", {
                    day: "numeric",
                    month: "long",
                    year: "numeric",
                    timeZone: "Europe/Moscow",
                })}
              </span>
                        </header>
                        <main className="content">
                            <Notice error={action.error}/>
                            {action.message && (
                                <div role="status" className="toast">
                                    {action.message}
                                </div>
                            )}
                            <Content
                                page={page}
                                user={user}
                                perform={action.run}
                                busy={action.busy}
                            />
                        </main>
                        <footer className="app-footer">
                            Система управления купальнями{" "}
                            <span>Время в системе — МСК</span>
                        </footer>
                    </div>
                </div>
            </ErrorContext.Provider>
        </RefreshContext.Provider>
    );
}

function Content({
                     page,
                     user,
                     perform,
                     busy,
                 }: {
    page: string;
    user: Account;
    perform: ReturnType<typeof useAction>["run"];
    busy: boolean;
}) {
    const {data: cat, error, loading} = useData<Catalog>("/catalog", 5000);
    if (loading) return <Loading/>;
    if (error) return <Notice error={error}/>;
    if (!cat) return null;
    switch (page) {
        case "dashboard":
            return <DashboardView/>;
        case "orders":
            return (
                <Orders cat={cat} role={user.role} perform={perform} busy={busy}/>
            );
        case "clients":
            return <Clients perform={perform} busy={busy}/>;
        case "templates":
            return <Templates cat={cat} perform={perform} busy={busy}/>;
        case "boiler":
            return <Boiler perform={perform} busy={busy}/>;
        case "schedule":
            return <Schedule cat={cat} perform={perform} busy={busy}/>;
        case "finance":
            return <Finance cat={cat} perform={perform} busy={busy}/>;
        case "inventory":
            return <Inventory cat={cat} perform={perform} busy={busy}/>;
        case "accounts":
            return <Accounts perform={perform} busy={busy}/>;
        default:
            return null;
    }
}
