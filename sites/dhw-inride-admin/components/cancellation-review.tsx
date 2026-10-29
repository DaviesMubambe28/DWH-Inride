"use client";
import { useState } from "react";
import { getSupabaseBrowserClient } from "@/lib/supabase";

export type CancellationReview = {
  trip_id: string; actor_id: string; reason: string; late_warning: boolean;
  review_required: boolean; created_at: string; reviewed_at: string | null;
};

export function CancellationReviews({ records, onReviewed }: { records: CancellationReview[]; onReviewed: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const pending = records.filter((record) => record.review_required && !record.reviewed_at);
  async function acknowledge(id: string) {
    const client = getSupabaseBrowserClient();
    if (!client) return;
    setBusy(id); setError("");
    try {
      const result = await client.rpc("admin_review_pilot_cancellation", { p_trip_id: id });
      if (result.error || result.data !== true) throw result.error;
      onReviewed();
    } catch { setError("Review could not be confirmed. Refresh the records and try again."); }
    finally { setBusy(null); }
  }
  return <section className="panel"><div className="panel-header"><div><h2>Cancellation review</h2><p>Warnings and rider cancellations for manual follow-up. No fee or next-ride surcharge is collected.</p></div></div>
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    {pending.length === 0 ? <p role="status">No cancellation warnings awaiting review.</p> : <div className="dashboard-stack">{pending.map((record) => <article className="mobile-trip" key={record.trip_id}>
      <strong>Trip {record.trip_id.slice(0, 8)}</strong><p>{record.reason.replaceAll("_", " ")}</p>
      {record.late_warning ? <p>Passenger cancelled more than two minutes after acceptance. Warning only; no debt or charge.</p> : null}
      <p>Follow up with the participants through the monitored support channels before acknowledging.</p>
      <button className="secondary-action" disabled={busy !== null} onClick={() => void acknowledge(record.trip_id)}>{busy === record.trip_id ? "Saving review…" : "Record manual review"}</button>
    </article>)}</div>}
  </section>;
}
