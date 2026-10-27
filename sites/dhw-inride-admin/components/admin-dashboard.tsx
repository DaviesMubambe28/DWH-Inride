"use client";

import {
  Activity, AlertTriangle, BarChart3, Bike, ChevronDown, CircleDollarSign,
  ClipboardCheck, Clock3, LayoutDashboard, LogOut, Map as MapIcon, Menu, RefreshCw,
  Search, ShieldAlert, UserCog, Users, X,
} from "lucide-react";
import type { User } from "@supabase/supabase-js";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { getSupabaseBrowserClient, getSupabaseConfig } from "@/lib/supabase";

type Section = "overview" | "verification" | "live" | "finance" | "incidents" | "analytics" | "roles";
type Tone = "green" | "orange" | "blue" | "red" | "slate";
type ApprovalStatus = "pending" | "approved" | "rejected";
type Profile = { id: string; role: "passenger" | "rider" | "admin"; full_name: string; phone_number: string; is_online: boolean; rating: number; total_trips: number; approval_status: ApprovalStatus | null };
type Trip = { id: string; passenger_id: string; rider_id: string | null; status: string; pickup_address: string; dropoff_address: string; fare_amount: number | null; payment_method: string; payment_status: string; created_at: string; completed_at: string | null };
type RiderDocument = { id: string; rider_id: string; document_type: string; storage_url: string; verification_status: string; submitted_at: string };
type TripLocation = { id: number; trip_id: string; rider_id: string; lat: number; lng: number; recorded_at: string };
type DashboardData = { profiles: Profile[]; trips: Trip[]; documents: RiderDocument[]; locations: TripLocation[] };

const emptyData: DashboardData = { profiles: [], trips: [], documents: [], locations: [] };
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
  overview: { eyebrow: "Operations", title: "Overview", description: "Current activity from the production database." },
  verification: { eyebrow: "Trust & safety", title: "Rider verification", description: "Review real rider profiles and submitted documents." },
  live: { eyebrow: "Dispatch control", title: "Live operations", description: "Monitor active trips and current rider availability." },
  finance: { eyebrow: "Finance operations", title: "Trip revenue", description: "Completed fares and participant payment acknowledgements." },
  incidents: { eyebrow: "Safety desk", title: "Complaints & incidents", description: "This workflow requires the incidents schema before it can be enabled." },
  analytics: { eyebrow: "Performance", title: "Reports", description: "Operational totals calculated from real trips." },
  roles: { eyebrow: "Access control", title: "Roles & access", description: "Current application roles stored in profiles." },
};

function Badge({ children, tone = "slate" }: { children: React.ReactNode; tone?: Tone }) { return <span className={`badge badge-${tone}`}>{children}</span>; }
function PanelHeader({ title, subtitle }: { title: string; subtitle?: string }) { return <div className="panel-header"><div><h2>{title}</h2>{subtitle ? <p>{subtitle}</p> : null}</div></div>; }
function EmptyState({ title, detail }: { title: string; detail: string }) { return <div className="empty-state"><Search size={22} /><strong>{title}</strong><span>{detail}</span></div>; }

function SetupRequired() {
  return <main className="setup-page"><section className="panel setup-card"><span className="brand-mark"><Bike size={24} /></span><h1>Connect Supabase</h1><p>The dashboard does not display sample records. Add these values to <code>sites/dhw-inride-admin/.env.local</code> and restart it:</p><pre>NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co{"\n"}NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=your-publishable-key</pre><p>Use a publishable key only. Never put a secret or service-role key in a <code>NEXT_PUBLIC_</code> variable.</p></section></main>;
}

function SignIn({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const [email, setEmail] = useState(""); const [password, setPassword] = useState("");
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    const client = getSupabaseBrowserClient(); if (!client) return;
    const result = await client.auth.signInWithPassword({ email: email.trim(), password });
    setBusy(false); if (result.error) setError(result.error.message); else if (result.data.user) onSignedIn(result.data.user);
  }
  return <main className="setup-page"><form className="panel setup-card" onSubmit={submit}><span className="brand-mark"><Bike size={24} /></span><h1>Operations sign in</h1><p>Use an account whose profile role is <strong>admin</strong>.</p><label>Email<input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label><label>Password<input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required /></label>{error ? <p className="form-error" role="alert">{error}</p> : null}<button className="primary-action full" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button></form></main>;
}

