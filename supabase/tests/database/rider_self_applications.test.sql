begin;
create extension if not exists pgtap with schema extensions;
select no_plan();

-- Isolated fixture policy. The transaction rolls this back and public bookings
-- remain disabled outside the test.
update private.pilot_service_settings set enabled=true, opens='00:00', closes='23:59:59.999999',
  weekdays=array[1,2,3,4,5,6,7], center_latitude=-15.3292, center_longitude=28.6821,
  radius_km=100, support_phone='+260979000001', support_email='fixture@test.invalid',
  fare_policy_approved=true, cancellation_policy_approved=true;

insert into auth.users(id, raw_user_meta_data) values
('71000000-0000-0000-0000-000000000001', '{"full_name":"Rider Applicant One","phone_number":"+260977100001","account_intent":"rider","motorcycle_make":"Honda","motorcycle_model":"Ace","motorcycle_year":"2024","motorcycle_plate":" chg-7101 ","motorcycle_color":"Blue","driving_licence_number":" dl-7101 "}'),
('71000000-0000-0000-0000-000000000002', '{"full_name":"Ordinary Passenger","phone_number":"+260977100002"}'),
('71000000-0000-0000-0000-000000000003', '{"full_name":"Application Admin","phone_number":"+260977100003"}'),
('71000000-0000-0000-0000-000000000004', '{"full_name":"Rider Applicant Two","phone_number":"+260977100004","account_intent":"rider","motorcycle_make":"Yamaha","motorcycle_model":"YBR","motorcycle_year":"2023","motorcycle_plate":"CHG-7104","motorcycle_color":"Red","driving_licence_number":"DL-7104"}'),
('71000000-0000-0000-0000-000000000005', '{"full_name":"Active Trip Applicant","phone_number":"+260977100005","account_intent":"rider","motorcycle_make":"Honda","motorcycle_model":"CG","motorcycle_year":"2022","motorcycle_plate":"CHG-7105","motorcycle_color":"Black","driving_licence_number":"DL-7105"}');
update public.profiles set role='admin' where id='71000000-0000-0000-0000-000000000003';

select is((select role::text from public.profiles where id='71000000-0000-0000-0000-000000000001'), 'passenger', 'rider signup never self-promotes the profile');
select is((select status::text from public.rider_applications where applicant_id='71000000-0000-0000-0000-000000000001'), 'pending', 'rider signup creates a pending application');
select is((select motorcycle_plate from public.rider_applications where applicant_id='71000000-0000-0000-0000-000000000001'), 'CHG-7101', 'application identifiers are normalized');

set local role authenticated;
select set_config('request.jwt.claim.sub','71000000-0000-0000-0000-000000000002',true);
select is((select count(*) from public.rider_applications), 0::bigint, 'passengers cannot read rider applications');
select throws_ok($$insert into public.rider_applications(applicant_id,motorcycle_make,motorcycle_model,motorcycle_year,motorcycle_plate,motorcycle_color,driving_licence_number) values('71000000-0000-0000-0000-000000000002','Make','Model',2024,'CHG-X','Black','DL-X')$$, '42501', null, 'clients cannot insert an application directly');
select throws_ok($$select public.admin_review_rider_application('71000000-0000-0000-0000-000000000001','approved',null)$$, '42501', null, 'non-admin cannot approve a rider application');

select set_config('request.jwt.claim.sub','71000000-0000-0000-0000-000000000001',true);
select is((select count(*) from public.rider_applications), 1::bigint, 'applicant can read only their application');
select throws_ok($$select public.apply_as_driver()$$, '42501', null, 'pending application cannot promote itself');
select ok(private.is_rider_applicant(), 'pending applicant qualifies only for private document submission');
insert into storage.objects(bucket_id,name,owner,metadata)
values('rider-documents','71000000-0000-0000-0000-000000000001/license.pdf',(select auth.uid()),'{"mimetype":"application/pdf"}');
insert into public.rider_documents(rider_id,document_type,storage_url)
values('71000000-0000-0000-0000-000000000001','license','71000000-0000-0000-0000-000000000001/license.pdf');
select is((select count(*) from public.rider_documents), 1::bigint, 'pending applicant can record their own pending document');
select is((select count(*) from storage.objects where bucket_id='rider-documents'), 1::bigint, 'pending applicant can create an object only in their private folder');
select throws_ok($$update public.rider_documents set verification_status='approved' where rider_id='71000000-0000-0000-0000-000000000001'$$, '42501', null, 'applicant cannot verify their own document');
select throws_ok($$insert into storage.objects(bucket_id,name,owner) values('rider-documents','71000000-0000-0000-0000-000000000002/nrc.jpg',(select auth.uid()))$$, '42501', null, 'applicant cannot write another user folder');

