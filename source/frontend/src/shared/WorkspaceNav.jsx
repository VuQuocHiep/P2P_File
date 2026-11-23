export default function WorkspaceNav({ label, tabs }) {
  return (
    <nav className="nav" aria-label={label}>
      {tabs.map(({ id, icon, label }, index) => (
        <button key={id} data-tab={id} className={index === 0 ? 'active' : undefined}>
          <span className="icon">{icon}</span>
          {label}
        </button>
      ))}
    </nav>
  );
}
