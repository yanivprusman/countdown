import { guarded } from "@/lib/auth";
import { deleteTick, updateTickNote } from "@/lib/countdowns";

export const runtime = "nodejs";

/** Only the note changes. When it happened is a fact, not a setting. */
export const PATCH = guarded(async (request, ctx) => {
  const { id } = await ctx.params;
  const b = await request.json();
  await updateTickNote(id, String(b.note ?? ""));
  return Response.json({ ok: true });
});

export const DELETE = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await deleteTick(id);
  return Response.json({ ok: true });
});
