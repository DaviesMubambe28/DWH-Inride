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
export function validateAdminUrl(value) { const url = httpsUrl(value); return Boolean(url && !url.search); }
export function validateRelease(release, app) {
  if (!Object.hasOwn(packageNames, app)) return false;
  const url = httpsUrl(release?.url);
  if (!url || release.kind !== "apk" || !/\.apk$/i.test(url.pathname) || /debug|development|unsigned|no-push/i.test(url.pathname) || url.search) return false;
  if (typeof release.version !== "string" || !/^\d+\.\d+\.\d+(?:[-+][\w.-]+)?$/.test(release.version)) return false;
  if (/debug|development|unsigned|no-push/i.test(release.version)) return false;
  if (!Number.isSafeInteger(release.bytes) || release.bytes <= 0) return false;
  if (typeof release.android !== "string" || !/^\d+(?:\.\d+)*$/.test(release.android)) return false;
  if (typeof release.releaseDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(release.releaseDate)
      || !Number.isFinite(Date.parse(release.releaseDate)) || new Date(release.releaseDate).toISOString().slice(0,10) !== release.releaseDate) return false;
  if (release.packageName !== packageNames[app]) return false;
  return /^[a-f0-9]{64}$/i.test(release.sha256 || "") && /^[a-f0-9]{64}$/i.test(release.certificateSha256 || "");
}

// HTML download is advisory cross-origin; the file host must initiate attachment delivery.
export function validateDownloadHeaders(headers) {
  const mime = (headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
  return ["application/vnd.android.package-archive", "application/octet-stream"].includes(mime)
    && /^attachment(?:;|$)/i.test((headers.get("content-disposition") || "").trim());
}
