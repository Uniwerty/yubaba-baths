import {createContext, useCallback, useContext, useEffect, useState,} from "react";

export const RefreshContext = createContext(0);
let token = sessionStorage.getItem("yubaba-token") || "";

export function setToken(value: string) {
    token = value;
    if (value) sessionStorage.setItem("yubaba-token", value);
    else sessionStorage.removeItem("yubaba-token");
}

export async function api<T>(
    path: string,
    method = "GET",
    body?: unknown,
): Promise<T> {
    const response = await fetch("/api" + path, {
        method,
        headers: {
            "Content-Type": "application/json",
            ...(token ? {Authorization: "Bearer " + token} : {}),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
        if (response.status === 401 && path != "/auth/login")
            window.dispatchEvent(new Event("session-expired"));
        const error = await response
            .json()
            .catch(() => ({message: "Не удалось связаться с сервером."}));
        throw new Error(
            error.message || "Не удалось выполнить действие. Повторите попытку.",
        );
    }
    return response.json();
}

export function useData<T>(path: string | null, poll = 0) {
    const [data, setData] = useState<T>();
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);
    const refresh = useContext(RefreshContext);
    useEffect(() => {
        let live = true;
        let busy = false;
        setData(undefined);
        setError("");
        setLoading(true);
        const run = async () => {
            if (!path || busy) return;
            busy = true;
            try {
                const value = await api<T>(path);
                if (live) {
                    setData(value);
                    setError("");
                }
            } catch (e) {
                if (live) setError((e as Error).message);
            } finally {
                busy = false;
                if (live) setLoading(false);
            }
        };
        void run();
        const id = poll ? setInterval(run, poll) : undefined;
        return () => {
            live = false;
            if (id) clearInterval(id);
        };
    }, [path, poll, refresh]);
    return {data, error, loading};
}

export function useAction(refresh: () => void) {
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const run = useCallback(
        async (job: () => Promise<unknown>, success = "Изменения сохранены") => {
            setBusy(true);
            setError("");
            try {
                await job();
                setMessage(success);
                refresh();
                return true;
            } catch (e) {
                setError((e as Error).message);
                return false;
            } finally {
                setBusy(false);
            }
        },
        [refresh],
    );
    return {busy, message, error, run, setError, setMessage};
}

export async function download(path: string, filename: string) {
    const response = await fetch("/api" + path, {
        headers: {Authorization: "Bearer " + token},
    });
    if (!response.ok) throw new Error("Не удалось выгрузить документ.");
    const url = URL.createObjectURL(await response.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
}

export const query = (f: object) =>
    "?" +
    new URLSearchParams(
        Object.entries(f)
            .filter(([, v]) => v !== null && v !== "" && v !== undefined)
            .map(([k, v]) => [k, String(v)]),
    ).toString();
