export function createPeerState() {
  return {
    mode: 'login',
    tab: 'explore',
    snapshot: null,
    catalog: [],
    query: '',
    rooms: [],
    roomId: '',
    roomFiles: {},
    roomRequests: {},
    roomMembers: {},
    lab: { selected: '', busy: false },
  };
}

export function applyState(model, next) {
  model.snapshot = next;
  if (Array.isArray(next.catalog))
    model.catalog = next.catalog.filter((file) =>
      file.manifest.name.toLocaleLowerCase().includes(model.query.toLocaleLowerCase()),
    );
}

export function ready(state) {
  return state?.user?.status === 'APPROVED' && state?.peerStatus === 'APPROVED';
}
