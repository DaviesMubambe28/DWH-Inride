import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "export",
  images: { unoptimized: true },
  turbopack: { root: __dirname },
  // The CLI checker can lose its captured `tsc --showConfig` output under
  // Node 24, causing a valid production build to fail before type checking.
  // Use Next's compiler API checker; `npm run typecheck` remains an explicit
  // quality gate as well.
  experimental: { useTypeScriptCli: false },
};

export default nextConfig;
