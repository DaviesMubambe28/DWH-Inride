begin;

-- Create a passenger profile for email/password sign-ups. Client-supplied metadata is
-- copied only into display fields; it is never used for authorization or role assignment.
create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  full_name_value text := btrim(coalesce(new.raw_user_meta_data ->> 'full_name', ''));
  phone_value text := btrim(coalesce(new.raw_user_meta_data ->> 'phone_number', ''));
begin
  if char_length(full_name_value) between 2 and 120
     and char_length(phone_value) between 7 and 32 then
    insert into public.profiles (id, role, full_name, phone_number)
    values (new.id, 'passenger', full_name_value, phone_value)
    on conflict (id) do nothing;
  end if;
  return new;
end;
$$;

revoke all on function private.handle_new_auth_user() from public, anon, authenticated;

drop trigger if exists create_profile_after_auth_signup on auth.users;
create trigger create_profile_after_auth_signup
after insert on auth.users
for each row execute function private.handle_new_auth_user();

-- Realtime publication is idempotent and still subject to each table's RLS policies.
do $$
begin
  alter publication supabase_realtime add table public.trips;
exception
  when duplicate_object then null;
end
$$;

do $$
begin
  alter publication supabase_realtime add table public.profiles;
exception
  when duplicate_object then null;
end
$$;

do $$
begin
  alter publication supabase_realtime add table public.rider_documents;
exception
  when duplicate_object then null;
end
$$;

commit;

