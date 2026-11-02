# Windows backup and isolated database restore

Prepared from `533a2a5` for **development-only** project `vhckkmzyabbsbgjuhjmq`. This is an operator procedure, not evidence that a backup/restore has run. All three PowerShell blocks passed syntax parsing with official checksum-verified PowerShell 7.5.4 on Linux. The revised inventory SQL ran successfully through the hosted read-only Management API, with numeric counts and security catalog coverage; this does not verify native Windows permissions, TLS, tool execution or restore. Hosted migrations remain blocked until the resulting backup has passed an isolated restore review. Do not upload the archive, role export, inventories, document bytes, password or certificate private keys to Git or chat.

## Why the cloud route cannot be enabled here

The runtime [TCP/VPN documentation](skill://plugin_connector_1p_c5b7d5df5d7081918f2c4be5a633ed5d/cloud-environment-runtime/references/vpn.md) says raw TCP on `proxy:8088` requires **VPN plus a TCP grant**, and resolved IPv4 destinations must be in `10/8`, `172.16/12`, `192.168/16` or `100.64/10`. Grants cover all TCP ports; changing them requires a replacement environment. The pooler resolves to public addresses, outside these ranges. VPN configuration is false and grants are empty in this runtime. The earlier proxy refusal is historical; it was not repeated.

The onboarding documentation/tool exposes HTTP allowed_domains, not public TCP activation. **The available documentation does not establish a public-TCP UI setting or a verified click path.** Do not infer that one exists, edit the runtime policy file, install a VPN or change an HTTP allowlist. The previous handoff's suggestion that merely granting this public hostname would unblock the backup was incorrect and is superseded here. A separately supported network architecture would need platform support; no such resource was created.

## Verified connection details

Read-only Management API checks reconfirmed the following on 2 November 2026. No password was retrieved or changed.

| Field | Value / source |
|---|---|
| Project | `vhckkmzyabbsbgjuhjmq` — owner-confirmed development-only |
| Pooler host | `aws-0-eu-west-1.pooler.supabase.com` — actual project pooler inventory |
| Database | `postgres` — pooler inventory and database query |
| Username | `postgres.vhckkmzyabbsbgjuhjmq` — actual pooler inventory |
| Native backup port | `5432` — documented shared-pooler **session** endpoint; cross-check **Connect → Session pooler → View parameters** for this project before running |
| Other advertised port | `6543`, transaction mode — do not use for this procedure |
| Server | PostgreSQL `17.11`; 12 recorded migrations at inspection |
| TLS | Require `verify-full` with the correct CA file. **Cloud PostgreSQL TLS/authentication remain untested**, because no supported TCP route exists |

Sources: [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres), [Supabase backup/restore](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore), [PostgreSQL 17 pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html), [TLS verification](https://www.postgresql.org/docs/17/libpq-ssl.html). Supabase's default CLI schema dump excludes managed schemas; this procedure instead attempts a native **whole-database schema-and-data archive** and stops on any privilege failure.

## Prepare the Windows machine

Use PowerShell 7 and trusted PostgreSQL **17** client tools, preferably 17.11 or a newer 17.x patch. `psql`, `pg_dump`, `pg_dumpall` and `pg_restore` must be on PATH from the same installation. Download through the official PostgreSQL Windows distribution; keep installer signature verification enabled. Use Docker Desktop with Linux containers for the isolated restore below.

Open this project's Supabase dashboard, **Connect → Session pooler → View parameters**, and compare host/port/database/user with the table. If any differs, stop and report only those non-secret fields. Obtain the **existing database password** from your password manager. It is different from SUPABASE_ACCESS_TOKEN. Do not reset it just because it is not available in this cloud runtime.

Download the server root certificate from this project's **Database settings**, as documented in the connection guide. Record the certificate SHA-256; it is public trust material. If the shared pooler certificate is issued under another CA, use the provider's documented trust chain for that endpoint. A certificate mismatch/unknown CA is a blocker: do not change to `require`, `prefer`, `allow` or `disable` to get past it.

Choose a **BitLocker-encrypted NTFS folder outside every checkout and cloud-synced public folder**. Limit access to the operator and approved recovery custodians; retain the encrypted volume recovery key separately. The commands restrict the new bundle to the current Windows identity, but do not enable or attest BitLocker. Do not use a PowerShell transcript/screen recording during password entry. The native tools' `--password` option prompts securely without placing the password in PowerShell variables, command arguments or files. Several prompts are intentional.

Pause all development test clients and operator schema/data changes during capture and comparison. Do not enable bookings or schedules. The database archive uses its own consistent snapshot; the before/after inventories require a quiet source. Inventory drift means the comparison must be repeated after quiescing, not ignored.

## Capture the whole database and roles

Run these blocks in the **same PowerShell session** from the repository root. All placeholder paths below are entered interactively; no secret goes into a command.

```powershell
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not (Test-Path '.git')) { throw 'Run from the repository root.' }
function Assert-Native([string] $Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed; backup is NOT verified." }
}
foreach ($Tool in @('psql', 'pg_dump', 'pg_dumpall', 'pg_restore')) {
    Get-Command $Tool -CommandType Application -ErrorAction Stop | Out-Null
    $Version = & $Tool --version
    Assert-Native "$Tool version check"
    if ($Version -notmatch '\b17\.') { throw 'Use PostgreSQL 17 tools.' }
}
foreach ($Name in @('PGPASSWORD', 'PGPASSFILE', 'PGSERVICE', 'PGSERVICEFILE', 'PGHOSTADDR', 'PGOPTIONS')) {
    if (Test-Path "Env:$Name") { throw "Start a clean shell without $Name; do not print its value." }
}
$DbHost = 'aws-0-eu-west-1.pooler.supabase.com'
$DbUser = 'postgres.vhckkmzyabbsbgjuhjmq'
$DbArgs = @('--host', $DbHost, '--port', '5432', '--username', $DbUser,
            '--dbname', 'postgres', '--password')
$CaFile = (Resolve-Path (Read-Host 'Path to the trusted database CA certificate')).Path
$Root = [IO.Path]::GetFullPath((Read-Host 'Existing private encrypted backup folder outside Git'))
if (-not (Test-Path $Root -PathType Container)) { throw 'Create the protected encrypted folder first.' }
$Repo = (Resolve-Path '.').Path.TrimEnd('\') + '\'
if (($Root.TrimEnd('\') + '\').StartsWith($Repo, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Backup storage must be outside the checkout.'
}
$Bundle = Join-Path $Root ('inride-dev-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
New-Item -ItemType Directory -Path $Bundle -ErrorAction Stop | Out-Null
$OwnerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls $Bundle /inheritance:r /grant:r "*${OwnerSid}:(OI)(CI)F" | Out-Null
Assert-Native 'Private folder ACL'
$OldSslMode = $env:PGSSLMODE
$OldSslRoot = $env:PGSSLROOTCERT
$OldTimeout = $env:PGCONNECT_TIMEOUT
$env:PGSSLMODE = 'verify-full'
$env:PGSSLROOTCERT = $CaFile
$env:PGCONNECT_TIMEOUT = '15'
```

Create a private inventory query. It exports counts and catalog security metadata, not row contents, but still belongs with the protected backup. This deliberately checks every non-system ordinary/partitioned table, including Auth, Storage metadata, migration history and private application data. Permission/RLS failures must stop capture.

```powershell
$InventorySql = @'
begin isolation level repeatable read read only;
set local row_security = off;
select jsonb_build_object(
  'tables', (select jsonb_object_agg(format('%I.%I',n.nspname,c.relname),
    ((xpath('/row/n/text()',query_to_xml(format('select count(*) as n from %I.%I',
      n.nspname,c.relname),false,true,'')))[1]::text)::bigint)
    from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where c.relkind in ('r','p') and n.nspname not in ('pg_catalog','information_schema')
      and n.nspname not like 'pg_toast%' and n.nspname not like 'pg_temp%'),
  'security', (select jsonb_agg(jsonb_build_array(n.nspname,c.relname,
    pg_get_userbyid(c.relowner),c.relrowsecurity,c.relforcerowsecurity,c.relacl::text)
    order by n.nspname,c.relname)
    from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where c.relkind in ('r','p','v','S') and n.nspname in ('public','private','auth','storage','supabase_migrations')),
  'columns', (select jsonb_agg(jsonb_build_array(n.nspname,c.relname,a.attname,
    format_type(a.atttypid,a.atttypmod),a.attnotnull,a.attacl::text,
    a.attgenerated,a.attidentity,pg_get_expr(d.adbin,d.adrelid))
    order by n.nspname,c.relname,a.attnum)
    from pg_attribute a join pg_class c on c.oid=a.attrelid
    join pg_namespace n on n.oid=c.relnamespace
    left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum
    where a.attnum>0 and not a.attisdropped and c.relkind in ('r','p','v','S')
      and n.nspname in ('public','private','auth','storage','supabase_migrations')),
  'schemas', (select jsonb_agg(jsonb_build_array(nspname,pg_get_userbyid(nspowner),nspacl::text)
    order by nspname) from pg_namespace
    where nspname in ('public','private','auth','storage','supabase_migrations')),
  'policies', (select jsonb_agg(jsonb_build_array(schemaname,tablename,policyname,
    permissive,roles,cmd,qual,with_check) order by schemaname,tablename,policyname)
    from pg_policies where schemaname in ('public','private','auth','storage')),
  'functions', (select jsonb_agg(jsonb_build_array(n.nspname,p.proname,
    pg_get_function_identity_arguments(p.oid),pg_get_userbyid(p.proowner),
    md5(pg_get_functiondef(p.oid)),p.proacl::text)
    order by n.nspname,p.proname,pg_get_function_identity_arguments(p.oid))
    from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname in ('public','private') and p.prokind in ('f','p'))
);
commit;
'@
$InventoryFile = Join-Path $Bundle 'inventory.sql'
[IO.File]::WriteAllText($InventoryFile, $InventorySql, [Text.UTF8Encoding]::new($false))
try {
    $TcpOk = Test-NetConnection -ComputerName $DbHost -Port 5432 -InformationLevel Quiet
    if (-not $TcpOk) { throw 'Local Windows TCP connection failed; stop.' }
    & psql @DbArgs -X --set ON_ERROR_STOP=1 --command '\conninfo'
    Assert-Native 'Certificate-verified TLS connection'
    & psql @DbArgs -X --set ON_ERROR_STOP=1 --command `
      "select current_database(),current_user,current_setting('server_version');"
    Assert-Native 'Database identity check'
    # Expect postgres / postgres / 17.11 (or a documented newer 17.x patch).
    if ((Read-Host 'Confirm dashboard and database identities match; type VERIFIED') -cne 'VERIFIED') {
        throw 'Database identity not confirmed.'
    }
    & psql @DbArgs -X --quiet --no-align --tuples-only --set ON_ERROR_STOP=1 `
      --file $InventoryFile --output (Join-Path $Bundle 'source-before.json')
    Assert-Native 'Before inventory'
    & pg_dump @DbArgs --format=custom --serializable-deferrable --lock-wait-timeout=15s `
      --file (Join-Path $Bundle 'database.dump')
    Assert-Native 'Whole database schema-and-data export'
    & pg_dumpall @DbArgs --roles-only --no-role-passwords --file (Join-Path $Bundle 'roles.sql')
    Assert-Native 'Role definitions without database login passwords'
    & psql @DbArgs -X --quiet --no-align --tuples-only --set ON_ERROR_STOP=1 `
      --file $InventoryFile --output (Join-Path $Bundle 'source-after.json')
    Assert-Native 'After inventory'
    & pg_restore --list --file (Join-Path $Bundle 'archive-toc.list') (Join-Path $Bundle 'database.dump')
    Assert-Native 'Archive table of contents'
    $Before = (Get-Content (Join-Path $Bundle 'source-before.json') -Raw).Trim()
    $After = (Get-Content (Join-Path $Bundle 'source-after.json') -Raw).Trim()
    if ($Before -cne $After) { throw 'Source changed during capture; do not certify this bundle.' }
    $Toc = Get-Content (Join-Path $Bundle 'archive-toc.list') -Raw
    foreach ($Required in @('TABLE DATA auth users', 'TABLE DATA public profiles',
                            'TABLE DATA public trips', 'TABLE DATA supabase_migrations schema_migrations')) {
        if (-not $Toc.Contains($Required)) { throw "Archive missing $Required; stop." }
    }
    Copy-Item $CaFile (Join-Path $Bundle 'database-root.crt') -ErrorAction Stop
    Get-ChildItem $Bundle -File | Get-FileHash -Algorithm SHA256 |
      Select-Object @{Name='File';Expression={Split-Path $_.Path -Leaf}},Hash |
      ConvertTo-Json | Set-Content (Join-Path $Bundle 'SHA256.json') -Encoding utf8
    'Capture complete. Backup verification remains BLOCKED until isolated restore passes.'
} finally {
    $env:PGSSLMODE = $OldSslMode
    $env:PGSSLROOTCERT = $OldSslRoot
    $env:PGCONNECT_TIMEOUT = $OldTimeout
}
```

No `--schema-only`, table/schema exclusion, `--no-owner`, `--no-acl` or ignore-error option is used. If managed objects cannot be read, leave the gate BLOCKED and investigate a provider-supported export. Do not quietly substitute a default CLI schema dump. Role **passwords** are intentionally excluded: destination database credentials must be separately established/rotated. Auth password hashes inside `auth.users` are data in the full archive and are sensitive. A database export is not a copy of encryption root keys; provider-encrypted Vault/other data can remain unusable without separately recoverable key material.

## Verify and restore into an isolated local database

Keep the source quiet until source-versus-restore comparison finishes. Use a brand-new disposable **Supabase PostgreSQL 17** container with matching extensions, not a generic PostgreSQL image lacking Supabase extensions. The previously used local image was `supabase/postgres:17.11.0.004`; confirm its provenance/digest and extension compatibility with this archive. A version match alone is insufficient. No cloud project, paid resource, source database reset or source write is needed.

Before restore, inspect the protected `roles.sql` and archive TOC. Reuse matching platform roles in the isolated cluster. Create any missing custom owner/grantee roles and reproduce required attributes/memberships from the role export **only in this disposable cluster**, using its local superuser. Do not replay CREATE statements for already-existing reserved roles, demote the restore administrator, copy source login passwords or fix ownership errors with `--no-owner/--no-acl`. Unresolved role/extension prerequisites block restoration; record the actual mismatch.

```powershell
# Run in the same session, or set $Bundle to the existing private bundle path.
$Hashes = Get-Content (Join-Path $Bundle 'SHA256.json') -Raw | ConvertFrom-Json
foreach ($Entry in $Hashes) {
    $Actual = (Get-FileHash (Join-Path $Bundle $Entry.File) -Algorithm SHA256).Hash
    if ($Actual -cne $Entry.Hash) { throw 'Backup checksum mismatch; stop.' }
}
$Container = 'inride-recovery-' + [Guid]::NewGuid().ToString('N')
$RestoreDb = 'inride_restore_' + [Guid]::NewGuid().ToString('N')
$OldLocalPassword = $env:POSTGRES_PASSWORD
try {
    # Random disposable LOCAL credential, unrelated to the hosted database password.
    $env:POSTGRES_PASSWORD = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    & docker run --detach --name $Container --network none --env POSTGRES_PASSWORD `
      supabase/postgres:17.11.0.004 postgres -c cron.launch_active_jobs=off
    Assert-Native 'Start isolated database'
} finally { $env:POSTGRES_PASSWORD = $OldLocalPassword }
# No published ports, outbound network, Auth/Storage/Edge services or app clients.
# Wait for readiness; a running container alone is not proof.
$Ready = $false
for ($Attempt = 0; $Attempt -lt 30; $Attempt++) {
    & docker exec $Container pg_isready -U supabase_admin -d postgres | Out-Null
    if ($LASTEXITCODE -eq 0) { $Ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $Ready) { throw 'Isolated database did not become ready.' }
& docker exec $Container psql -X -U supabase_admin -d postgres --set ON_ERROR_STOP=1 `
  --command "select current_user,rolsuper from pg_roles where rolname=current_user;"
Assert-Native 'Local restore administrator check'
# Confirm this is the isolated superuser, then resolve the reviewed role/extension
# prerequisites above. Do not continue with missing owner/grantee roles.
if ((Read-Host 'Local administrator, roles and extensions reviewed; type ISOLATED') -cne 'ISOLATED') {
    throw 'Isolated prerequisites not confirmed.'
}
& docker exec $Container createdb -U supabase_admin --template template0 $RestoreDb
Assert-Native 'Create fresh isolated database'
& docker cp (Join-Path $Bundle 'database.dump') "${Container}:/tmp/database.dump"
Assert-Native 'Copy archive'
& docker cp (Join-Path $Bundle 'inventory.sql') "${Container}:/tmp/inventory.sql"
Assert-Native 'Copy verification SQL'
& docker exec $Container pg_restore -U supabase_admin --dbname $RestoreDb `
  --clean --if-exists --single-transaction --exit-on-error /tmp/database.dump
Assert-Native 'Restore data, schema, ownership and grants'
& docker exec $Container psql -X -U supabase_admin --dbname $RestoreDb --quiet --no-align `
  --tuples-only --set ON_ERROR_STOP=1 --file /tmp/inventory.sql --output /tmp/restored.json
Assert-Native 'Restored inventory'
& docker cp "${Container}:/tmp/restored.json" (Join-Path $Bundle 'restored.json')
Assert-Native 'Collect private verification inventory'
$Expected = (Get-Content (Join-Path $Bundle 'source-after.json') -Raw).Trim()
$Restored = (Get-Content (Join-Path $Bundle 'restored.json') -Raw).Trim()
if ($Expected -cne $Restored) { throw 'Data/catalog security inventory differs; review before claiming recovery.' }
'Database restore comparison PASS. Auth/Storage HTTP, keys and full-service recovery remain unverified.'
# After reviewing/capturing the result, destroy ONLY this disposable container:
# docker rm --force --volumes $Container
```

Restore SQL is privileged executable material: use only your reviewed archive. `--clean` is permitted here solely because the destination is a newly created database in a new network-isolated disposable container. Never change these commands to point at the hosted source. If restore fails, retain the protected logs, report only a sanitized error class/object type, and stop; do not delete failing archive entries or ignore errors.

Before certifying the database gate, review table counts (including Auth users, profiles, trips and all 12 migration records), original owners/ACLs, RLS/policy and function comparison, source/destination PostgreSQL/extensions and role memberships. The JSON comparison covers table counts and selected security catalogs, not every platform setting or content digest. Successful full archive extraction/restore and checksum validation are required alongside it. Record actual source/restore times, tool/image versions/digest, reviewer, SHA-256 and every exception. Windows execution and this actual hosted restore are **not yet verified by the agent**. Preserve a second encrypted off-machine copy and prove its integrity as well; the disk's BitLocker recovery key and backup custody must be recoverable separately.

## Coverage and remaining recovery gates

This archive attempts all accessible database schema/data, including Auth rows/password hashes, Storage **metadata**, migration history, functions/RLS/ACLs and private application tables. The role export supplies role definitions/memberships without database login passwords. A successful native dump plus reviewed isolated restore can support the **database** backup gate; a schema-only export cannot.

It does **not** back up Storage object bytes, hosted Auth/SMTP/OAuth settings, platform JWT/encryption keys, Edge deployment artifacts/secrets, certificates/hosting, schedules, signing keys or operator configuration. Database row restoration alone does not prove Auth login, session continuity, document access or email delivery. Preserve the existing deployed request-trip source export and separately controlled `MAPBOX_SERVER_TOKEN` recovery reference. Complete the other components in [FULL_SERVICE_RECOVERY.md](FULL_SERVICE_RECOVERY.md), using the existing Storage byte companion and a distinct isolated target, before any full-service recovery claim. Do not restore historical push tokens into an active sender or enable notification/retention schedules during rehearsal.

This whole-database rehearsal restores Storage metadata too. The Storage companion's empty-target API restore must not be run against those populated metadata tables. Full-service recovery requires a reviewed compatible metadata/byte restoration plan, or a separate reviewed restore selection that excludes the relevant Storage row data for API recreation while preserving the original complete archive and ownership inventory. Do not wipe metadata or silently discard ownership information to get past a collision.

The next operator action is to cross-check **Connect → Session pooler → View parameters**, download the documented CA from Database settings, and run the capture procedure with the existing database password at its masked native prompts. Return only the stage result and sanitized failure if any; never send the password or backup bundle in chat.
