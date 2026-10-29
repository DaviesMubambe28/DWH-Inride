"use client";

import { useState, type FormEvent } from "react";
import { getSupabaseBrowserClient } from "@/lib/supabase";

type Candidate = { id: string; full_name: string; phone_number: string };

export function RiderProvisionForm({ candidates, onProvisioned }: { candidates: Candidate[]; onProvisioned: () => void }) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  async function provision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const client = getSupabaseBrowserClient();
    if (!client) return;
    setBusy(true); setMessage(""); setError("");
    try {
      const result = await client.rpc("admin_provision_pilot_rider", {
        p_user_id: values.get("account"), p_make: values.get("make"), p_model: values.get("model"),
        p_year: Number(values.get("year")), p_plate: values.get("plate"), p_color: values.get("color"),
      });
      if (result.error) throw result.error;
      form.reset();
      setMessage("Provisional rider created. Complete the in-person checks before activation.");
      onProvisioned();
    } catch {
      setError("Provisioning failed. Verify the identity, motorcycle details, and unique plate number. Finish any active passenger trip before trying again.");
    } finally { setBusy(false); }
  }
  return <section className="panel">
    <div className="panel-header"><div><h2>Provision a pilot rider</h2><p>Create and verify the Auth identity through your secure administrator console first. Provisioning here grants pending rider access; it does not activate the account.</p></div></div>
    <form onSubmit={provision} aria-busy={busy}>
      <fieldset className="form-grid" disabled={busy || candidates.length === 0}>
        <legend className="sr-only">Verified rider identity and motorcycle</legend>
        <label>Existing verified account<select name="account" required defaultValue=""><option value="" disabled>Select an account</option>{candidates.map((person) => <option key={person.id} value={person.id}>{person.full_name} · {person.phone_number}</option>)}</select></label>
        <label>Motorcycle make<input name="make" required maxLength={80} /></label>
        <label>Motorcycle model<input name="model" required maxLength={80} /></label>
        <label>Year<input name="year" type="number" required min={1950} max={2100} /></label>
        <label>Registration plate<input name="plate" required minLength={2} maxLength={32} /></label>
        <label>Colour<input name="color" required maxLength={40} /></label>
      </fieldset>
      {candidates.length === 0 ? <p>No eligible identities are loaded. Create the account through the secure administrator console, then refresh.</p> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {message ? <p role="status">{message}</p> : null}
      <button className="primary-action" disabled={busy || candidates.length === 0}>{busy ? "Provisioning…" : "Create provisional rider"}</button>
    </form>
  </section>;
}
