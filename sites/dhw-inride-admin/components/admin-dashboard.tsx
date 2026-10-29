"use client";

import {
  Activity, AlertTriangle, BarChart3, Bike, CircleDollarSign,
  ClipboardCheck, Clock3, LayoutDashboard, LogOut, Map as MapIcon, Menu, RefreshCw,
  Search, ShieldAlert, UserCog, Users, X,
} from "lucide-react";
import type { User } from "@supabase/supabase-js";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CancellationReviews, type CancellationReview } from "@/components/cancellation-review";
import { RiderProvisionForm } from "@/components/rider-provision-form";
import { matchesTripFilter, tripPresentation } from "@/lib/trip-presentation";
import { getSupabaseBrowserClient, getSupabaseConfig } from "@/lib/supabase";

type Section = "overview" | "verification" | "live" | "finance" | "incidents" | "analytics" | "roles";
type Tone = "green" | "orange" | "blue" | "red" | "slate";
type ApprovalStatus = "pending" | "approved" | "rejected";
type Profile = { id: string; role: "passenger" | "rider" | "admin"; full_name: string; phone_number: string; is_online: boolean; rating: number; total_trips: number; approval_status: ApprovalStatus | null };
type Trip = { id: string; passenger_id: string; rider_id: string | null; status: string; pickup_address: string; dropoff_address: string; fare_amount: number | null; payment_method: string; payment_status: string; created_at: string; completed_at: string | null };
type RiderDocument = { id: string; rider_id: string; document_type: string; storage_url: string; verification_status: string; submitted_at: string };
type TripLocation = { id: number; trip_id: string; rider_id: string; lat: number; lng: number; recorded_at: string };
type DashboardData = { profiles: Profile[]; trips: Trip[]; documents: RiderDocument[]; locations: TripLocation[]; cancellations: CancellationReview[] };

const emptyData: DashboardData = { profiles: [], trips: [], documents: [], locations: [], cancellations: [] };
const navItems = [
  { id: "overview" as Section, label: "Overview", icon: LayoutDashboard },
  { id: "verification" as Section, label: "Rider verification", icon: ClipboardCheck },
  { id: "live" as Section, label: "Live operations", icon: MapIcon },
  { id: "finance" as Section, label: "Finance", icon: CircleDollarSign },
  { id: "incidents" as Section, label: "Incidents", icon: ShieldAlert },
  { id: "analytics" as Section, label: "Reports", icon: BarChart3 },
  { id: "roles" as Section, label: "Roles & access", icon: UserCog },
];
const sectionCopy: Record<Section, { eyebrow: string; title: string; description: string }> = {
  overview: { eyebrow: "Operations", title: "Overview", description: "Current activity and requests that need your attention." },
  verification: { eyebrow: "Trust & safety", title: "Rider verification", description: "Review real rider profiles and submitted documents." },
  live: { eyebrow: "Dispatch control", title: "Live operations", description: "Monitor active trips and current rider availability." },
  finance: { eyebrow: "Finance operations", title: "Trip revenue", description: "Completed fares and participant payment acknowledgements." },
  incidents: { eyebrow: "Safety desk", title: "Complaints & incidents", description: "This workflow requires the incidents schema before it can be enabled." },
  analytics: { eyebrow: "Performance", title: "Reports", description: "Operational totals calculated from real trips." },
  roles: { eyebrow: "Access control", title: "Roles & access", description: "Current application roles stored in profiles." },
};

function Badge({ children, tone = "slate" }: { children: React.ReactNode; tone?: Tone }) { return <span className={`badge badge-${tone}`}>{children}</span>; }
function PanelHeader({ title, subtitle }: { title: string; subtitle?: string }) { return <div className="panel-header"><div><h2>{title}</h2>{subtitle ? <p>{subtitle}</p> : null}</div></div>; }
function EmptyState({ title, detail }: { title: string; detail: string }) { return <div className="empty-state" role="status"><Search size={22} /><strong>{title}</strong><span>{detail}</span></div>; }

function LoadingScreen({ title, detail }: { title: string; detail: string }) {
  return <main className="setup-page"><section className="panel setup-card" role="status" aria-busy="true"><RefreshCw className="spin" aria-hidden="true" /><h1>{title}</h1><p>{detail}</p><div className="loading-lines" aria-hidden="true"><span /><span /><span /></div></section></main>;
}

