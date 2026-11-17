import "server-only";

/**
 * A counted day that says what it paid is an income in tazrim, the cash-flow app.
 *
 * The day owns that income: tazrim keys the row by `countdown:<tickId>`, so saving the
 * day again (an edit, an undo putting it back) updates the one row, and removing the
 * day removes it. Counting a day once is all it takes — nobody has to type the same
 * day into the forecast a second time.
 *
 * Both servers run on the desktop, so this is a call on localhost, made from whatever
 * device counted the day. `TAZRIM_URL` must be set; there is no default to guess at.
 */

export type VatMode = "plus" | "included" | "none";
export const VatModes: readonly VatMode[] = ["plus", "included", "none"];

export type PaidDay = {
  tickId: string;
  /** Epoch ms of the count; the income is dated on that day in Israel. */
  at: number;
  job: string;
  note: string;
  amount: number;
  vat: VatMode;
};

const ref = (tickId: string) => `countdown:${tickId}`;

function base(): string {
  const url = process.env.TAZRIM_URL;
  if (!url) throw new Error("TAZRIM_URL is not set on the countdown server (.env.local)");
  return url.replace(/\/+$/, "");
}

/** The calendar day of `at` in Israel, as tazrim stores manual rows: YYYY-MM-DD. */
export function israelDay(at: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jerusalem" }).format(new Date(at));
}

/** "יומית — דולפינריום, תל אביב": the same wording tazrim's own quick income uses. */
export function incomeDescription(job: string, note: string): string {
  const what = job.trim() || note.trim();
  return what ? `יומית — ${what}`.slice(0, 190) : "יומית";
}

async function call(method: "PUT" | "DELETE", path: string, body?: unknown): Promise<void> {
  let res: Response;
  try {
    res = await fetch(`${base()}${path}`, {
      method,
      headers: body ? { "content-type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(15_000),
    });
  } catch (e) {
    throw new Error(`tazrim did not answer (${e instanceof Error ? e.message : String(e)})`);
  }
  const reply = (await res.json().catch(() => null)) as { ok?: boolean; error?: string } | null;
  if (!res.ok || !reply?.ok) throw new Error(`tazrim refused it: ${reply?.error ?? `HTTP ${res.status}`}`);
}

/** Write (or rewrite) the day's income. Throws a sentence on failure. */
export async function putIncome(d: PaidDay): Promise<void> {
  await call("PUT", "/api/work-days", {
    ref: ref(d.tickId),
    date: israelDay(d.at),
    net: d.amount,
    vat: d.vat,
    description: incomeDescription(d.job, d.note),
  });
}

/** Remove the day's income, if it has one. Removing one that is not there is fine. */
export async function removeIncome(tickId: string): Promise<void> {
  await call("DELETE", `/api/work-days?ref=${encodeURIComponent(ref(tickId))}`);
}
