"use client";

import { useState } from "react";
import { AlertTriangle, CircleDollarSign } from "lucide-react";
import { getSupabaseBrowserClient } from "@/lib/supabase";

export type ServiceFeeRider = {
  rider_id: string; full_name: string; phone_number: string; total_trips: number;
  fee_trip_count: number; total_fees_zmw: number; total_paid_zmw: number;
  outstanding_zmw: number; pending_payments: number; last_payment_at: string | null;
};
export type ServiceFeePayment = {
  id: string; rider_id: string; amount_zmw: number; transaction_reference: string;
  proof_storage_path: string | null; status: "pending_verification" | "verified" | "rejected";
  submitted_at: string; reviewed_at: string | null; admin_note: string | null;
};
export type ServiceFeeConfig = {
  payment_method: string | null; payment_recipient: string | null;
  payment_instructions: string | null; updated_at: string;
};

export function ServiceFeeManagement({ riders, payments, config, available, onChanged }: {
  riders: ServiceFeeRider[]; payments: ServiceFeePayment[]; config: ServiceFeeConfig | null;
  available: boolean; onChanged: () => void;
}) {
  const [method, setMethod] = useState(config?.payment_method ?? "");
  const [recipient, setRecipient] = useState(config?.payment_recipient ?? "");
  const [instructions, setInstructions] = useState(config?.payment_instructions ?? "");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  if (!available) return <section className="panel"><h2>Service-fee management is pending deployment</h2><p>The reviewed ledger migration has not been applied to this environment. No balance can be cleared from this dashboard until the backend is deployed and verified.</p></section>;

  async function saveConfig() {
    const client = getSupabaseBrowserClient(); if (!client) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const { error: requestError } = await client.rpc("admin_update_service_fee_payment_config", {
        p_payment_method: method, p_payment_recipient: recipient, p_payment_instructions: instructions,
      });
      if (requestError) throw requestError;
      setMessage("Official payment instructions saved."); onChanged();
    } catch { setError("Payment instructions could not be saved. Check the values and retry."); }
    finally { setBusy(false); }
  }

  const pending = payments.filter((payment) => payment.status === "pending_verification");
  const totalOutstanding = riders.reduce((total, rider) => total + Number(rider.outstanding_zmw), 0);
  return <div className="dashboard-stack">
    {error ? <div className="error-banner" role="alert"><AlertTriangle size={18} />{error}</div> : null}
    {message ? <p className="panel" role="status">{message}</p> : null}
    <section className="kpi-grid finance-kpis">
      <article className="kpi-card"><span>Outstanding fees</span><strong>{money(totalOutstanding)}</strong><small>{riders.filter((rider) => Number(rider.outstanding_zmw) > 0).length} riders owe fees</small></article>
      <article className="kpi-card"><span>Pending verification</span><strong>{pending.length}</strong><small>Never clears balance automatically</small></article>
      <article className="kpi-card"><span>Verified receipts</span><strong>{money(riders.reduce((total, rider) => total + Number(rider.total_paid_zmw), 0))}</strong><small>Immutable ledger credits</small></article>
    </section>
    <section className="panel"><div className="panel-header"><div><h2>Official payment destination</h2><p>Riders see these values exactly. Verify ownership before saving.</p></div></div>
      <div className="incident-form">
        <label>Payment method<input value={method} maxLength={80} onChange={(event) => setMethod(event.target.value)} /></label>
        <label>Official number or recipient<input value={recipient} maxLength={120} onChange={(event) => setRecipient(event.target.value)} /></label>
        <label className="incident-summary-field">Instructions<textarea value={instructions} minLength={10} maxLength={1000} onChange={(event) => setInstructions(event.target.value)} /></label>
        <button className="primary-action" disabled={busy || method.trim().length < 2 || recipient.trim().length < 4 || instructions.trim().length < 10} onClick={() => void saveConfig()}>{busy ? "Saving…" : "Save official instructions"}</button>
      </div>
    </section>
    <section className="panel"><div className="panel-header"><div><h2>Pending payment submissions</h2><p>Confirm receipt in the official provider account before verification.</p></div></div>
      {pending.length === 0 ? <p role="status">No payments await verification.</p> : <div className="incident-list">{pending.map((payment) => <PaymentReview key={payment.id} payment={payment} rider={riders.find((rider) => rider.rider_id === payment.rider_id)} onChanged={onChanged} />)}</div>}
    </section>
    <section className="panel"><div className="panel-header"><div><h2>Rider balances</h2><p>Charges come from completed trips; only verified submissions become credits.</p></div></div>
      <div className="desktop-table"><table><thead><tr><th>Rider</th><th>Fee trips</th><th>Generated</th><th>Verified</th><th>Outstanding</th><th>Pending</th><th>Last payment</th></tr></thead><tbody>{riders.map((rider) => <tr key={rider.rider_id}><td><strong>{rider.full_name}</strong><small className="next-step">{rider.phone_number}</small></td><td>{rider.fee_trip_count}</td><td>{money(rider.total_fees_zmw)}</td><td>{money(rider.total_paid_zmw)}</td><td><strong>{Number(rider.outstanding_zmw) > 0 ? `-${money(rider.outstanding_zmw)}` : "K0.00"}</strong></td><td>{rider.pending_payments}</td><td>{rider.last_payment_at ? new Date(rider.last_payment_at).toLocaleDateString() : "—"}</td></tr>)}</tbody></table></div>
      <div className="mobile-card-list">{riders.map((rider) => <article className="mobile-trip" key={rider.rider_id}><div><strong>{rider.full_name}</strong><span>{Number(rider.outstanding_zmw) > 0 ? `-${money(rider.outstanding_zmw)}` : "K0.00"}</span></div><p>{rider.phone_number}</p><small>{rider.fee_trip_count} fee trips · {rider.pending_payments} pending</small></article>)}</div>
    </section>
    <section className="panel"><div className="panel-header"><div><h2>Payment history</h2><p>Submitted references and final review decisions.</p></div></div>{payments.length === 0 ? <p>No payment history.</p> : payments.map((payment) => <div className="health-row" key={payment.id}><span><strong>{payment.transaction_reference} · {money(payment.amount_zmw)}</strong><b>{label(payment.status)}</b></span><small>{new Date(payment.submitted_at).toLocaleString()}{payment.admin_note ? ` · ${payment.admin_note}` : ""}</small></div>)}</section>
  </div>;
}

