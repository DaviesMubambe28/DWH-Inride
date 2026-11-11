begin;
create extension if not exists pgtap with schema extensions;
select plan(12);

select has_column('private', 'pilot_service_settings', 'service_area_label', 'service area has a human label');
select has_column('private', 'pilot_service_settings', 'service_area_polygon', 'service area stores a district polygon');
select has_function(
  'private', 'point_in_polygon', array['double precision', 'double precision', 'jsonb'],
  'polygon inclusion helper exists'
);
select is(
  (select service_area_label from private.pilot_service_settings where singleton),
  'Chongwe District',
  'the configured operating area is Chongwe District'
);
select ok(
  (select jsonb_array_length(service_area_polygon) >= 100 from private.pilot_service_settings where singleton),
  'the stored boundary is detailed enough to avoid a broad district circle'
);

select is(private.pilot_point_allowed(-15.327476, 28.681337), true, 'Chongwe town and hospital area is allowed');
select is(private.pilot_point_allowed(-15.4473, 28.7732), true, 'district point beyond the old four kilometre circle is allowed');
select is(private.pilot_point_allowed(-15.3373, 28.4432), true, 'western Chongwe District point is allowed');
select is(private.pilot_point_allowed(-15.4167, 28.2833), false, 'Lusaka city point remains outside Chongwe District');
select is(private.pilot_point_allowed(-15.3000, 29.1000), false, 'point east of the district remains outside');
select is(private.pilot_point_allowed(91, 28.6820), false, 'invalid latitude remains denied');

update private.pilot_service_settings set service_area_polygon = null where singleton;
select is(private.pilot_point_allowed(-15.4473, 28.7732), false, 'the legacy circle remains a fail-closed fallback');

select * from finish();
rollback;
