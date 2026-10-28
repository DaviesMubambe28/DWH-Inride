import test from 'node:test';
import assert from 'node:assert/strict';
import { tripPresentation, matchesTripFilter } from '../lib/trip-presentation.ts';

test('each trip stage has a human label and useful next step', () => {
  for (const status of ['requesting', 'accepted', 'arrived', 'in_progress', 'completed', 'cancelled']) {
    const state = tripPresentation(status);
    assert.ok(state.label.length > 0);
    assert.ok(state.nextAction.length > 0);
  }
  assert.equal(tripPresentation('requesting').label, 'Awaiting rider');
  assert.equal(tripPresentation('in_progress').label, 'In progress');
  assert.equal(tripPresentation('unexpected').label, 'Unknown status');
  assert.equal(tripPresentation('__proto__').label, 'Unknown status');
});

test('active filter excludes terminal trips and status filters stay exact', () => {
  assert.equal(matchesTripFilter('completed', 'active'), false);
  assert.equal(matchesTripFilter('cancelled', 'active'), false);
  assert.equal(matchesTripFilter('requesting', 'active'), true);
  assert.equal(matchesTripFilter('arrived', 'requesting'), false);
  assert.equal(matchesTripFilter('completed', 'all'), true);
});
