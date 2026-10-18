import "server-only";
import mysql from "mysql2/promise";

// Single shared pool against the system MySQL (3306), where the `countdown` database
// lives — the same arrangement as tally, veggieBox and lawSuits. The password comes
// from .env.local only: a missing one fails the first query with a sentence rather
// than quietly trying a default.
const globalForDb = globalThis as typeof globalThis & { _countdownPool?: mysql.Pool };

function createPool() {
  const password = process.env.DB_PASSWORD;
  if (!password) throw new Error("DB_PASSWORD is not set on the server (.env.local)");
  return mysql.createPool({
    host: process.env.DB_HOST ?? "127.0.0.1",
    port: Number(process.env.DB_PORT ?? 3306),
    user: process.env.DB_USER ?? "countdown",
    password,
    database: process.env.DB_NAME ?? "countdown",
    waitForConnections: true,
    connectionLimit: 5,
    charset: "utf8mb4",
    dateStrings: true,
  });
}

export function pool(): mysql.Pool {
  globalForDb._countdownPool ??= createPool();
  return globalForDb._countdownPool;
}

export async function q<T = Record<string, unknown>>(sql: string, params?: unknown[]): Promise<T[]> {
  const [rows] = await pool().query(sql, params);
  return rows as T[];
}

export async function exec(sql: string, params?: unknown[]) {
  const [res] = await pool().query(sql, params);
  return res as mysql.ResultSetHeader;
}