function SetupRequired() {
  return <main className="setup-page"><section className="panel setup-card"><span className="brand-mark"><Bike size={24} /></span><h1>Connect Supabase</h1><p>The dashboard does not display sample records. Add these values to <code>sites/dhw-inride-admin/.env.local</code> and restart it:</p><pre>NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co{"\n"}NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=your-publishable-key</pre><p>Use a publishable key only. Never put a secret or service-role key in a <code>NEXT_PUBLIC_</code> variable.</p></section></main>;
}

function SignIn({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const [email, setEmail] = useState(""); const [password, setPassword] = useState("");
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try {
      const client = getSupabaseBrowserClient();
      if (!client) { setError("Sign in is unavailable. Refresh the page and try again."); return; }
      const result = await client.auth.signInWithPassword({ email: email.trim(), password });
      if (result.error) setError("Could not sign in. Check your email, password, and connection, then try again.");
      else if (result.data.user) onSignedIn(result.data.user);
    } catch {
      setError("Sign in could not reach the server. Check your connection and try again.");
    } finally { setBusy(false); }
  }
  return <main className="setup-page"><form className="panel setup-card" aria-busy={busy} onSubmit={submit}><span className="brand-mark"><Bike size={24} /></span><h1>Operations sign in</h1><p>Use an account whose profile role is <strong>admin</strong>.</p><label>Email<input type="email" autoComplete="email" disabled={busy} value={email} onChange={(e) => setEmail(e.target.value)} required /></label><label>Password<input type="password" autoComplete="current-password" disabled={busy} value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required /></label>{error ? <p className="form-error" role="alert">{error}</p> : null}<button className="primary-action full" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button></form></main>;
}

