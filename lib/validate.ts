import "server-only";
import { MaxStart, type CountdownFields } from "./countdowns";

/** The fields of a countdown as the phone sent them, or a sentence saying what is wrong. */
export function countdownFields(b: Record<string, unknown>, id: string): CountdownFields | string {
  const name = String(b.name ?? "").trim();
  const start = Number(b.start);
  const unit = String(b.unit ?? "").trim();
  if (!id || !name) return "id and name are required";
  if (!Number.isInteger(start) || start < 1 || start > MaxStart)
    return `start must be a whole number from 1 to ${MaxStart}`;
  if (!unit) return "unit is required";
  return {
    id,
    name,
    start,
    unit,
    accent: Number(b.accent ?? 0) | 0,
    oncePerDay: Boolean(b.oncePerDay),
    createdAt: Number(b.createdAt) || Date.now(),
  };
}
