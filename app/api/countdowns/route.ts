import { guarded } from "@/lib/auth";
import { listCountdowns, saveCountdown, type Tick } from "@/lib/countdowns";
import { countdownFields } from "@/lib/validate";

export const runtime = "nodejs";

/** Everything the app shows, in one round trip — a countdown is small and the phone
 *  is often on a slow link, so paging would cost more than it saves. */
export const GET = guarded(async () => Response.json({ ok: true, countdowns: await listCountdowns() }));

/**
 * Create a countdown — or put one back. Undo and Duplicate send the countdown with
 * its ticks, so it lands exactly as it was rather than as a fresh one dated today.
 */
export const POST = guarded(async (request) => {
  const b = await request.json();
  const fields = countdownFields(b, String(b.id ?? "").trim());
  if (typeof fields === "string") return Response.json({ ok: false, error: fields }, { status: 400 });
  const ticks = Array.isArray(b.ticks) ? (b.ticks as Tick[]) : undefined;
  await saveCountdown(fields, ticks);
  return Response.json({ ok: true });
});
