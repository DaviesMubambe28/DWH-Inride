export const packageNames = {
  passenger: "zm.co.dhwinride.passenger",
  rider: "zm.co.dhwinride.rider",
};
function httpsUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password && !url.hash ? url : null;
  } catch { return null; }
}
export function validateAdminUrl(value) { return Boolean(httpsUrl(value)); }
export function validateRelease(release, app) {
  const url = httpsUrl(release?.url);
  if (!url || release.kind !== "apk" || !/\.apk$/i.test(url.pathname) || /debug/i.test(url.pathname)) return false;
  if (typeof release.version !== "string" || !/^\d+\.\d+\.\d+(?:[-+][\w.-]+)?$/.test(release.version)) return false;
  if (!Number.isSafeInteger(release.bytes) || release.bytes <= 0) return false;
  if (typeof release.android !== "string" || !/^\d+(?:\.\d+)*$/.test(release.android)) return false;
  if (typeof release.releaseDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(release.releaseDate)
      || !Number.isFinite(Date.parse(release.releaseDate)) || new Date(release.releaseDate).toISOString().slice(0,10) !== release.releaseDate) return false;
  if (release.packageName !== packageNames[app]) return false;
  return /^[a-f0-9]{64}$/i.test(release.sha256 || "") && /^[a-f0-9]{64}$/i.test(release.certificateSha256 || "");
}
