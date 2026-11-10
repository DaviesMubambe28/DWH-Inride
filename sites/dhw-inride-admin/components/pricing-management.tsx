"use client";

import { FormEvent, useState } from "react";
import { getSupabaseBrowserClient } from "@/lib/supabase";

export type PricingConfig = {
  base_fare_zmw: number; rate_per_km_zmw: number; rate_per_minute_zmw: number;
  minimum_fare_zmw: number; free_waiting_minutes: number; waiting_rate_zmw: number;
  rider_share_percent: number; platform_share_percent: number; rain_pricing_enabled: boolean;
  night_surge_zmw: number; busy_surge_zmw: number; rain_surge_zmw: number;
  maximum_surge_zmw: number; pricing_config_version: number;
};
export type RevenueSummary = {
  total_passenger_revenue_zmw: number; total_rider_earnings_zmw: number;
  total_platform_commission_zmw: number; outstanding_commission_zmw: number;
  settled_commission_zmw: number; daily_revenue_zmw: number;
  weekly_revenue_zmw: number; monthly_revenue_zmw: number;
};

const money = (value: number | undefined) => `K${Number(value ?? 0).toFixed(2)}`;

export function PricingManagement({ config, revenue, available, onChanged }: {
  config: PricingConfig | null; revenue: RevenueSummary | null; available: boolean; onChanged: () => void;
}) {
  const [form, setForm] = useState({
    km: String(config?.rate_per_km_zmw ?? 5), minute: String(config?.rate_per_minute_zmw ?? 1),
    minimum: String(config?.minimum_fare_zmw ?? 21), freeWaiting: String(config?.free_waiting_minutes ?? 3),
    waiting: String(config?.waiting_rate_zmw ?? 1), rider: String(config?.rider_share_percent ?? 85),
    platform: String(config?.platform_share_percent ?? 15),
  });
  const [busy, setBusy] = useState(false); const [message, setMessage] = useState(""); const [error, setError] = useState("");
  if (!available || !config) return <section className="panel"><h2>Dynamic pricing</h2><p>Deploy the official dynamic-fare migration before using these controls.</p></section>;
  const rainEnabled = config.rain_pricing_enabled;
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    try {
      const client = getSupabaseBrowserClient(); if (!client) throw new Error();
      const { error: rpcError } = await client.rpc("admin_update_official_pricing", {
        p_rate_per_km_zmw: Number(form.km), p_rate_per_minute_zmw: Number(form.minute),
        p_minimum_fare_zmw: Number(form.minimum), p_free_waiting_minutes: Number(form.freeWaiting),
        p_waiting_rate_zmw: Number(form.waiting), p_rider_share_percent: Number(form.rider),
        p_platform_share_percent: Number(form.platform),
      });
      if (rpcError) throw rpcError; setMessage("Pricing version created. Existing quotes and trips were not changed."); onChanged();
    } catch { setError("Pricing could not be updated. Check the values and administrator access."); }
    finally { setBusy(false); }
  }
  async function toggleRain() {
    setBusy(true); setError(""); setMessage("");
    try { const client = getSupabaseBrowserClient(); if (!client) throw new Error(); const { error: rpcError } = await client.rpc("admin_set_rain_pricing", { p_enabled: !rainEnabled }); if (rpcError) throw rpcError; setMessage(`Rain pricing ${rainEnabled ? "disabled" : "enabled"}.`); onChanged(); }
    catch { setError("Rain pricing could not be changed."); } finally { setBusy(false); }
  }
  return <div className="dashboard-stack">
    <section className="panel"><h2>Official pricing · version {config.pricing_config_version}</h2><p>Base fare {money(config.base_fare_zmw)}. Night 21:00–05:00 adds {money(config.night_surge_zmw)}; busy periods add {money(config.busy_surge_zmw)}; rain adds {money(config.rain_surge_zmw)}. Combined surge is capped at {money(config.maximum_surge_zmw)}.</p>
      <form onSubmit={save} className="form-grid">
        <label>Per kilometre<input type="number" min="0" step="0.01" value={form.km} onChange={(e) => setForm({ ...form, km: e.target.value })} /></label>
        <label>Per journey minute<input type="number" min="0" step="0.01" value={form.minute} onChange={(e) => setForm({ ...form, minute: e.target.value })} /></label>
        <label>Minimum fare<input type="number" min="21" step="0.01" value={form.minimum} onChange={(e) => setForm({ ...form, minimum: e.target.value })} /></label>
        <label>Free waiting minutes<input type="number" min="0" max="60" step="1" value={form.freeWaiting} onChange={(e) => setForm({ ...form, freeWaiting: e.target.value })} /></label>
        <label>Waiting per minute<input type="number" min="0" step="0.01" value={form.waiting} onChange={(e) => setForm({ ...form, waiting: e.target.value })} /></label>
        <label>Rider share %<input type="number" min="0.01" max="99.99" step="0.01" value={form.rider} onChange={(e) => setForm({ ...form, rider: e.target.value })} /></label>
        <label>Platform share %<input type="number" min="0.01" max="99.99" step="0.01" value={form.platform} onChange={(e) => setForm({ ...form, platform: e.target.value })} /></label>
        <button className="primary-action" disabled={busy}>{busy ? "Saving…" : "Create pricing version"}</button>
      </form>
      <button className="secondary-action" disabled={busy} onClick={() => void toggleRain()}>{config.rain_pricing_enabled ? "Disable rain pricing" : "Enable rain pricing"}</button>
      {message ? <p role="status">{message}</p> : null}{error ? <p className="form-error" role="alert">{error}</p> : null}
    </section>
    <section className="panel"><h2>Revenue</h2><div className="kpi-grid">
      <article className="kpi-card"><span>Passenger revenue</span><strong>{money(revenue?.total_passenger_revenue_zmw)}</strong></article>
      <article className="kpi-card"><span>Rider earnings</span><strong>{money(revenue?.total_rider_earnings_zmw)}</strong></article>
      <article className="kpi-card"><span>Platform commission</span><strong>{money(revenue?.total_platform_commission_zmw)}</strong></article>
      <article className="kpi-card"><span>Outstanding commission</span><strong>{money(revenue?.outstanding_commission_zmw)}</strong></article>
    </div><p>Daily {money(revenue?.daily_revenue_zmw)} · Weekly {money(revenue?.weekly_revenue_zmw)} · Monthly {money(revenue?.monthly_revenue_zmw)}</p></section>
  </div>;
}
