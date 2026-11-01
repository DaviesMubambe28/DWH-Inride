begin;
create extension if not exists pgtap with schema extensions;
select plan(8);
insert into auth.users(id,raw_user_meta_data) values
 ('66000000-0000-0000-0000-000000000001','{"full_name":"Retention Passenger","phone_number":"+260976600001"}'),
 ('66000000-0000-0000-0000-000000000002','{"full_name":"Retention Rider","phone_number":"+260976600002"}');
update public.profiles set role='rider',approval_status='approved' where id='66000000-0000-0000-0000-000000000002';
update private.pilot_service_settings set enabled=true,opens='00:00',closes='23:59:59.999999',weekdays=array[1,2,3,4,5,6,7],center_latitude=-15.3292,center_longitude=28.6821,radius_km=100,fare_policy_approved=true,cancellation_policy_approved=true;
insert into public.trips(id,passenger_id,rider_id,status,pickup_lat,pickup_lng,pickup_address,dropoff_lat,dropoff_lng,dropoff_address,route_distance_meters,route_duration_seconds,created_at,started_at,completed_at,retention_ended_at)
select ('65000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,'66000000-0000-0000-0000-000000000001','66000000-0000-0000-0000-000000000002',case when n=3 then 'cancelled'::public.trip_status else 'completed'::public.trip_status end,-15.3292,28.6821,'Historical fixture',-15.33,28.69,'Historical fixture',4000,600,now()-interval '2 years',now()-interval '2 years',case when n=3 then null else now()-interval '2 years'+interval '10 minutes' end,now()-interval '2 years'
from generate_series(1,3)n;
insert into public.trip_locations(trip_id,rider_id,lat,lng,recorded_at) select id,rider_id,-15.3292,28.6821,now()-interval '2 years' from public.trips;
insert into public.pilot_cancellation_reviews(trip_id,actor_id,reason,review_required) values ('65000000-0000-0000-0000-000000000003','66000000-0000-0000-0000-000000000001','passenger_cancelled',true);
select is(public.run_retention_batch()->>'approved','false','unapproved policy performs no purge');
update private.retention_policy set approved=true,trip_retention=interval '3 years',location_retention=interval '1 year';
insert into private.data_holds(user_id,reason) values ('66000000-0000-0000-0000-000000000002','legal');
select is((public.run_retention_batch()->>'locations_deleted')::int,0,'hold protects expired GPS records');
delete from private.data_holds;
select is((public.run_retention_batch()->>'locations_deleted')::int,2,'approved expired locations are removed while unresolved review retains its GPS');
select is((select count(*)::int from public.trips),3,'longer financial retention preserves trip records');
update private.retention_policy set trip_retention=interval '1 year';
select is((public.run_retention_batch()->>'trips_deleted')::int,2,'eligible expired trips are actually removed');
select is((select count(*)::int from public.trips),1,'unresolved cancellation review prevents retirement');
update public.pilot_cancellation_reviews set reviewed_at=now(),reviewed_by='66000000-0000-0000-0000-000000000002';
select is((public.run_retention_batch()->>'trips_deleted')::int,1,'resolved expired trip and review can be removed');
select is((select count(*)::int from public.pilot_cancellation_reviews),0,'dependent review removed without FK failure');
select * from finish();
rollback;
