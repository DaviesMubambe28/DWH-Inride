function httpsUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password ? url : null;
  } catch { return null; }
}

export function validateAdminUrl(value) {
  return Boolean(httpsUrl(value));
}

export function validateRelease(release) {
  const url = httpsUrl(release?.url);
  if (!url || typeof release.version !== "string" || !/^\d+\.\d+\.\d+(?:[-+][\w.-]+)?$/.test(release.version)
      || typeof release.android !== "string" || !/^\d+(?:\.\d+)*$/.test(release.android)) return false;
  if (release.kind === "play") return url.hostname === "play.google.com" && url.pathname === "/store/apps/details" && Boolean(url.searchParams.get("id"));
  return release.kind === "apk" && /\.apk$/i.test(url.pathname) && !/debug/i.test(url.pathname);
}