function tripTone(status: string): Tone { return tripPresentation(status).tone; }
function formatMoney(value: number | null) { return value == null ? "Pending" : `K${Number(value).toFixed(2)}`; }
function elapsed(iso: string) { const minutes = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000)); return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`; }

export function AdminDashboard() {
  const { configured } = getSupabaseConfig();
  const [user, setUser] = useState<User | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);
  useEffect(() => {
    if (!configured) return;
    const client = getSupabaseBrowserClient()!;
    let active = true;
    client.auth.getUser().then(({ data }) => {
      if (active) { setUser(data.user ?? null); setCheckingSession(false); }
    }).catch(() => { if (active) { setUser(null); setCheckingSession(false); } });
    const { data: listener } = client.auth.onAuthStateChange((_event, session) => {
      if (active) setUser(session?.user ?? null);
    });
    return () => { active = false; listener.subscription.unsubscribe(); };
  }, [configured]);
  if (!configured) return <SetupRequired />;
  if (checkingSession) return <LoadingScreen title="Checking your session" detail="Confirming access to operations tools." />;
  if (!user) return <SignIn onSignedIn={setUser} />;
  return <OperationsDashboard key={user.id} user={user} />;
}

function OperationsDashboard({ user }: { user: User }) {
  const [section, setSection] = useState<Section>("overview"); const [drawerOpen, setDrawerOpen] = useState(false);
  const [search, setSearch] = useState(""); const [data, setData] = useState<DashboardData>(emptyData);
  const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const [authorized, setAuthorized] = useState<boolean | null>(null);
  const [filter, setFilter] = useState("active");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [online, setOnline] = useState(true);
  const [pauseBusy, setPauseBusy] = useState(false);
  const [pauseMessage, setPauseMessage] = useState("");
  const menuRef = useRef<HTMLDialogElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const loadVersion = useRef(0);

  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    update();
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => { window.removeEventListener("online", update); window.removeEventListener("offline", update); };
  }, []);
  useEffect(() => {
    if (drawerOpen) menuRef.current?.showModal();
    else menuRef.current?.close();
  }, [drawerOpen]);
  async function pausePilot() {
    if (!window.confirm("Pause all new pilot bookings? Existing active trips can still finish.")) return;
    const client = getSupabaseBrowserClient();
    if (!client) return;
    setPauseBusy(true); setPauseMessage("");
    try {
      const { error } = await client.rpc("admin_pause_pilot");
      if (error) throw error;
      setPauseMessage("New pilot bookings are paused. Continue supporting active trips.");
    } catch { setPauseMessage("Pause could not be confirmed. Contact the responsible operator immediately and retry."); }
    finally { setPauseBusy(false); }
  }
  function navigate(next: Section) {
    setSection(next); setDrawerOpen(false);
    requestAnimationFrame(() => titleRef.current?.focus());
  }


  const load = useCallback(() => {
    const client = getSupabaseBrowserClient(); if (!client || !user) return;
    const version = ++loadVersion.current;
    return Promise.all([
        client.from("profiles").select("id,role,full_name,phone_number,is_online,rating,total_trips,approval_status").order("created_at", { ascending: false }),
        client.from("authorized_trip_details").select("id,passenger_id,rider_id,status,pickup_address,dropoff_address,fare_amount,payment_method,payment_status,created_at,completed_at").order("created_at", { ascending: false }).limit(500),
        client.from("rider_documents").select("id,rider_id,document_type,storage_url,verification_status,submitted_at").order("submitted_at", { ascending: false }),
        client.from("trip_locations").select("id,trip_id,rider_id,lat,lng,recorded_at").order("recorded_at", { ascending: false }).limit(500),
        client.from("pilot_cancellation_reviews").select("trip_id,actor_id,reason,late_warning,review_required,created_at,reviewed_at").order("created_at", { ascending: false }).limit(200),
      ]).then(([profileResult, tripsResult, docsResult, locationsResult, cancellationsResult]) => {
      if (version !== loadVersion.current) return;
      if (profileResult.error) throw profileResult.error;
      const ownProfile = profileResult.data?.find((profile) => profile.id === user.id);
      if (ownProfile?.role !== "admin") { setAuthorized(false); setData(emptyData); setLastUpdated(null); return; }
      setAuthorized(true);
      const firstError = tripsResult.error ?? docsResult.error ?? locationsResult.error ?? cancellationsResult.error;
      if (firstError) throw firstError;
      setData({ profiles: (profileResult.data ?? []) as Profile[], trips: (tripsResult.data ?? []) as Trip[], documents: (docsResult.data ?? []) as RiderDocument[], locations: (locationsResult.data ?? []) as TripLocation[], cancellations: (cancellationsResult.data ?? []) as CancellationReview[] });
      setLastUpdated(new Date()); setError("");
    }).catch(() => {
      if (version === loadVersion.current) setError("We couldn’t refresh operations data. Check your connection and try again.");
    }).finally(() => {
      if (version === loadVersion.current) setLoading(false);
    });
  }, [user]);

  const refresh = useCallback(() => { setLoading(true); setError(""); void load(); }, [load]);
  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    const client = getSupabaseBrowserClient(); if (!client || !user || authorized !== true) return;
    const channel = client.channel("admin-dashboard").on("postgres_changes", { event: "*", schema: "public" }, () => refresh()).subscribe();
    return () => { void client.removeChannel(channel); };
  }, [user, authorized, refresh]);

  const profilesById = useMemo(() => new Map(data.profiles.map((profile) => [profile.id, profile])), [data.profiles]);
  const activeTrips = data.trips.filter((trip) => !["completed", "cancelled"].includes(trip.status));
  const riders = data.profiles.filter((profile) => profile.role === "rider");
  const query = search.trim().toLowerCase();
  const searchedTrips = query ? data.trips.filter((trip) => [trip.id, trip.pickup_address, trip.dropoff_address, profilesById.get(trip.passenger_id)?.full_name, trip.rider_id ? profilesById.get(trip.rider_id)?.full_name : ""].join(" ").toLowerCase().includes(query)) : data.trips;
  const shownTrips = searchedTrips.filter((trip) => matchesTripFilter(trip.status, filter));
  const copy = sectionCopy[section];

  if (lastUpdated === null && !error && authorized !== false) return <LoadingScreen title="Loading operations" detail="Fetching trips, rider records, and verification requests." />;
  if (lastUpdated === null && error) return <main className="setup-page"><section className="panel setup-card"><ShieldAlert size={32} /><h1>Operations data unavailable</h1><p role="alert">{error}</p><button className="primary-action full" onClick={() => refresh()}>Retry</button></section></main>;
  if (authorized === false) return <main className="setup-page"><section className="panel setup-card"><ShieldAlert size={32} /><h1>Admin access required</h1><p>This signed-in account is not authorized to use operations tools.</p><button className="secondary-action full" onClick={() => void getSupabaseBrowserClient()?.auth.signOut()}>Sign out</button></section></main>;

  const navigation = <nav className="side-nav" aria-label="Operations sections">{navItems.map((item) => {
    const Icon = item.icon;
    return <button key={item.id} aria-current={section === item.id ? "page" : undefined} className={section === item.id ? "nav-item active" : "nav-item"} onClick={() => navigate(item.id)}><Icon size={18} aria-hidden="true" /><span>{item.label}</span>{item.id === "verification" && riders.some((r) => r.approval_status === "pending") ? <em aria-label="Pending rider applications">{riders.filter((r) => r.approval_status === "pending").length}</em> : null}</button>;
  })}</nav>;
  return <div className="admin-shell">
    <a className="skip-link" href="#operations-content">Skip to operations</a>
    <aside className="sidebar" aria-label="Main navigation">
      <div className="brand-row"><span className="brand-mark"><Bike size={22} /></span><span className="brand-name">DHW<span>-inRide</span></span></div>
      <div className="workspace-chip"><span className="workspace-avatar">CO</span><span><small>Workspace</small><strong>Chongwe Operations</strong></span></div>
      {navigation}
      <div className="sidebar-footer"><button className="nav-item" onClick={() => void getSupabaseBrowserClient()?.auth.signOut()}><LogOut size={18} /><span>Sign out</span></button><div className="operator"><span className="operator-avatar">{user.email?.slice(0, 2).toUpperCase()}</span><span><strong>{user.email}</strong><small>Authenticated admin</small></span></div></div>
    </aside>
    <dialog ref={menuRef} className="navigation-dialog" onClose={() => setDrawerOpen(false)} aria-label="Operations navigation">
      <div className="panel-header"><strong>DHW-inRide</strong><button className="icon-button" autoFocus onClick={() => setDrawerOpen(false)} aria-label="Close navigation"><X /></button></div>
      {navigation}
    </dialog>
    <main className="main-panel" id="operations-content" tabIndex={-1}>
      <header className="topbar">
        <button className="icon-button menu-button" onClick={() => setDrawerOpen(true)} aria-label="Open navigation" aria-haspopup="dialog" aria-expanded={drawerOpen}><Menu size={21} /></button>
        <strong className="workspace-title">Chongwe operations</strong>
        <span className="system-live" role="status">{!online ? "Offline · updates paused" : loading ? "Refreshing…" : lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}` : "Waiting for data"}</span>
        <button className="secondary-action" disabled={pauseBusy || !online} onClick={() => void pausePilot()}>{pauseBusy ? "Pausing…" : "Pause new bookings"}</button>
        <button className="secondary-action refresh-action" disabled={loading || !online} onClick={() => refresh()}><RefreshCw aria-hidden="true" className={loading ? "spin" : ""} size={18} /><span>{loading ? "Refreshing…" : "Refresh"}</span></button>
      </header>
      <div className="page-content" aria-busy={loading}>
        <section className="page-heading"><div><span className="eyebrow">{copy.eyebrow}</span><h1 ref={titleRef} tabIndex={-1}>{copy.title}</h1><p>{copy.description}</p></div></section>
        {!online ? <div className="error-banner" role="status"><AlertTriangle size={18} />You’re offline. Showing the last loaded records. Reconnect, then refresh.</div> : null}
        {error ? <div className="error-banner" role="alert"><AlertTriangle size={18} /><span>{error} Previously loaded records are shown below.</span><button disabled={loading || !online} onClick={() => refresh()}>Retry</button></div> : null}
        {pauseMessage ? <p className="panel" role="status">{pauseMessage}</p> : null}
        <p className="data-scope">Latest {data.trips.length} trips loaded (maximum 500). Totals describe this loaded set.</p>
        {section === "overview" ? <Overview trips={data.trips} riders={riders} activeTrips={activeTrips} profiles={profilesById} onDispatch={() => { setFilter("requesting"); navigate("live"); }} onVerification={() => navigate("verification")} /> : null}
        {section === "live" ? <div className="dashboard-stack"><section className="panel trip-controls" aria-label="Filter trips"><label>Search trips<input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Trip ID, person, or place" /></label><label>Trip status<select value={filter} onChange={(event) => setFilter(event.target.value)}><option value="active">Active trips</option><option value="all">All trips</option>{["requesting", "accepted", "arrived", "in_progress", "completed", "cancelled"].map((status) => <option key={status} value={status}>{tripPresentation(status).label}</option>)}</select></label><button className="secondary-action" onClick={() => { setSearch(""); setFilter("all"); }}>Clear filters</button><span role="status">{shownTrips.length} matching trips</span></section><TripsPanel trips={shownTrips} profiles={profilesById} title="Trip control" /><CancellationReviews records={data.cancellations} onReviewed={refresh} /></div> : null}
        {section === "verification" ? <div className="dashboard-stack"><RiderProvisionForm candidates={data.profiles.filter((p) => p.role === "passenger")} onProvisioned={refresh} /><Verification riders={riders} documents={data.documents} /></div> : null}
        {section === "finance" ? <Finance trips={data.trips} profiles={profilesById} /> : null}
        {section === "incidents" ? <Unsupported title="Incident tracking is unavailable" detail="Incident records are not connected yet. Follow your established support procedure for passenger or rider concerns." /> : null}
        {section === "analytics" ? <Analytics trips={data.trips} /> : null}
        {section === "roles" ? <Roles profiles={data.profiles} /> : null}
      </div>
    </main>
  </div>;
}

