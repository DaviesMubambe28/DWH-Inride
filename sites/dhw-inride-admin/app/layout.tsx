import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "DHW-inRide Operations",
  description: "Operations, safety, rider verification and finance control centre for DHW-inRide.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
