import "server-only";
import { q, exec, pool } from "./db";
import { putIncome, removeIncome, VatModes, type VatMode } from "./tazrim";

/**
 * Every query in one place.
 *
 * A delete here is a real DELETE. The records live off the phone so that *accidents*
 * — a wipe, a reinstall, a stray adb command — cannot destroy them; a delete the user
 * chose is not an accident, and a row that quietly stays behind a flag is not a
 * delete. Undo works because the client still holds what it removed for as long as
 * the snackbar is up, and puts it back with `saveCountdown`.
 */

export type Tick = {
  id: string;
  note: string;
  /** Which job this was — "the Ofakim roof". Empty when not said. */
  job: string;
  /** Where the phone was when it was counted. Null when it was not asked or not known. */
  lat: number | null;
  lon: number | null;
  /** `lat`/`lon` as words, resolved here on the server. Empty until resolved. */
  place: string;
  /** What the day paid, before or including VAT as `vat` says. Null when not said. */
  amount: number | null;
  /** "plus" (1,500 + VAT), "included", "none"; empty when there is no amount. */
  vat: VatMode | "";
  /**
   * Why tazrim does not have this day's income yet, in words; empty when it does (or
   * when there is nothing to send). The phone shows it on the day, and every list
   * tries again, so a day can be out of the forecast only while it says so.
   */
  tazrimError: string;
  at: number;
};

export type Countdown = {
  id: string;
  name: string;
  /** Where it began. Never decremented: what is left is start minus the ticks. */
  start: number;
  unit: string;
  accent: number;
  oncePerDay: boolean;
  /** Ask which job after each count, and record where it was counted. */
  askJob: boolean;
  createdAt: number;
  ticks: Tick[];
};

export type CountdownFields = Omit<Countdown, "ticks">;

// Times are UTC end to end. MySQL's CURRENT_TIMESTAMP writes in the *server's* zone
// (IDT here), which read back as UTC puts every row three hours in the future (tally
// shipped that bug) — so nothing relies on a column default: the client owns the
// clock and every write passes an explicit UTC string.
const ms = (s: string) => new Date(s.replace(" ", "T") + "Z").getTime();
const sqlTime = (epochMs: number) => new Date(epochMs).toISOString().slice(0, 19).replace("T", " ");

export const MaxStart = 100_000;

export async function listCountdowns(): Promise<Countdown[]> {
  const rows = await q<{
    id: string; name: string; start_count: number; unit: string; accent: number;
    once_per_day: number; ask_job: number; created_at: string;
  }>(`SELECT id, name, start_count, unit, accent, once_per_day, ask_job, created_at
        FROM countdowns ORDER BY created_at`);
  if (rows.length === 0) return [];

  await retryFailedIncome();

  const ticks = await q<{
    id: string; countdown_id: string; note: string; job: string;
    lat: number | null; lon: number | null; place: string;
    amount: string | null; vat: string; tazrim_error: string; at: string;
  }>(
    `SELECT id, countdown_id, note, job, lat, lon, place, amount, vat, tazrim_error, at FROM ticks
      WHERE countdown_id IN (${rows.map(() => "?").join(",")}) ORDER BY at`,
    rows.map((r) => r.id),
  );

  const byCountdown = new Map<string, Tick[]>();
  for (const t of ticks) {
    const list = byCountdown.get(t.countdown_id) ?? [];
    list.push({
      id: t.id, note: t.note, job: t.job,
      lat: t.lat === null ? null : Number(t.lat), lon: t.lon === null ? null : Number(t.lon),
      place: t.place,
      amount: t.amount === null ? null : Number(t.amount),
      vat: (VatModes as readonly string[]).includes(t.vat) ? (t.vat as VatMode) : "",
      tazrimError: t.tazrim_error,
      at: ms(t.at),
    });
    byCountdown.set(t.countdown_id, list);
  }

  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    start: Number(r.start_count),
    unit: r.unit,
    accent: Number(r.accent),
    oncePerDay: Boolean(r.once_per_day),
    askJob: Boolean(r.ask_job),
    createdAt: ms(r.created_at),
    ticks: byCountdown.get(r.id) ?? [],
  }));
}

/**
 * Creates a countdown, and — when `ticks` is given — puts them back with their
 * original ids and timestamps. That second form is what Undo and Duplicate use, so an
 * undone delete restores the countdown exactly as it was rather than as a fresh one
 * dated today. Idempotent, so a retried undo is harmless.
 */
