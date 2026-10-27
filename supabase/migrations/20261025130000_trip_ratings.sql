-- Server-authoritative passenger ratings. Clients call submit_trip_rating();
-- direct table writes are intentionally not granted to authenticated users.
create table if not exists public.trip_ratings (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references public.trips (id) on delete cascade,
  rater_id uuid not null references public.profiles (id) on delete cascade,
  rated_user_id uuid not null references public.profiles (id) on delete cascade,
  rating_value smallint not null,
  comment text,
  created_at timestamptz not null default now(),
  constraint trip_ratings_value_range check (rating_value between 1 and 5),
  constraint trip_ratings_comment_length check (comment is null or char_length(comment) <= 1000),
  constraint trip_ratings_trip_rater_unique unique (trip_id, rater_id)
);

create index if not exists trip_ratings_rated_user_created_idx
  on public.trip_ratings (rated_user_id, created_at desc);
create index if not exists trip_ratings_trip_idx
  on public.trip_ratings (trip_id);

alter table public.trip_ratings enable row level security;
revoke all on public.trip_ratings from public, anon, authenticated;
grant select on public.trip_ratings to authenticated;

drop policy if exists trip_ratings_select_relevant_or_admin on public.trip_ratings;
create policy trip_ratings_select_relevant_or_admin
on public.trip_ratings for select to authenticated
using (
  rater_id = (select auth.uid())
  or rated_user_id = (select auth.uid())
  or (select private.is_admin())
);

create or replace function public.submit_trip_rating(
  p_trip_id uuid,
  p_rating_value smallint,
  p_comment text default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
  rider uuid;
  rating_id uuid;
  normalized_comment text := nullif(btrim(p_comment), '');
begin
  if actor is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  if p_rating_value is null or p_rating_value not between 1 and 5 then
    raise exception 'rating must be between 1 and 5' using errcode = '22023';
  end if;
  if normalized_comment is not null and char_length(normalized_comment) > 1000 then
    raise exception 'rating comment is too long' using errcode = '22023';
  end if;

  select t.rider_id into rider
  from public.trips t
  where t.id = p_trip_id
    and t.passenger_id = actor
    and t.status = 'completed'::public.trip_status;

  if rider is null then
    raise exception 'only the passenger can rate a completed trip' using errcode = '42501';
  end if;

  insert into public.trip_ratings (trip_id, rater_id, rated_user_id, rating_value, comment)
  values (p_trip_id, actor, rider, p_rating_value, normalized_comment)
  returning id into rating_id;

  perform set_config('app.profile_write_authorized', 'true', true);
  update public.profiles p
  set rating = (
    select round(avg(r.rating_value)::numeric, 2)
    from public.trip_ratings r
    where r.rated_user_id = rider
  )
  where p.id = rider;

  return rating_id;
exception
  when unique_violation then
    raise exception 'this trip has already been rated' using errcode = '23505';
end;
$$;

revoke all on function public.submit_trip_rating(uuid, smallint, text) from public, anon;
grant execute on function public.submit_trip_rating(uuid, smallint, text) to authenticated;
