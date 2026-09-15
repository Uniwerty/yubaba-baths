export type Role = "ADMIN" | "ATTENDANT" | "BOILER" | "ACCOUNTANT" | "MANAGER";
export type Status =
    | "CREATED"
    | "IN_SERVICE"
    | "AWAITING_PAYMENT"
    | "CLOSED"
    | "CANCELLED";

export interface Account {
    id: number;
    version: number;
    login: string;
    name: string;
    role: Role;
    blocked: boolean;
    restUntil: string | null;
}

export interface Client {
    id: number;
    version: number;
    name: string;
    contact: string;
    notes: string;
}

export interface Room {
    id: number;
    name: string;
    bathType: string;
    capacity: number;
}

export interface Ingredient {
    id: number;
    name: string;
    unit: string;
    stock: number;
    reserved: number;
    threshold: number;
    price: number;
}

export interface Line {
    ingredientId: number;
    quantity: number;
    name?: string;
    unit?: string;
    unitPrice?: number;
}

export interface Composition {
    name: string;
    bathType: string;
    preferredRoomId: number | null;
    attendants: number;
    durationMinutes: number;
    preparationMinutes: number;
    breakMinutes: number;
    temperature: number;
    steps: string;
    extraServices: string;
    basePrice: number;
    lines: Line[];
}

export interface Template extends Composition {
    id: number;
    version: number;
}

export interface Order extends Omit<Composition, "name"> {
    id: number;
    version: number;
    clientId: number;
    templateId: number | null;
    serviceName: string;
    visitors: number;
    priority: number;
    total: number;
    status: Status;
    roomId: number | null;
    attendantIds: number[];
    createdAt: string;
    launchedAt: string | null;
    waterReadyAt: string | null;
    serviceStartedAt: string | null;
    completedAt: string | null;
    closedAt: string | null;
    cancellationReason: string | null;
}

export interface Catalog {
    templates: Template[];
    rooms: Room[];
    ingredients: Ingredient[];
}

export interface Payment {
    id: number;
    orderId: number;
    amount: number;
    method: string;
    paidAt: string;
}

export interface Filter {
    from: string;
    to: string;
    templateId: number | null;
    method: string | null;
    visitors: number | null;
}

export interface Report {
    orders: Order[];
    payments: Payment[];
    orderCount: number;
    visitors: number;
    revenue: number;
    empty: boolean;
}

export interface SavedReport {
    id: number;
    name: string;
    parameters: string;
}

export interface Supply {
    id: number;
    ingredientName: string;
    quantity: number;
    unit: string;
    status: string;
    createdAt: string;
}

export interface Dashboard {
    rooms: { room: Room; orders: number[] }[];
    attendants: { account: Account; orders: number[] }[];
    queue: Order[];
    orderCount: number;
    averageMinutes: number;
    revenue: number;
    empty: boolean;
    updatedAt: string;
}