function tripTone(status: string): Tone { if (status === "completed") return "slate"; if (status === "cancelled") return "red"; if (status === "requesting") return "orange"; return "green"; }
function formatMoney(value: number | null) { return value == null ? "Pending" : `K${Number(value).toFixed(2)}`; }
function elapsed(iso: string) { const minutes = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000)); return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`; }

export function AdminDashboard() {
  const { configured } = getSupabaseConfig();
  const [user, setUser] = useState<User | null>(null); const [checkingSession, setCheckingSession] = useState(true);
  const [section, setSection] = useState<Section>("overview"); const [drawerOpen, setDrawerOpen] = useState(false);
  const [search, setSearch] = useState(""); const [data, setData] = useState<DashboardData>(emptyData);
  const [loading, setLoading] = useState(false); const [error, setError] = useState("");
  const [authorized, setAuthorized] = useState<boolean | null>(null);

  const load = useCallback(async () => {
    const client = getSupabaseBrowserClient(); if (!client || !user) return;
    setLoading(true); setError("");
    const [profileResult, tripsResult, docsResult, locationsResult] = await Promise.all([
      client.from("profiles").select("id,role,full_name,phone_number,is_online,rating,total_trips,approval_status").order("created_at", { ascending: false }),
      client.from("authorized_trip_details").select("id,passenger_id,rider_id,status,pickup_address,dropoff_address,fare_amount,payment_method,payment_status,created_at,completed_at").order("created_at", { ascending: false }).limit(500),
      client.from("rider_documents").select("id,rider_id,document_type,storage_url,verification_status,submitted_at").order("submitted_at", { ascending: false }),
      client.from("trip_locations").select("id,trip_id,rider_id,lat,lng,recorded_at").order("recorded_at", { ascending: false }).limit(500),
    ]);
    const ownProfile = profileResult.data?.find((profile) => profile.id === user.id);
    if (profileResult.error) { setError(profileResult.error.message); setAuthorized(null); }
    else if (ownProfile?.role !== "admin") { setAuthorized(false); setData(emptyData); }
    else {
      const firstError = tripsResult.error ?? docsResult.error ?? locationsResult.error; setAuthorized(true);
      if (firstError) setError(firstError.message);
      setData({ profiles: (profileResult.data ?? []) as Profile[], trips: (tripsResult.data ?? []) as Trip[], documents: (docsResult.data ?? []) as RiderDocument[], locations: (locationsResult.data ?? []) as TripLocation[] });
    }
    setLoading(false);
  }, [user]);

  useEffect(() => {
    if (!configured) { setCheckingSession(false); return; }
    const client = getSupabaseBrowserClient()!;
    client.auth.getUser().then(({ data: authData }) => { setUser(authData.user ?? null); setCheckingSession(false); });
    const { data: listener } = client.auth.onAuthStateChange((_event, session) => setUser(session?.user ?? null));
    return () => listener.subscription.unsubscribe();
  }, [configured]);
  useEffect(() => { if (user) void load(); }, [user, load]);
  useEffect(() => {
    const client = getSupabaseBrowserClient(); if (!client || !user || authorized !== true) return;
    const channel = client.channel("admin-dashboard").on("postgres_changes", { event: "*", schema: "public" }, () => void load()).subscribe();
    return () => { void client.removeChannel(channel); };
  }, [user, authorized, load]);

  const profilesById = useMemo(() => new Map(data.profiles.map((profile) => [profile.id, profile])), [data.profiles]);
  const activeTrips = data.trips.filter((trip) => !["completed", "cancelled"].includes(trip.status));
  const riders = data.profiles.filter((profile) => profile.role === "rider");
  const query = search.trim().toLowerCase();
  const shownTrips = query ? data.trips.filter((trip) => [trip.id, trip.pickup_address, trip.dropoff_address, profilesById.get(trip.passenger_id)?.full_name, trip.rider_id ? profilesById.get(trip.rider_id)?.full_name : ""].join(" ").toLowerCase().includes(query)) : data.trips;
  const copy = sectionCopy[section];

  if (!configured) return <SetupRequired />;
  if (checkingSession) return <main className="setup-page"><RefreshCw className="spin" /><p>Checking session…</p></main>;
  if (!user) return <SignIn onSignedIn={setUser} />;
  if (authorized === null && loading) return <main className="setup-page"><RefreshCw className="spin" /><p>Loading operations data…</p></main>;
  if (authorized === null && error) return <main className="setup-page"><section className="panel setup-card"><ShieldAlert size={32} /><h1>Operations data unavailable</h1><p role="alert">{error}</p><button className="primary-action full" onClick={() => void load()}>Retry</button></section></main>;
  if (authorized === false) return <main className="setup-page"><section className="panel setup-card"><ShieldAlert size={32} /><h1>Admin access required</h1><p>This signed-in account is not authorized to use operations tools.</p><button className="secondary-action full" onClick={() => void getSupabaseBrowserClient()?.auth.signOut()}>Sign out</button></section></main>;

  return <div className="admin-shell"><aside className={`sidebar ${drawerOpen ? "sidebar-open" : ""}`} aria-label="Main navigation"><div className="brand-row"><span className="brand-mark"><Bike size={22} /></span><span className="brand-name">DHW<span>-inRide</span></span><button className="icon-button sidebar-close" onClick={() => setDrawerOpen(false)} aria-label="Close menu"><X size={20} /></button></div><div className="workspace-chip"><span className="workspace-avatar">CO</span><span><small>Workspace</small><strong>Chongwe Operations</strong></span><ChevronDown size={16} /></div><nav className="side-nav">{navItems.map((item) => { const Icon = item.icon; return <button key={item.id} className={section === item.id ? "nav-item active" : "nav-item"} onClick={() => { setSection(item.id); setDrawerOpen(false); }}><Icon size={18} /><span>{item.label}</span></button>; })}</nav><div className="sidebar-footer"><button className="nav-item" onClick={() => void getSupabaseBrowserClient()?.auth.signOut()}><LogOut size={18} /><span>Sign out</span></button><div className="operator"><span className="operator-avatar">{user.email?.slice(0, 2).toUpperCase()}</span><span><strong>{user.email}</strong><small>Authenticated admin</small></span></div></div></aside>{drawerOpen ? <button className="drawer-scrim" aria-label="Close navigation" onClick={() => setDrawerOpen(false)} /> : null}<main className="main-panel"><header className="topbar"><button className="icon-button menu-button" onClick={() => setDrawerOpen(true)} aria-label="Open menu"><Menu size={21} /></button><div className="global-search"><Search size={18} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search trips, people or places…" /></div><span className="system-live"><i /> Supabase connected</span><button className="icon-button" onClick={() => void load()} aria-label="Refresh data"><RefreshCw className={loading ? "spin" : ""} size={19} /></button></header><div className="page-content"><section className="page-heading"><div><span className="eyebrow">{copy.eyebrow}</span><h1>{copy.title}</h1><p>{copy.description}</p></div></section>{error ? <div className="error-banner" role="alert"><AlertTriangle size={18} />{error}<button onClick={() => void load()}>Retry</button></div> : null}{section === "overview" ? <Overview trips={data.trips} riders={riders} activeTrips={activeTrips} profiles={profilesById} /> : null}{section === "live" ? <TripsPanel trips={shownTrips} profiles={profilesById} title="Trip control" /> : null}{section === "verification" ? <Verification riders={riders} documents={data.documents} /> : null}{section === "finance" ? <Finance trips={data.trips} profiles={profilesById} /> : null}{section === "incidents" ? <Unsupported title="Incident tracking is not configured" detail="The current database has no incidents, assignments, notes, or resolution-history tables. No fake queue is shown." /> : null}{section === "analytics" ? <Analytics trips={data.trips} /> : null}{section === "roles" ? <Roles profiles={data.profiles} /> : null}</div></main></div>;
}

function Overview({ trips, riders, activeTrips, profiles }: { trips: Trip[]; riders: Profile[]; activeTrips: Trip[]; profiles: Map<string, Profile> }) {
  const today = new Date().toDateString(); const tripsToday = trips.filter((trip) => new Date(trip.created_at).toDateString() === today).length;
  const cards = [["Trips today", tripsToday, "blue"], ["Riders online", riders.filter((r) => r.is_online).length, "green"], ["Active trips", activeTrips.length, "orange"], ["Awaiting dispatch", activeTrips.filter((t) => t.status === "requesting").length, "red"]] as const;
  return <div className="dashboard-stack"><section className="kpi-grid">{cards.map(([label, value, tone], index) => <article className="kpi-card" key={label}><div className={`kpi-icon tone-${tone}`}>{[<Activity key="a" />, <Bike key="b" />, <Clock3 key="c" />, <AlertTriangle key="d" />][index]}</div><span>{label}</span><strong>{value}</strong><small>Live database total</small></article>)}</section><TripsPanel trips={activeTrips.slice(0, 10)} profiles={profiles} title="Active trips" /></div>;
}

function TripsPanel({ trips, profiles, title }: { trips: Trip[]; profiles: Map<string, Profile>; title: string }) {
  return <section className="panel"><PanelHeader title={title} subtitle="Newest records first" />{trips.length === 0 ? <EmptyState title="No trips found" detail="New real bookings will appear here after they are persisted." /> : <><div className="desktop-table"><table><thead><tr><th>Trip</th><th>Passenger</th><th>Rider</th><th>Route</th><th>Status</th><th>Fare</th><th>Age</th></tr></thead><tbody>{trips.map((trip) => <tr key={trip.id}><td><strong>{trip.id.slice(0, 8)}</strong></td><td>{profiles.get(trip.passenger_id)?.full_name ?? trip.passenger_id.slice(0, 8)}</td><td>{trip.rider_id ? profiles.get(trip.rider_id)?.full_name ?? trip.rider_id.slice(0, 8) : "Unassigned"}</td><td><span className="route-cell">{trip.pickup_address} → {trip.dropoff_address}</span></td><td><Badge tone={tripTone(trip.status)}>{trip.status.replaceAll("_", " ")}</Badge></td><td><strong>{formatMoney(trip.fare_amount)}</strong></td><td>{elapsed(trip.created_at)}</td></tr>)}</tbody></table></div><div className="mobile-card-list">{trips.map((trip) => <article className="mobile-trip" key={trip.id}><div><strong>{trip.id.slice(0, 8)}</strong><Badge tone={tripTone(trip.status)}>{trip.status.replaceAll("_", " ")}</Badge></div><p>{trip.pickup_address} → {trip.dropoff_address}</p><small>{profiles.get(trip.passenger_id)?.full_name ?? "Passenger"}</small><footer><b>{formatMoney(trip.fare_amount)}</b><span>{elapsed(trip.created_at)}</span></footer></article>)}</div></>}</section>;
}

function Verification({ riders, documents }: { riders: Profile[]; documents: RiderDocument[] }) {
  const [actionId, setActionId] = useState<string | null>(null);
  const [localStatuses, setLocalStatuses] = useState<Record<string, ApprovalStatus>>({});
  const [actionError, setActionError] = useState("");
  const rows = riders.map((rider) => ({ rider, docs: documents.filter((doc) => doc.rider_id === rider.id) }));
  async function setApproval(riderId: string, status: ApprovalStatus) {
    const client = getSupabaseBrowserClient(); if (!client) return;
    setActionId(riderId); setActionError("");
    const { error } = await client.rpc("admin_set_driver_approval", { p_rider_id: riderId, p_status: status });
    if (error) setActionError(error.message);
    else setLocalStatuses((current) => ({ ...current, [riderId]: status }));
    setActionId(null);
  }
  async function openDocument(document: RiderDocument) {
    const client = getSupabaseBrowserClient(); if (!client) return;
    setActionId(document.id); setActionError("");
    const { data, error } = await client.storage.from("rider-documents").createSignedUrl(document.storage_url, 300);
    if (error) setActionError(error.message);
    else window.open(data.signedUrl, "_blank", "noopener,noreferrer");
    setActionId(null);
  }
  return <section className="panel"><PanelHeader title="Rider records" subtitle="Review private documents, then approve or reject the driver account" />{actionError ? <div className="error-banner" role="alert"><AlertTriangle size={18} />{actionError}</div> : null}{rows.length === 0 ? <EmptyState title="No riders found" detail="A rider appears after a real driver application is submitted." /> : <div className="verification-list">{rows.map(({ rider, docs }) => { const status = localStatuses[rider.id] ?? rider.approval_status ?? "pending"; return <article className="verification-row verification-review" key={rider.id}><span className="person-avatar">{rider.full_name.split(" ").map((part) => part[0]).join("").slice(0, 2)}</span><div className="person-details"><strong>{rider.full_name}</strong><span>{rider.phone_number}</span><small>{rider.id}</small><div className="document-actions">{docs.length === 0 ? <small>No documents submitted</small> : docs.map((doc) => <button className="secondary-action" disabled={actionId !== null} key={doc.id} onClick={() => void openDocument(doc)}>{actionId === doc.id ? "Opening…" : `View ${doc.document_type.replaceAll("_", " ")}`}</button>)}</div></div><div className="doc-progress"><strong>{docs.length}</strong><small>Documents submitted</small></div><Badge tone={status === "approved" ? "green" : status === "rejected" ? "red" : "orange"}>{status}</Badge><div className="approval-actions"><button className="secondary-action" disabled={actionId !== null || status === "rejected"} onClick={() => void setApproval(rider.id, "rejected")}>Reject</button><button className="primary-action" disabled={actionId !== null || status === "approved"} onClick={() => void setApproval(rider.id, "approved")}>{actionId === rider.id ? "Saving…" : "Approve"}</button></div></article>; })}</div>}</section>;
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
