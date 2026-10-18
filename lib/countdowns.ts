import "server-only";
import { q, exec, pool } from "./db";

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
    once_per_day: number; created_at: string;
  }>(`SELECT id, name, start_count, unit, accent, once_per_day, created_at
        FROM countdowns ORDER BY created_at`);
  if (rows.length === 0) return [];

  const ticks = await q<{ id: string; countdown_id: string; note: string; at: string }>(
    `SELECT id, countdown_id, note, at FROM ticks
      WHERE countdown_id IN (${rows.map(() => "?").join(",")}) ORDER BY at`,
    rows.map((r) => r.id),
  );

  const byCountdown = new Map<string, Tick[]>();
  for (const t of ticks) {
    const list = byCountdown.get(t.countdown_id) ?? [];
    list.push({ id: t.id, note: t.note, at: ms(t.at) });
    byCountdown.set(t.countdown_id, list);
  }

  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    start: Number(r.start_count),
    unit: r.unit,
    accent: Number(r.accent),
    oncePerDay: Boolean(r.once_per_day),
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
      `INSERT INTO countdowns (id, name, start_count, unit, accent, once_per_day, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE name = VALUES(name), start_count = VALUES(start_count),
                               unit = VALUES(unit), accent = VALUES(accent),
                               once_per_day = VALUES(once_per_day)`,
      [c.id, c.name.slice(0, 64), c.start, c.unit.slice(0, 24), c.accent, c.oncePerDay ? 1 : 0,
       sqlTime(c.createdAt)],
    );
    for (const t of ticks ?? []) {
      await conn.query(
        `INSERT INTO ticks (id, countdown_id, note, at) VALUES (?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE note = VALUES(note), at = VALUES(at)`,
        [t.id, c.id, String(t.note ?? "").slice(0, 255), sqlTime(t.at)],
      );
    }
    await conn.commit();
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}

export async function updateCountdown(id: string, c: Omit<CountdownFields, "id" | "createdAt">) {
  await exec(
    `UPDATE countdowns SET name = ?, start_count = ?, unit = ?, accent = ?, once_per_day = ?
      WHERE id = ?`,
    [c.name.slice(0, 64), c.start, c.unit.slice(0, 24), c.accent, c.oncePerDay ? 1 : 0, id],
  );
}

/** Gone. The foreign key takes its ticks with it. */
export async function deleteCountdown(id: string) {
  await exec(`DELETE FROM countdowns WHERE id = ?`, [id]);
}

/** Back to the starting number: the ticks go, the countdown stays. */
export async function resetCountdown(id: string) {
  await exec(`DELETE FROM ticks WHERE countdown_id = ?`, [id]);
}

export async function addTick(countdownId: string, t: Tick) {
  await exec(`INSERT INTO ticks (id, countdown_id, note, at) VALUES (?, ?, ?, ?)`,
    [t.id, countdownId, t.note.slice(0, 255), sqlTime(t.at)]);
}

export async function updateTickNote(id: string, note: string) {
  await exec(`UPDATE ticks SET note = ? WHERE id = ?`, [note.slice(0, 255), id]);
}

export async function deleteTick(id: string) {
  await exec(`DELETE FROM ticks WHERE id = ?`, [id]);
}