function Overview({ trips, riders, activeTrips, profiles, onDispatch, onVerification }: { trips: Trip[]; riders: Profile[]; activeTrips: Trip[]; profiles: Map<string, Profile>; onDispatch: () => void; onVerification: () => void }) {
  const today = new Date().toDateString(); const tripsToday = trips.filter((trip) => new Date(trip.created_at).toDateString() === today).length;
  const cards = [["Trips today", tripsToday, "blue"], ["Riders online", riders.filter((r) => r.is_online).length, "green"], ["Active trips", activeTrips.length, "orange"], ["Awaiting dispatch", activeTrips.filter((t) => t.status === "requesting").length, "red"]] as const;
  return <div className="dashboard-stack"><section className="panel attention-summary"><div><h2>Needs attention</h2><p>Review waiting requests and pending rider applications.</p></div><button className="secondary-action" onClick={onDispatch}>Awaiting rider · {activeTrips.filter((t) => t.status === "requesting").length}</button><button className="secondary-action" onClick={onVerification}>Pending verification · {riders.filter((r) => r.approval_status === "pending").length}</button></section><section className="kpi-grid">{cards.map(([label, value, tone], index) => <article className="kpi-card" key={label}><div className={`kpi-icon tone-${tone}`}>{[<Activity key="a" />, <Bike key="b" />, <Clock3 key="c" />, <AlertTriangle key="d" />][index]}</div><span>{label}</span><strong>{value}</strong><small>Loaded records</small></article>)}</section><TripsPanel trips={activeTrips.slice(0, 10)} profiles={profiles} title="Active trips" /></div>;
}

