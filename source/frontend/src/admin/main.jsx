import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import '../shared/theme.scss';
import './styles/malware.scss';

createRoot(document.getElementById('root')).render(<App />);
