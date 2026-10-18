import { guarded } from "@/lib/auth";
import { deleteCountdown, updateCountdown } from "@/lib/countdowns";
import { countdownFields } from "@/lib/validate";

export const runtime = "nodejs";

/** Rename, restyle, change where it started or how often it may be counted. */
export const PATCH = guarded(async (request, ctx) => {
  const { id } = await ctx.params;
  const fields = countdownFields(await request.json(), id);
  if (typeof fields === "string") return Response.json({ ok: false, error: fields }, { status: 400 });
  await updateCountdown(id, fields);
  return Response.json({ ok: true });
});

export const DELETE = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await deleteCountdown(id);
  return Response.json({ ok: true });
});
