import { guarded } from "@/lib/auth";
import { resetCountdown } from "@/lib/countdowns";

export const runtime = "nodejs";

export const POST = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await resetCountdown(id);
  return Response.json({ ok: true });
});