function TripsPanel({ trips, profiles, title }: { trips: Trip[]; profiles: Map<string, Profile>; title: string }) {
  return <section className="panel"><PanelHeader title={title} subtitle="Newest records first · status includes the next step" />{trips.length === 0 ? <EmptyState title="No trips found" detail="Try a different status or clear your search. New bookings appear after the next update." /> : <><div className="desktop-table"><table><caption className="sr-only">{title}, newest records first</caption><thead><tr><th scope="col">Trip</th><th scope="col">Passenger</th><th scope="col">Rider</th><th scope="col">Route</th><th scope="col">Status / next step</th><th scope="col">Fare</th><th scope="col">Age</th></tr></thead><tbody>{trips.map((trip) => <tr key={trip.id}><td><strong>{trip.id.slice(0, 8)}</strong></td><td>{profiles.get(trip.passenger_id)?.full_name ?? trip.passenger_id.slice(0, 8)}</td><td>{trip.rider_id ? profiles.get(trip.rider_id)?.full_name ?? trip.rider_id.slice(0, 8) : "Unassigned"}</td><td><span className="route-cell"><b>Pickup</b> {trip.pickup_address}<br /><b>Destination</b> {trip.dropoff_address}</span></td><td><Badge tone={tripTone(trip.status)}>{tripPresentation(trip.status).label}</Badge><small className="next-step">{tripPresentation(trip.status).nextAction}</small></td><td><strong>{formatMoney(trip.fare_amount)}</strong></td><td>{elapsed(trip.created_at)}</td></tr>)}</tbody></table></div><div className="mobile-card-list">{trips.map((trip) => <article className="mobile-trip" key={trip.id}><div><strong>{trip.id.slice(0, 8)}</strong><Badge tone={tripTone(trip.status)}>{tripPresentation(trip.status).label}</Badge></div><p>{trip.pickup_address} → {trip.dropoff_address}</p><small>Passenger: {profiles.get(trip.passenger_id)?.full_name ?? "Passenger"}</small><p className="next-step">{tripPresentation(trip.status).nextAction}</p><footer><b>{formatMoney(trip.fare_amount)}</b><span>{elapsed(trip.created_at)}</span></footer></article>)}</div></>}</section>;
}

