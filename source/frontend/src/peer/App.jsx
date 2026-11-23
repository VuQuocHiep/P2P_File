import LoginLayout from './layout/LoginLayout.jsx';
import WorkspaceLayout from './layout/WorkspaceLayout.jsx';
import UploadDialog from './components/dialogs/UploadDialog.jsx';
import SourcesDialog from './components/dialogs/SourcesDialog.jsx';
import BandwidthDialog from './components/dialogs/BandwidthDialog.jsx';
import { usePeerWorkspace } from './hooks/usePeerWorkspace.js';

export default function App() {
  usePeerWorkspace();

  return (
    <>
      <LoginLayout />
      <WorkspaceLayout />
      <UploadDialog />
      <SourcesDialog />
      <BandwidthDialog />
    </>
  );
}
