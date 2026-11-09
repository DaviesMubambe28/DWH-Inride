"use client";

import { FormEvent, useState } from "react";
import { AlertTriangle, ShieldAlert } from "lucide-react";
import { getSupabaseBrowserClient } from "@/lib/supabase";

export type Incident = {
  id: string;
  trip_id: string | null;
  reporter_id: string | null;
  recorded_by: string;
  assigned_admin_id: string | null;
  category: "safety" | "service" | "conduct" | "payment" | "other";
  severity: "low" | "medium" | "high" | "critical";
  summary: string;
  status: "open" | "in_review" | "resolved";
  resolution_note: string | null;
  created_at: string;
  updated_at: string;
  resolved_at: string | null;
};

type Option = { id: string; label: string };

export function IncidentManagement({
  incidents,
  trips,
  people,
  admins,
  available,
  onChanged,
}: {
  incidents: Incident[];
  trips: Option[];
  people: Option[];
  admins: Option[];
  available: boolean;
  onChanged: () => void;
}) {
  const [tripId, setTripId] = useState("");
  const [reporterId, setReporterId] = useState("");
  const [category, setCategory] = useState<Incident["category"]>("safety");
  const [severity, setSeverity] = useState<Incident["severity"]>("medium");
  const [summary, setSummary] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  if (!available) {
    return <section className="panel"><h2>Incident tracking is pending deployment</h2><p>The reviewed incident migration has not been applied to this environment. Continue using the monitored support channels and incident runbook until it is deployed and verified.</p><SupportContacts /></section>;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const client = getSupabaseBrowserClient();
    if (!client) return;
    setBusy(true); setMessage(""); setError("");
    try {
      const { error: requestError } = await client.rpc("admin_record_incident", {
        p_trip_id: tripId || null,
        p_reporter_id: reporterId || null,
        p_category: category,
        p_severity: severity,
        p_summary: summary,
      });
      if (requestError) throw requestError;
      setTripId(""); setReporterId(""); setCategory("safety"); setSeverity("medium"); setSummary("");
      setMessage("Incident recorded. Continue the incident runbook and assign an operator.");
      onChanged();
    } catch {
      setError("The incident could not be recorded. Use the monitored support channel and retry.");
    } finally { setBusy(false); }
  }

  return <div className="dashboard-stack">
    <section className="panel">
      <div className="panel-header"><div><h2>Record an incident</h2><p>Use a concise operational summary. Do not place passwords, PINs or identity-document numbers here.</p></div></div>
      {message ? <p role="status">{message}</p> : null}
      {error ? <div className="error-banner" role="alert"><AlertTriangle size={18} />{error}</div> : null}
      <form className="incident-form" onSubmit={(event) => void submit(event)}>
        <label>Trip (optional)<select value={tripId} onChange={(event) => setTripId(event.target.value)}><option value="">No linked trip</option>{trips.map((trip) => <option key={trip.id} value={trip.id}>{trip.label}</option>)}</select></label>
        <label>Reporter (optional)<select value={reporterId} onChange={(event) => setReporterId(event.target.value)}><option value="">Not linked to an account</option>{people.map((person) => <option key={person.id} value={person.id}>{person.label}</option>)}</select></label>
        <label>Category<select value={category} onChange={(event) => setCategory(event.target.value as Incident["category"])}>{["safety", "service", "conduct", "payment", "other"].map((value) => <option key={value} value={value}>{label(value)}</option>)}</select></label>
        <label>Severity<select value={severity} onChange={(event) => setSeverity(event.target.value as Incident["severity"])}>{["low", "medium", "high", "critical"].map((value) => <option key={value} value={value}>{label(value)}</option>)}</select></label>
        <label className="incident-summary-field">Summary<textarea required minLength={10} maxLength={2000} value={summary} onChange={(event) => setSummary(event.target.value)} /></label>
        <button className="primary-action" disabled={busy || summary.trim().length < 10}>{busy ? "Recording…" : "Record incident"}</button>
      </form>
    </section>
    <section className="panel"><div className="panel-header"><div><h2>Incident register</h2><p>Newest records first. Resolution requires an accountable note.</p></div></div>{incidents.length === 0 ? <p role="status">No incidents are recorded.</p> : <div className="incident-list">{incidents.map((incident) => <IncidentRow key={incident.id} incident={incident} admins={admins} onChanged={onChanged} />)}</div>}</section>
    <SupportContacts />
  </div>;
}

function IncidentRow({ incident, admins, onChanged }: { incident: Incident; admins: Option[]; onChanged: () => void }) {
  const [status, setStatus] = useState<Incident["status"]>(incident.status);
  const [severity, setSeverity] = useState<Incident["severity"]>(incident.severity);
  const [assignedAdminId, setAssignedAdminId] = useState(incident.assigned_admin_id ?? "");
  const [resolution, setResolution] = useState(incident.resolution_note ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save() {
    const client = getSupabaseBrowserClient(); if (!client) return;
    setBusy(true); setError("");
    try {
      const { error: requestError } = await client.rpc("admin_update_incident", {
        p_incident_id: incident.id,
        p_status: status,
        p_severity: severity,
        p_assigned_admin_id: assignedAdminId || null,
        p_resolution_note: status === "resolved" ? resolution : null,
      });
      if (requestError) throw requestError;
      onChanged();
    } catch { setError("The incident update was not saved. Retry or follow the support escalation path."); }
    finally { setBusy(false); }
  }
  return <article className="incident-record"><div><span className={`severity-dot severity-${severity}`} aria-hidden="true" /><div><span className="incident-id">{incident.id.slice(0, 8)}</span><strong>{incident.summary}</strong><small>{label(incident.category)} · {new Date(incident.created_at).toLocaleString()}</small></div></div>{error ? <p className="form-error" role="alert">{error}</p> : null}<div className="incident-controls"><label>Status<select value={status} onChange={(event) => setStatus(event.target.value as Incident["status"])}><option value="open">Open</option><option value="in_review">In review</option><option value="resolved">Resolved</option></select></label><label>Severity<select value={severity} onChange={(event) => setSeverity(event.target.value as Incident["severity"])}>{["low", "medium", "high", "critical"].map((value) => <option key={value} value={value}>{label(value)}</option>)}</select></label><label>Assigned operator<select value={assignedAdminId} onChange={(event) => setAssignedAdminId(event.target.value)}><option value="">Unassigned</option>{admins.map((admin) => <option key={admin.id} value={admin.id}>{admin.label}</option>)}</select></label>{status === "resolved" ? <label className="incident-summary-field">Resolution note<textarea required minLength={10} maxLength={2000} value={resolution} onChange={(event) => setResolution(event.target.value)} /></label> : null}<button className="secondary-action" disabled={busy || (status === "resolved" && resolution.trim().length < 10)} onClick={() => void save()}>{busy ? "Saving…" : "Save incident"}</button></div></article>;
}

function SupportContacts() {
  return <p className="support-actions"><ShieldAlert size={18} aria-hidden="true" /> Escalation: <a href="tel:+260770001203">0770 001 203</a> · <a href="tel:+260975461121">0975 461 121</a> · <a href="mailto:gershmukuka1@gmail.com">Email support</a></p>;
}

function label(value: string) { return value.replaceAll("_", " ").replace(/^./, (character) => character.toUpperCase()); }