export async function saveCountdown(c: CountdownFields, ticks?: Tick[]) {
  const conn = await pool().getConnection();
  try {
    await conn.beginTransaction();
    await conn.query(
      `INSERT INTO countdowns (id, name, start_count, unit, accent, once_per_day, ask_job, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE name = VALUES(name), start_count = VALUES(start_count),
                               unit = VALUES(unit), accent = VALUES(accent),
                               once_per_day = VALUES(once_per_day), ask_job = VALUES(ask_job)`,
      [c.id, c.name.slice(0, 64), c.start, c.unit.slice(0, 24), c.accent, c.oncePerDay ? 1 : 0,
       c.askJob ? 1 : 0, sqlTime(c.createdAt)],
    );
    for (const t of ticks ?? []) {
      const p = pay(t.amount, t.vat);
      await conn.query(
        `INSERT INTO ticks (id, countdown_id, note, job, lat, lon, place, amount, vat, at)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE note = VALUES(note), job = VALUES(job), lat = VALUES(lat),
                                 lon = VALUES(lon), place = VALUES(place), amount = VALUES(amount),
                                 vat = VALUES(vat), at = VALUES(at)`,
        [t.id, c.id, String(t.note ?? "").slice(0, 255), String(t.job ?? "").slice(0, 80),
         coord(t.lat, 90), coord(t.lon, 180), String(t.place ?? "").slice(0, 160),
         p.amount, p.vat, sqlTime(t.at)],
      );
    }
    await conn.commit();
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
  // An undo putting back a paid day puts its income back too (same key, so it cannot
  // land twice). A duplicate's ticks arrive without amounts — a what-if copy earned
  // nothing — so it sends nothing.
  for (const t of ticks ?? []) if (pay(t.amount, t.vat).amount !== null) await syncIncome(t.id);
}

export async function updateCountdown(id: string, c: Omit<CountdownFields, "id" | "createdAt">) {
  await exec(
    `UPDATE countdowns SET name = ?, start_count = ?, unit = ?, accent = ?, once_per_day = ?, ask_job = ?
      WHERE id = ?`,
    [c.name.slice(0, 64), c.start, c.unit.slice(0, 24), c.accent, c.oncePerDay ? 1 : 0, c.askJob ? 1 : 0, id],
  );
}

/** Gone. The foreign key takes its ticks with it — and their incomes go from tazrim first. */
export async function deleteCountdown(id: string) {
  await removeIncomesOf(`countdown_id = ?`, [id]);
  await exec(`DELETE FROM countdowns WHERE id = ?`, [id]);
}

/** Back to the starting number: the ticks go, the countdown stays. */
export async function resetCountdown(id: string) {
  await removeIncomesOf(`countdown_id = ?`, [id]);
  await exec(`DELETE FROM ticks WHERE countdown_id = ?`, [id]);
}

export async function addTick(
  countdownId: string,
  t: Pick<Tick, "id" | "note" | "job" | "at"> & { amount?: unknown; vat?: unknown },
) {
  const p = pay(t.amount, t.vat);
  await exec(`INSERT INTO ticks (id, countdown_id, note, job, amount, vat, at) VALUES (?, ?, ?, ?, ?, ?, ?)`,
    [t.id, countdownId, t.note.slice(0, 255), t.job.slice(0, 80), p.amount, p.vat, sqlTime(t.at)]);
  if (p.amount !== null) await syncIncome(t.id);
}

/**
 * What the day paid. A missing or non-positive amount clears it (the day stays, its
 * income goes); an amount without a valid VAT mode is refused rather than guessed.
 */
export function pay(amount: unknown, vat: unknown): { amount: number | null; vat: VatMode | "" } {
  const n = amount === null || amount === undefined || amount === "" ? NaN : Number(amount);
  if (!(n > 0)) return { amount: null, vat: "" };
  if (!(VatModes as readonly unknown[]).includes(vat))
    throw new Error(`vat must be one of ${VatModes.join(", ")} when an amount is given`);
  return { amount: Math.round(n * 100) / 100, vat: vat as VatMode };
}

/** Set (or clear) what a day paid, and bring tazrim in line with it. */
export async function setTickPay(id: string, amount: unknown, vat: unknown) {
  const p = pay(amount, vat);
  await exec(`UPDATE ticks SET amount = ?, vat = ? WHERE id = ?`, [p.amount, p.vat, id]);
  await syncIncome(id);
}

/**
 * Makes tazrim hold exactly this day's income — written when it has an amount,
 * removed when it has none. Never throws: the day is saved either way, and a failure
 * is written on the day (`tazrim_error`) for the phone to show and the next list to
 * retry.
 */
export async function syncIncome(id: string) {
  const [t] = await q<{ id: string; note: string; job: string; amount: string | null; vat: string; at: string }>(
    `SELECT id, note, job, amount, vat, at FROM ticks WHERE id = ?`, [id]);
  if (!t) return;
  let error = "";
  try {
    if (t.amount === null) await removeIncome(t.id);
    else await putIncome({
      tickId: t.id, at: ms(t.at), job: t.job, note: t.note, amount: Number(t.amount), vat: t.vat as VatMode,
    });
  } catch (e) {
    error = (e instanceof Error ? e.message : String(e)).slice(0, 200);
    console.error(`[countdown] tick ${id}: ${error}`);
  }
  await exec(`UPDATE ticks SET tazrim_error = ? WHERE id = ?`, [error, id]);
}

/** A new job or note renames the income — only for a day that has (or failed to send) one. */
export async function syncIncomeIfPaid(id: string) {
  const [t] = await q<{ paid: number }>(
    `SELECT (amount IS NOT NULL OR tazrim_error <> '') AS paid FROM ticks WHERE id = ?`, [id]);
  if (t && Number(t.paid)) await syncIncome(id);
}

/** Every day tazrim is still missing, tried again. */
async function retryFailedIncome() {
  const failed = await q<{ id: string }>(`SELECT id FROM ticks WHERE tazrim_error <> ''`);
  for (const t of failed) await syncIncome(t.id);
}

/**
 * Before ticks are deleted, their incomes leave tazrim. This one throws: deleting a
 * day while its money stayed in the forecast would be the silent drift this link
 * exists to prevent, so the delete is refused with the reason instead.
 */
async function removeIncomesOf(where: string, params: unknown[]) {
  const ticks = await q<{ id: string }>(
    `SELECT id FROM ticks WHERE (${where}) AND (amount IS NOT NULL OR tazrim_error <> '')`, params);
  for (const t of ticks) {
    try {
      await removeIncome(t.id);
    } catch (e) {
      throw new Error(`Not removed: its income is still in tazrim, which ${
        (e instanceof Error ? e.message : String(e)).replace(/^tazrim /, "")}`);
    }
  }
}

/** A coordinate the database may hold, or null for anything that is not one. */
export function coord(v: unknown, limit: number): number | null {
  const n = typeof v === "number" ? v : v === null || v === undefined || v === "" ? NaN : Number(v);
  return Number.isFinite(n) && Math.abs(n) <= limit ? n : null;
}

/** What was said about a tick afterwards: its note and its job. When it happened is a fact, not a setting. */
export async function updateTickWords(id: string, words: { note?: string; job?: string }) {
  if (words.note !== undefined) await exec(`UPDATE ticks SET note = ? WHERE id = ?`, [words.note.slice(0, 255), id]);
  if (words.job !== undefined) await exec(`UPDATE ticks SET job = ? WHERE id = ?`, [words.job.slice(0, 80), id]);
}

/**
 * Where it was counted. The phone sends this a few seconds after the count — a GPS
 * fix takes longer than a tap — so it is its own write. The place name is cleared
 * here and filled in by [setTickPlace] once it is resolved.
 */
export async function setTickLocation(id: string, lat: number, lon: number) {
  await exec(`UPDATE ticks SET lat = ?, lon = ?, place = '' WHERE id = ?`, [lat, lon, id]);
}

/** Only lands if the tick still stands where it was resolved for. */
export async function setTickPlace(id: string, lat: number, lon: number, place: string) {
  await exec(`UPDATE ticks SET place = ? WHERE id = ? AND lat = ? AND lon = ?`, [place.slice(0, 160), id, lat, lon]);
}

export async function deleteTick(id: string) {
  await removeIncomesOf(`id = ?`, [id]);
  await exec(`DELETE FROM ticks WHERE id = ?`, [id]);
}
