"use client";

import { useState } from "react";
import { getSupabaseBrowserClient } from "@/lib/supabase";

export type RiderApplication = {
  applicant_id: string;
  status: "pending" | "approved" | "rejected";
  motorcycle_make: string;
  motorcycle_model: string;
  motorcycle_year: number;
  motorcycle_plate: string;
  motorcycle_color: string;
  driving_licence_number: string;
  review_note: string | null;
  submitted_at: string;
};

type Applicant = { id: string; full_name: string; phone_number: string };

export function RiderApplicationReview({ applications, applicants, onReviewed }: {
  applications: RiderApplication[];
  applicants: Map<string, Applicant>;
  onReviewed: () => void;
}) {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function review(application: RiderApplication, decision: "approved" | "rejected") {
    const note = notes[application.applicant_id]?.trim() ?? "";
    if (decision === "rejected" && !note) {
      setError("Record a concise reason before rejecting an application.");
      return;
    }
    if (decision === "approved" && !window.confirm("Approve this application for provisional rider access? Final activation still requires document, safety, and physical-device checks.")) return;
    const client = getSupabaseBrowserClient();
    if (!client) return;
    setBusyId(application.applicant_id); setMessage(""); setError("");
    try {
      const result = await client.rpc("admin_review_rider_application", {
        p_applicant_id: application.applicant_id,
        p_decision: decision,
        p_review_note: note || null,
      });
      if (result.error) throw result.error;
      setMessage(decision === "approved"
        ? "Application approved for provisional access. Complete the required in-person activation checks."
        : "Application rejected and the review reason was recorded.");
      onReviewed();
    } catch {
      setError("The review could not be saved. Check the application, active-trip state, motorcycle registration, and your admin access, then retry.");
    } finally { setBusyId(null); }
  }

  const ordered = [...applications].sort((a, b) => Number(b.status === "pending") - Number(a.status === "pending") || b.submitted_at.localeCompare(a.submitted_at));
  return <section className="panel">
    <div className="panel-header"><div><h2>Self-registered rider applications</h2><p>Approval provisions a pending rider. It cannot activate trip access or replace the in-person checks.</p></div></div>
    {message ? <p role="status">{message}</p> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {ordered.length === 0 ? <p>No self-registered rider applications are waiting.</p> : <div className="application-list">{ordered.map((application) => {
      const person = applicants.get(application.applicant_id);
      return <article className="application-card" key={application.applicant_id}>
        <header><div><strong>{person?.full_name ?? "Applicant"}</strong><span>{person?.phone_number ?? application.applicant_id}</span></div><span className={`badge badge-${application.status === "approved" ? "green" : application.status === "rejected" ? "red" : "orange"}`}>{application.status}</span></header>
        <dl><div><dt>Motorcycle</dt><dd>{application.motorcycle_make} {application.motorcycle_model} · {application.motorcycle_year} · {application.motorcycle_color}</dd></div><div><dt>Registration</dt><dd>{application.motorcycle_plate}</dd></div><div><dt>Licence</dt><dd>{application.driving_licence_number}</dd></div></dl>
        {application.status === "pending" ? <><label>Review note<textarea maxLength={500} value={notes[application.applicant_id] ?? ""} onChange={(event) => setNotes((current) => ({ ...current, [application.applicant_id]: event.target.value }))} placeholder="Required when rejecting; optional when approving" /></label><div className="approval-actions"><button className="secondary-action" disabled={busyId !== null} onClick={() => void review(application, "rejected")}>Reject</button><button className="primary-action" disabled={busyId !== null} onClick={() => void review(application, "approved")}>{busyId === application.applicant_id ? "Saving…" : "Approve provisionally"}</button></div></> : <p>{application.review_note || "No review note recorded."}</p>}
      </article>;
    })}</div>}
  </section>;
}
