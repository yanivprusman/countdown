import { reverseGeocode } from "@automatelinux/geo";
import { guarded } from "@/lib/auth";
import { coord, deleteTick, setTickLocation, setTickPay, setTickPlace, syncIncomeIfPaid, updateTickWords } from "@/lib/countdowns";

export const runtime = "nodejs";

/**
 * What can change about a tick after it was counted: its note, its job, what it paid
 * (`amount` + `vat`, which becomes its income in tazrim), and where it
 * was — the phone sends the location a few seconds after the count, since a fix takes
 * longer than a tap. When it happened is a fact, not a setting, and never changes.
 *
 * A location is answered with the place it resolved to, so the phone can show
 * "Herzl 12, Ofakim" without another round trip.
 */
export const PATCH = guarded(async (request, ctx) => {
  const { id } = await ctx.params;
  const b = await request.json();

  const words: { note?: string; job?: string } = {};
  if ("note" in b) words.note = String(b.note ?? "").trim();
  if ("job" in b) words.job = String(b.job ?? "").trim();
  await updateTickWords(id, words);

  // What the day paid goes to tazrim with it; a new job renames the income there too.
  if ("amount" in b) await setTickPay(id, b.amount, b.vat);
  else if (words.job !== undefined || words.note !== undefined) await syncIncomeIfPaid(id);

  if (!("lat" in b) && !("lon" in b)) return Response.json({ ok: true });

  const lat = coord(b.lat, 90);
  const lon = coord(b.lon, 180);
  if (lat === null || lon === null)
    return Response.json({ ok: false, error: "lat and lon must be coordinates" }, { status: 400 });
  await setTickLocation(id, lat, lon);
  try {
    const place = await reverseGeocode(lat, lon);
    await setTickPlace(id, lat, lon, place);
    return Response.json({ ok: true, place });
  } catch (e) {
    // The coordinates are saved and shown; only their name is missing. Say so in the
    // log and in the reply rather than inventing one.
    console.error(`[countdown] reverse geocode failed for tick ${id} (${lat}, ${lon}):`, e);
    return Response.json({ ok: true, place: "", placeError: "Could not name this place" });
  }
});

export const DELETE = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await deleteTick(id);
  return Response.json({ ok: true });
});

