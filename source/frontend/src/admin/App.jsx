import LoginLayout from './layout/LoginLayout.jsx';
import WorkspaceLayout from './layout/WorkspaceLayout.jsx';
import { useAdminWorkspace } from './hooks/useAdminWorkspace.js';

export default function App() {
  useAdminWorkspace();

  return (
    <>
      <LoginLayout />
      <WorkspaceLayout />
    </>
  );
}
