type TripPresentation = { label: string; tone: 'orange' | 'blue' | 'green' | 'slate' | 'red'; nextAction: string };

const stages: Record<string, TripPresentation> = {
  requesting: { label: 'Awaiting rider', tone: 'orange', nextAction: 'An available rider needs to accept this request.' },
  accepted: { label: 'Rider en route', tone: 'blue', nextAction: 'Rider travels to the pickup point.' },
  arrived: { label: 'At pickup', tone: 'blue', nextAction: 'Passenger and rider verify the trip PIN.' },
  in_progress: { label: 'In progress', tone: 'green', nextAction: 'Rider completes the trip at the destination.' },
  completed: { label: 'Completed', tone: 'slate', nextAction: 'Check payment acknowledgements in Finance.' },
  cancelled: { label: 'Cancelled', tone: 'red', nextAction: 'No further dispatch action is needed.' },
};

export function tripPresentation(status: string): TripPresentation {
  return Object.hasOwn(stages, status) ? stages[status] : { label: 'Unknown status', tone: 'slate', nextAction: 'Refresh and check the trip record before taking action.' };
}

export function matchesTripFilter(status: string, filter: string): boolean {
  if (filter === 'all') return true;
  if (filter === 'active') return ['requesting', 'accepted', 'arrived', 'in_progress'].includes(status);
  return status === filter;
}