function Verification({ riders, documents }: { riders: Profile[]; documents: RiderDocument[] }) {
  const [actionId, setActionId] = useState<string | null>(null);
  const [localStatuses, setLocalStatuses] = useState<Record<string, ApprovalStatus>>({});
  const [actionError, setActionError] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [checks, setChecks] = useState<Record<string, Record<string, boolean>>>({});
  const [documentLink, setDocumentLink] = useState<{ url: string; label: string } | null>(null);
  const rows = [...riders].sort((a, b) => Number(b.approval_status === "pending") - Number(a.approval_status === "pending")).map((rider) => ({ rider, docs: documents.filter((doc) => doc.rider_id === rider.id) }));
  async function setApproval(riderId: string, status: ApprovalStatus) {
    const client = getSupabaseBrowserClient(); if (!client) return;
    setActionId(riderId); setActionError("");
    setActionMessage("");
    try {
      const { error } = status === "approved"
        ? await client.rpc("admin_activate_pilot_rider", { p_rider_id: riderId, p_documents_checked: checks[riderId]?.documents === true, p_safety_briefing_completed: checks[riderId]?.briefing === true, p_physical_device_tested: checks[riderId]?.device === true })
        : await client.rpc("admin_set_driver_approval", { p_rider_id: riderId, p_status: status });
      if (error) throw error;
      setLocalStatuses((current) => ({ ...current, [riderId]: status }));
      setActionMessage(`Rider application ${status}.`);
    } catch { setActionError("The application could not be updated. Check your connection and try again."); }
    finally { setActionId(null); }
  }
  async function openDocument(document: RiderDocument) {
    const client = getSupabaseBrowserClient(); if (!client) return;
    setActionId(document.id); setActionError("");
    setDocumentLink(null);
    try {
      const { data, error } = await client.storage.from("rider-documents").createSignedUrl(document.storage_url, 300);
      if (error) throw error;
      setDocumentLink({ url: data.signedUrl, label: document.document_type.replaceAll("_", " ") });
    } catch { setActionError("The document could not be opened. Check your connection and try again."); }
    finally { setActionId(null); }
  }
  return <section className="panel"><PanelHeader title="Rider records" subtitle="Pending applications appear first. Review documents before approving." />{actionMessage ? <p role="status">{actionMessage}</p> : null}{documentLink ? <p role="status"><a className="secondary-action" href={documentLink.url} target="_blank" rel="noopener noreferrer">Open {documentLink.label} in a new tab</a> This private link expires in 5 minutes. Request it again if it expires.</p> : null}{actionError ? <div className="error-banner" role="alert"><AlertTriangle size={18} />{actionError}</div> : null}{rows.length === 0 ? <EmptyState title="No riders found" detail="A rider appears after a real driver application is submitted." /> : <div className="verification-list">{rows.map(({ rider, docs }) => { const status = localStatuses[rider.id] ?? rider.approval_status ?? "pending"; return <article className="verification-row verification-review" key={rider.id}><span className="person-avatar">{rider.full_name.split(" ").map((part) => part[0]).join("").slice(0, 2)}</span><div className="person-details"><strong>{rider.full_name}</strong><span>{rider.phone_number}</span><small>{rider.id}</small><div className="document-actions">{docs.length === 0 ? <small>No documents submitted</small> : docs.map((doc) => <button className="secondary-action" disabled={actionId !== null} key={doc.id} aria-label={`Get ${doc.document_type.replaceAll("_", " ")} for ${rider.full_name}`} onClick={() => void openDocument(doc)}>{actionId === doc.id ? "Opening…" : `View ${doc.document_type.replaceAll("_", " ")}`}</button>)}</div>{status !== "approved" ? <fieldset className="onboarding-checks"><legend>In-person activation checks</legend>{[["documents", "Verified NRC, Class A licence, registration and insurance"], ["briefing", "Completed safety briefing"], ["device", "Tested the rider’s physical device"]].map(([key, label]) => <label key={key}><input type="checkbox" checked={checks[rider.id]?.[key] ?? false} disabled={actionId !== null} onChange={(event) => setChecks((current) => ({ ...current, [rider.id]: { ...current[rider.id], [key]: event.target.checked } }))} />{label}</label>)}</fieldset> : null}</div><div className="doc-progress"><strong>{docs.length}</strong><small>Documents submitted</small></div><Badge tone={status === "approved" ? "green" : status === "rejected" ? "red" : "orange"}>{status}</Badge><div className="approval-actions"><button className="secondary-action" aria-label={`Reject application for ${rider.full_name}`} disabled={actionId !== null || status === "rejected"} onClick={() => void setApproval(rider.id, "rejected")}>Reject</button><button className="primary-action" aria-label={`Approve application for ${rider.full_name}`} disabled={actionId !== null || status === "approved" || !checks[rider.id]?.documents || !checks[rider.id]?.briefing || !checks[rider.id]?.device} onClick={() => void setApproval(rider.id, "approved")}>{actionId === rider.id ? "Saving…" : "Approve"}</button></div></article>; })}</div>}</section>;
}

