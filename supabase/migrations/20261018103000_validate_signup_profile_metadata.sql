begin;

-- Profiles and its existing app_role enum are created by earlier project migrations.
-- Keep the deployed schema and existing policy set; this migration only ensures RLS
-- remains enabled and repairs profile creation for new Auth users.
alter table public.profiles enable row level security;

-- Ensure every email signup has a profile. The supplied role metadata is deliberately
-- ignored for authorization: newly self-registered accounts always start as passengers.
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
  if char_length(full_name_value) not between 2 and 120 then
    raise exception using
      errcode = '23514',
      message = 'Signup requires a full name between 2 and 120 characters.';
  end if;

  if char_length(phone_value) not between 7 and 32 then
    raise exception using
      errcode = '23514',
      message = 'Signup requires a phone number between 7 and 32 characters.';
  end if;

  insert into public.profiles (id, role, full_name, phone_number)
  values (new.id, 'passenger', full_name_value, phone_value)
  on conflict (id) do nothing;

  return new;
end;
$$;

revoke all on function private.handle_new_auth_user() from public, anon, authenticated;

drop trigger if exists create_profile_after_auth_signup on auth.users;
create trigger create_profile_after_auth_signup
after insert on auth.users
for each row execute function private.handle_new_auth_user();

commit;
