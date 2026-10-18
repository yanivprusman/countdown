const points = [
  {
    title: "The start stays",
    body: "Set 100 and it stays 100. What is left is worked out from what you have counted, so the number you began with is always on screen next to the one you are at.",
  },
  {
    title: "One tap a day",
    body: "Each tap takes one off, with Undo. Count twice on the same day and it asks first — a double tap cannot cost you a day.",
  },
  {
    title: "Several at once",
    body: "Days of work, runs, books — each countdown keeps its own history, streak and colour, side by side.",
  },
];

export default function Home() {
  return (
    <main className="min-h-screen bg-[#16130F] text-[#F2EAE0] antialiased">
      <div className="mx-auto max-w-2xl px-6 py-24 sm:py-32">
        <div className="relative h-[84px] w-[92px]" aria-hidden>
          <span className="absolute top-[8px] left-[6px] h-[72px] w-[86px] rounded-[14px] bg-[#3A3129]" />
          <span className="absolute top-[2px] left-0 h-[72px] w-[86px] overflow-hidden rounded-[14px] bg-[#F2EAE0]">
            <span className="block h-[18px] bg-[#F0A07A]" />
          </span>
          <span className="absolute -top-[6px] left-[20px] h-[16px] w-[7px] rounded-full bg-[#8A7E71]" />
          <span className="absolute -top-[6px] left-[60px] h-[16px] w-[7px] rounded-full bg-[#8A7E71]" />
          <span className="absolute top-[40px] left-[16px] font-bold text-[34px] leading-none tracking-tight text-[#2B1F17]">
            −1
          </span>
        </div>

        <h1 className="mt-10 text-4xl font-bold tracking-tight sm:text-5xl">Countdown</h1>
        <p className="mt-4 text-lg leading-relaxed text-[#ADA093]">
          An Android app for counting down toward a goal you set — a hundred days of work, thirty
          runs, twelve books. Tap once when one is done.
        </p>

        <dl className="mt-14 space-y-8">
          {points.map((p) => (
            <div key={p.title} className="rounded-2xl bg-[#211C17] p-6">
              <dt className="text-base font-semibold">{p.title}</dt>
              <dd className="mt-2 text-[15px] leading-relaxed text-[#ADA093]">{p.body}</dd>
            </div>
          ))}
        </dl>

        <p className="mt-14 text-sm leading-relaxed text-[#8A7E71]">
          The records live in a database on the home network, reachable over the VPN — the phone
          holds a view of them, not the only copy. A lost phone or a reinstall costs nothing.
        </p>
      </div>
    </main>
  );
}