function Finance({ trips, profiles }: { trips: Trip[]; profiles: Map<string, Profile> }) {
  const completed = trips.filter((trip) => trip.status === "completed"); const revenue = completed.reduce((sum, trip) => sum + Number(trip.fare_amount ?? 0), 0); const outstanding = completed.filter((trip) => trip.payment_status !== "rider_confirmed");
  return <div className="dashboard-stack"><section className="kpi-grid finance-kpis"><article className="kpi-card"><span>Completed fares</span><strong>{formatMoney(revenue)}</strong><small>{completed.length} completed trips</small></article><article className="kpi-card"><span>Payment confirmed</span><strong>{completed.filter((t) => t.payment_status === "rider_confirmed").length}</strong><small>Participant acknowledgements</small></article><article className="kpi-card"><span>Outstanding</span><strong>{outstanding.length}</strong><small>Needs confirmation or review</small></article></section><TripsPanel trips={outstanding} profiles={profiles} title="Outstanding payment acknowledgements" /></div>;
}

function Analytics({ trips }: { trips: Trip[] }) {
  const byStatus = Array.from(new Set(trips.map((trip) => trip.status))).map((status) => [status, trips.filter((trip) => trip.status === status).length] as const);
  return <section className="panel"><PanelHeader title="Trips by status" subtitle="Calculated from current database rows" />{byStatus.length === 0 ? <EmptyState title="No analytics yet" detail="Reports populate after real trips are created." /> : byStatus.map(([status, count]) => <div className="health-row" key={status}><span><strong>{status.replaceAll("_", " ")}</strong><b>{count}</b></span><div className="progress-track"><i style={{ width: `${Math.round(count / trips.length * 100)}%` }} /></div></div>)}</section>;
}

function Roles({ profiles }: { profiles: Profile[] }) {
  const roles = ["admin", "rider", "passenger"] as const;
  return <section className="roles-grid">{roles.map((role, index) => <article className="panel role-card" key={role}><span className={`role-badge role-${index}`}><Users size={20} /></span><h2>{role[0].toUpperCase() + role.slice(1)}</h2><p>Role stored and enforced through database policies.</p><div><span>{profiles.filter((p) => p.role === role).length} members</span><Badge tone={role === "admin" ? "red" : "blue"}>{role}</Badge></div></article>)}</section>;
}

function Unsupported({ title, detail }: { title: string; detail: string }) { return <section className="panel"><PanelHeader title={title} /><EmptyState title="Backend required" detail={detail} /></section>; }