select set_config('request.jwt.claim.sub','71000000-0000-0000-0000-000000000002',true);
select is((select count(*) from public.rider_documents), 0::bigint, 'unrelated passenger cannot read applicant document rows');
select is((select count(*) from storage.objects where bucket_id='rider-documents'), 0::bigint, 'unrelated passenger cannot read applicant objects');

select set_config('request.jwt.claim.sub','71000000-0000-0000-0000-000000000004',true);
insert into storage.objects(bucket_id,name,owner)
values('rider-documents','71000000-0000-0000-0000-000000000004/nrc.jpg',(select auth.uid()));
insert into public.rider_documents(rider_id,document_type,storage_url)
values('71000000-0000-0000-0000-000000000004','nrc','71000000-0000-0000-0000-000000000004/nrc.jpg');

select set_config('request.jwt.claim.sub','71000000-0000-0000-0000-000000000003',true);
select throws_ok($$select public.admin_review_rider_application('71000000-0000-0000-0000-000000000001',null,null)$$, '22023', null, 'admin must provide an explicit review decision');
reset role;
insert into public.trips(passenger_id,pickup_lat,pickup_lng,pickup_address,dropoff_lat,dropoff_lng,dropoff_address,
  route_distance_meters,route_duration_seconds,payment_method)
values('71000000-0000-0000-0000-000000000005',-15.3292,28.6820,'Synthetic pickup',-15.3300,28.6900,
  'Synthetic destination',1000,300,'cash');
set local role authenticated;
select set_config('request.jwt.claim.sub','71000000-0000-0000-0000-000000000003',true);
select throws_ok($$select public.admin_review_rider_application('71000000-0000-0000-0000-000000000005','approved',null)$$, '22023', null, 'application approval cannot race past an active passenger trip');
select ok(
  position('for update' in lower(pg_get_functiondef('public.admin_provision_pilot_rider(uuid,text,text,integer,text,text)'::regprocedure)))
    < position('if exists' in lower(pg_get_functiondef('public.admin_provision_pilot_rider(uuid,text,text,integer,text,text)'::regprocedure))),
  'provisioning locks the passenger profile before checking active trips'
);
select is(public.admin_review_rider_application('71000000-0000-0000-0000-000000000001','approved','Identity reviewed'), 'approved'::public.verification_status, 'admin can approve and provision the application');
select is((select role::text from public.profiles where id='71000000-0000-0000-0000-000000000001'), 'rider', 'approval assigns rider role through the trusted provisioning function');
select is((select approval_status::text from public.profiles where id='71000000-0000-0000-0000-000000000001'), 'pending', 'application approval does not bypass in-person activation');
select is((select count(*) from public.motorcycles where rider_id='71000000-0000-0000-0000-000000000001' and plate_number='CHG-7101'), 1::bigint, 'approved motorcycle is provisioned atomically');
select throws_ok($$select public.admin_review_rider_application('71000000-0000-0000-0000-000000000001','approved',null)$$, '22023', null, 'approved application cannot be provisioned twice');
select throws_ok($$select public.admin_review_rider_application('71000000-0000-0000-0000-000000000004','rejected','  ')$$, '22023', null, 'rejection requires an audit reason at the RPC boundary');
select is(public.admin_review_rider_application('71000000-0000-0000-0000-000000000004','rejected','Licence could not be verified'), 'rejected'::public.verification_status, 'admin can reject an application with an audit note');
select is((select role::text from public.profiles where id='71000000-0000-0000-0000-000000000004'), 'passenger', 'rejection does not grant rider access');
select is(has_function_privilege('anon','public.admin_review_rider_application(uuid,public.verification_status,text)','EXECUTE'), false, 'anonymous application review is denied');

select set_config('request.jwt.claim.sub','71000000-0000-0000-0000-000000000004',true);
select is((select count(*) from public.rider_documents), 0::bigint, 'rejected applicant loses access to submitted document rows');
select is((select count(*) from storage.objects where bucket_id='rider-documents'), 0::bigint, 'rejected applicant loses access to submitted objects');

reset role;
insert into private.account_closures(user_id,erase_after)
values('71000000-0000-0000-0000-000000000004',now()+interval '1 year 1 day');
update private.account_closures set state='anonymized' where user_id='71000000-0000-0000-0000-000000000004';
select is((select count(*) from public.rider_applications where applicant_id='71000000-0000-0000-0000-000000000004'), 0::bigint, 'account anonymization removes the rider application');
select * from finish();
rollback;