function PaymentReview({ payment, rider, onChanged }: { payment: ServiceFeePayment; rider?: ServiceFeeRider; onChanged: () => void }) {
  const [note, setNote] = useState(""); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  async function review(decision: "verified" | "rejected") {
    if (decision === "verified" && !window.confirm(`Confirm ${money(payment.amount_zmw)} was actually received? Only this amount will reduce the balance.`)) return;
    const client = getSupabaseBrowserClient(); if (!client) return;
    setBusy(true); setError("");
    try {
      const { error: requestError } = await client.rpc("admin_review_service_fee_payment", { p_payment_id: payment.id, p_decision: decision, p_admin_note: note });
      if (requestError) throw requestError; onChanged();
    } catch { setError("Review was not saved. Refresh the current balance and retry."); }
    finally { setBusy(false); }
  }
  return <article className="incident-record"><div><CircleDollarSign className="severity-dot severity-medium" aria-hidden="true" /><div><strong>{rider?.full_name ?? payment.rider_id.slice(0, 8)} · {money(payment.amount_zmw)}</strong><span>{payment.transaction_reference}</span><small>Outstanding before review: {rider && Number(rider.outstanding_zmw) > 0 ? `-${money(rider.outstanding_zmw)}` : "K0.00"}</small></div></div>{error ? <p className="form-error" role="alert">{error}</p> : null}<div className="incident-controls"><label className="incident-summary-field">Required admin note<textarea minLength={3} maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} /></label><button className="secondary-action" disabled={busy || note.trim().length < 3} onClick={() => void review("rejected")}>Reject payment</button><button className="primary-action" disabled={busy || note.trim().length < 3} onClick={() => void review("verified")}>Verify payment & apply {money(payment.amount_zmw)}</button></div></article>;
}

function money(value: number) { return `K${Number(value).toFixed(2)}`; }
function label(value: string) { return value.replaceAll("_", " ").replace(/^./, (character) => character.toUpperCase()); }
