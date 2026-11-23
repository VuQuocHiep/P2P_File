export function createAdminState() {
  return {
    current: 'overview',
    data: null,
    signedIn: false,
    malware: { scanningFiles: new Set(), scanErrors: new Map() },
  };
}
