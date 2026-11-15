import { guarded } from "@/lib/auth";
import { addTick } from "@/lib/countdowns";

export const runtime = "nodejs";

/** Count one off. The phone sends the time — it owns the clock, see lib/countdowns.ts. */
export const POST = guarded(async (request, ctx) => {
  const { id } = await ctx.params;
  const b = await request.json();
  const tickId = String(b.id ?? "").trim();
  const at = Number(b.at);
  if (!tickId || !at) return Response.json({ ok: false, error: "id and at are required" }, { status: 400 });
  await addTick(id, { id: tickId, note: String(b.note ?? ""), job: String(b.job ?? "").trim(), at });
  return Response.json({ ok: true });
});
