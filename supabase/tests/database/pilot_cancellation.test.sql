begin;
create extension if not exists pgtap with schema extensions;
select no_plan();
\ir ../support/open-pilot-fixture.sql
insert into auth.users(id, raw_user_meta_data)
select ('40000000-0000-0000-0000-' || lpad(n::text,12,'0'))::uuid,
  jsonb_build_object('full_name','Cancellation Test '||n,'phone_number','+26097400000'||n)
from generate_series(1,7) n;
update public.profiles set role='rider', approval_status='approved', is_online=true where id='40000000-0000-0000-0000-000000000002';
update public.profiles set role='admin' where id='40000000-0000-0000-0000-000000000004';
insert into private.pilot_rider_presence(rider_id,latitude,longitude) values('40000000-0000-0000-0000-000000000002',-15.3292,28.6820);
set local role service_role;
do $$ declare actor uuid; begin
  foreach actor in array array['40000000-0000-0000-0000-000000000001'::uuid,'40000000-0000-0000-0000-000000000005'::uuid,'40000000-0000-0000-0000-000000000006'::uuid,'40000000-0000-0000-0000-000000000007'::uuid] loop
    perform public.create_routed_trip(actor,-15.3292,28.6820,'Pickup',-15.33,28.69,'Destination',4000,600,'cash');
  end loop;
end $$;
reset role;
create temporary table offers as select id,passenger_id from public.trips;
grant select on offers to authenticated;
set local role authenticated;
select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000003',true);
select is(public.cancel_pilot_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000001'),'passenger_cancelled'),false,'unrelated passenger cannot cancel');

select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000002',true);
select ok(public.accept_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000001')),'rider accepts first trip');
select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000001',true);
select ok(public.cancel_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000001')),'passenger can cancel an accepted trip within two minutes');
reset role;
select is((select late_warning from public.pilot_cancellation_reviews where actor_id='40000000-0000-0000-0000-000000000001'),false,'free-window cancellation has no warning');
select is((select review_required from public.pilot_cancellation_reviews where actor_id='40000000-0000-0000-0000-000000000001'),false,'free-window cancellation needs no fee review');

set local role authenticated;
select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000002',true);
select ok(public.accept_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000005')),'rider accepts second trip');
reset role;
select set_config('request.jwt.claim.sub','',true);
update public.trips set accepted_at=now()-interval '3 minutes' where passenger_id='40000000-0000-0000-0000-000000000005';
set local role authenticated;
select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000005',true);
select ok(public.cancel_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000005')),'late passenger cancellation is permitted for manual review');
select is((select count(*) from public.pilot_cancellation_reviews),0::bigint,'passenger cannot read the operations review queue');
select throws_ok($$select public.admin_review_pilot_cancellation((select id from offers where passenger_id='40000000-0000-0000-0000-000000000005'))$$,'42501',null,'passenger cannot acknowledge their own warning');
reset role;
select is((select late_warning from public.pilot_cancellation_reviews where actor_id='40000000-0000-0000-0000-000000000005'),true,'late cancellation is flagged');
select is((select fare_amount from public.trips where passenger_id='40000000-0000-0000-0000-000000000005'),20::numeric,'warning never adds a cancellation charge to the trip');

set local role authenticated;
select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000002',true);
select ok(public.accept_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000006')),'rider accepts third trip');
select ok(public.mark_trip_arrived((select id from offers where passenger_id='40000000-0000-0000-0000-000000000006')),'rider marks arrival');
select throws_ok($$select public.cancel_pilot_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000006'),'passenger_no_show')$$,'22023',null,'no-show is rejected before five minutes');
reset role;
select set_config('request.jwt.claim.sub','',true);
update public.trips set arrived_at=now()-interval '6 minutes' where passenger_id='40000000-0000-0000-0000-000000000006';
set local role authenticated;
select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000002',true);
select ok(public.cancel_pilot_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000006'),'passenger_no_show'),'no-show is accepted after five minutes at pickup');
select is(public.cancel_pilot_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000006'),'passenger_no_show'),false,'repeated cancellation has no duplicate effect');
select ok(public.accept_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000007')),'rider accepts fourth trip');
select ok(public.cancel_pilot_trip((select id from offers where passenger_id='40000000-0000-0000-0000-000000000007'),'motorcycle_problem'),'rider can give an operational cancellation reason');
reset role;
select is((select count(*) from public.pilot_cancellation_reviews where review_required),2::bigint,'late passenger and other rider cancellation are queued for review');
set local role authenticated;
select set_config('request.jwt.claim.sub','40000000-0000-0000-0000-000000000004',true);
select ok(public.admin_review_pilot_cancellation((select id from offers where passenger_id='40000000-0000-0000-0000-000000000005')),'admin can record manual review');
select is(public.admin_review_pilot_cancellation((select id from offers where passenger_id='40000000-0000-0000-0000-000000000005')),false,'review acknowledgement is idempotent');
reset role;
select is((select reviewed_by from public.pilot_cancellation_reviews where actor_id='40000000-0000-0000-0000-000000000005'),'40000000-0000-0000-0000-000000000004'::uuid,'review records the responsible administrator');
select * from finish();
rollback;
