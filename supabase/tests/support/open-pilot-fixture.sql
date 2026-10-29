-- Isolated test transaction only. Never use these values as a real pilot policy.
update private.pilot_service_settings set enabled = true, opens = '00:00', closes = '23:59:59.999999',
  weekdays = array[1,2,3,4,5,6,7], center_latitude = -15.3292, center_longitude = 28.6821,
  radius_km = 100, support_phone = '+260979000001', support_email = 'fixture@test.invalid',
  fare_policy_approved = true, cancellation_policy_approved = true;
