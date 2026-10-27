begin;

create table if not exists public.device_push_tokens (
  fcm_token text primary key,
  user_id uuid not null references public.profiles(id) on delete cascade,
  app_id text not null check (app_id in (
    'zm.co.dhwinride.passenger',
    'zm.co.dhwinride.rider'
  )),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint device_push_tokens_token_length check (char_length(fcm_token) between 20 and 4096)
);

create index if not exists device_push_tokens_user_app_idx
  on public.device_push_tokens (user_id, app_id);

alter table public.device_push_tokens enable row level security;
revoke all on public.device_push_tokens from public, anon, authenticated;
grant all on public.device_push_tokens to service_role;

create or replace function public.register_my_fcm_token(
  p_fcm_token text,
  p_app_id text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
begin
  if actor is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  if p_fcm_token is null or char_length(p_fcm_token) not between 20 and 4096 then
    raise exception 'invalid FCM token' using errcode = '22023';
  end if;
  if p_app_id is null or p_app_id not in ('zm.co.dhwinride.passenger', 'zm.co.dhwinride.rider') then
    raise exception 'invalid application id' using errcode = '22023';
  end if;

  insert into public.device_push_tokens (fcm_token, user_id, app_id, updated_at)
  values (p_fcm_token, actor, p_app_id, now())
  on conflict (fcm_token) do update
    set user_id = excluded.user_id,
        app_id = excluded.app_id,
        updated_at = now();

  return true;
end;
$$;

create or replace function public.unregister_my_fcm_token(p_fcm_token text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
begin
  if actor is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;

  delete from public.device_push_tokens
  where fcm_token = p_fcm_token
    and user_id = actor;

  return found;
end;
$$;

revoke all on function public.register_my_fcm_token(text, text) from public, anon;
revoke all on function public.unregister_my_fcm_token(text) from public, anon;
grant execute on function public.register_my_fcm_token(text, text) to authenticated;
grant execute on function public.unregister_my_fcm_token(text) to authenticated;

commit;
