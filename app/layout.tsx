import type { Metadata } from "next";
import "./globals.css";
import FeedbackChatMount from "./FeedbackChatMount";

export const metadata: Metadata = {
  title: "countdown",
  description: "Count down toward a goal you set — 100 days of work, 30 runs. The starting number stays on screen; one tap counts one off. Several countdowns side by side, each with its own history, reset and delete.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body>{children}
        <FeedbackChatMount />
      </body>
    </html>
  );
}
