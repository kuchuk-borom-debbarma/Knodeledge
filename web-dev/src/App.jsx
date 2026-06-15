import { useEffect, useRef, useState } from 'react';
import { Network } from 'vis-network';
import {
  AlertCircle,
  ArrowLeft,
  Brain,
  Bug,
  ChevronRight,
  CircleUserRound,
  Database,
  LogOut,
  MessageSquareText,
  Network as NetworkIcon,
  Plus,
  RefreshCw,
  Send,
} from 'lucide-react';
import './App.css';

const KIND_COLORS = {
  ROOT: { background: '#4c1d95', border: '#c4b5fd' },
  TOPIC: { background: '#172554', border: '#60a5fa' },
  ENTITY: { background: '#064e3b', border: '#6ee7b7' },
  FACT: { background: '#78350f', border: '#fbbf24' },
  CONDITION: { background: '#881337', border: '#fb7185' },
};

const emptyLevel = {
  current: null,
  children: [],
  breadcrumbs: [],
  leaf: false,
  hierarchyVersion: 0,
};

function App() {
  const [currentUser, setCurrentUser] = useState(() => {
    const saved = localStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });
  const [authUsername, setAuthUsername] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [isRegistering, setIsRegistering] = useState(false);
  const [boundaries, setBoundaries] = useState([]);
  const [selectedBoundary, setSelectedBoundary] = useState(null);
  const [newBoundaryName, setNewBoundaryName] = useState('');
  const [newBoundaryDesc, setNewBoundaryDesc] = useState('');
  const [showCreateBoundary, setShowCreateBoundary] = useState(false);
  const [mode, setMode] = useState('ingest');
  const [noteText, setNoteText] = useState('');
  const [promptText, setPromptText] = useState('');
  const [promptAnswer, setPromptAnswer] = useState('');
  const [level, setLevel] = useState(emptyLevel);
  const [selectedNode, setSelectedNode] = useState(null);
  const [debugData, setDebugData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [levelLoading, setLevelLoading] = useState(false);
  const [status, setStatus] = useState(null);
  const canvasRef = useRef(null);

  useEffect(() => {
    if (!status) return undefined;
    const timeout = setTimeout(() => setStatus(null), 4000);
    return () => clearTimeout(timeout);
  }, [status]);

  const showStatus = (type, text) => setStatus({ type, text });

  const fetchBoundaries = async (userId) => {
    try {
      const response = await fetch(`/api/v1/context-boundary/user/${userId}`);
      if (!response.ok) throw new Error('Boundary request failed');
      const data = await response.json();
      setBoundaries(data);
      setSelectedBoundary((current) => {
        if (current && data.some((boundary) => boundary.id === current.id)) return current;
        return data[0] || null;
      });
    } catch (error) {
      console.error(error);
      showStatus('error', 'Failed to load boundaries');
    }
  };

  const fetchLevel = async (nodeId = null, fallbackToRoot = false) => {
    if (!currentUser || !selectedBoundary) return;
    setLevelLoading(true);
    try {
      const nodeQuery = nodeId ? `&nodeId=${encodeURIComponent(nodeId)}` : '';
      const response = await fetch(
        `/api/v1/hierarchy/${selectedBoundary.id}/level?userId=${currentUser.id}${nodeQuery}`
      );
      if (!response.ok) {
        if (fallbackToRoot && nodeId) {
          await fetchLevel(null, false);
          return;
        }
        throw new Error('Hierarchy level request failed');
      }
      const data = await response.json();
      setLevel(data);
      setSelectedNode(data.current);
    } catch (error) {
      console.error(error);
      showStatus('error', 'Failed to load hierarchy level');
    } finally {
      setLevelLoading(false);
    }
  };

  const fetchDebugHierarchy = async () => {
    if (!currentUser || !selectedBoundary) return;
    setLoading(true);
    try {
      const response = await fetch(
        `/api/v1/hierarchy/debug/${selectedBoundary.id}?userId=${currentUser.id}`
      );
      if (!response.ok) throw new Error('Debug hierarchy request failed');
      setDebugData(await response.json());
    } catch (error) {
      console.error(error);
      showStatus('error', 'Failed to load debug hierarchy');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (currentUser) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      fetchBoundaries(currentUser.id);
    } else {
      setBoundaries([]);
      setSelectedBoundary(null);
      setLevel(emptyLevel);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  useEffect(() => {
    // Boundary changes intentionally reset stale level state before async loading.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLevel(emptyLevel);
    setSelectedNode(null);
    setDebugData(null);
    if (selectedBoundary && currentUser) fetchLevel();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedBoundary]);

  useEffect(() => {
    if (!canvasRef.current || !level.current || mode === 'debug') return undefined;

    const radius = Math.max(220, Math.min(390, level.children.length * 48));
    const nodes = [
      {
        id: level.current.id,
        label: level.current.name,
        title: level.current.summary,
        x: 0,
        y: 0,
        fixed: true,
        size: 44,
        shape: 'dot',
        color: KIND_COLORS[level.current.kind] || KIND_COLORS.TOPIC,
        font: { color: '#fff', size: 18, face: 'Inter', bold: true },
        borderWidth: 3,
        shadow: { enabled: true, color: 'rgba(139,92,246,.6)', size: 22 },
      },
      ...level.children.map((child, index) => {
        const angle = (Math.PI * 2 * index) / level.children.length - Math.PI / 2;
        return {
          id: child.id,
          label: child.name,
          title: child.summary,
          x: Math.cos(angle) * radius,
          y: Math.sin(angle) * radius,
          fixed: true,
          size: child.kind === 'TOPIC' ? 34 : 28,
          shape: 'dot',
          color: KIND_COLORS[child.kind] || KIND_COLORS.TOPIC,
          font: {
            color: '#f8fafc',
            size: 14,
            face: 'Inter',
            bold: child.kind === 'TOPIC',
            vadjust: 4,
          },
          borderWidth: 2,
          shadow: { enabled: true, color: 'rgba(0,0,0,.55)', size: 10 },
        };
      }),
    ];
    const edges = level.children.map((child) => ({
      id: `${level.current.id}:${child.id}`,
      from: level.current.id,
      to: child.id,
      label: child.relationToParent || 'CONTAINS',
      arrows: { to: { enabled: true, scaleFactor: 0.65 } },
      color: { color: 'rgba(148,163,184,.5)', highlight: '#a78bfa' },
      font: {
        color: '#94a3b8',
        size: 10,
        face: 'Inter',
        background: 'rgba(9,11,18,.85)',
        strokeWidth: 0,
      },
      width: 1.5,
      smooth: { type: 'curvedCW', roundness: 0.12 },
    }));
    const network = new Network(
      canvasRef.current,
      { nodes, edges },
      {
        physics: false,
        interaction: {
          hover: true,
          tooltipDelay: 120,
          zoomView: true,
          dragView: true,
          navigationButtons: true,
        },
      }
    );
    network.fit({ animation: { duration: 350, easingFunction: 'easeInOutQuad' } });
    network.on('click', ({ nodes: clicked }) => {
      if (!clicked.length) return;
      const node = [level.current, ...level.children].find((item) => item.id === clicked[0]);
      setSelectedNode(node || null);
      if (node && node.id !== level.current.id) fetchLevel(node.id);
    });
    return () => network.destroy();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [level, mode]);

  const handleAuth = async (event) => {
    event.preventDefault();
    if (!authUsername.trim() || !authPassword) return;
    setLoading(true);
    try {
      const response = await fetch(
        isRegistering ? '/api/v1/auth/register' : '/api/v1/auth/login',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: authUsername, password: authPassword }),
        }
      );
      if (!response.ok) throw new Error(await response.text());
      if (isRegistering) {
        setIsRegistering(false);
        showStatus('success', 'Account created. Log in.');
      } else {
        const user = { id: await response.text(), username: authUsername };
        localStorage.setItem('user', JSON.stringify(user));
        setCurrentUser(user);
      }
      setAuthPassword('');
    } catch (error) {
      showStatus('error', error.message || 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateBoundary = async (event) => {
    event.preventDefault();
    if (!newBoundaryName.trim() || !newBoundaryDesc.trim()) return;
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/context-boundary/?userId=${currentUser.id}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newBoundaryName, context: newBoundaryDesc }),
      });
      if (!response.ok) throw new Error('Boundary creation failed');
      const boundary = await response.json();
      setBoundaries((current) => [...current, boundary]);
      setSelectedBoundary(boundary);
      setNewBoundaryName('');
      setNewBoundaryDesc('');
      setShowCreateBoundary(false);
    } catch (error) {
      console.error(error);
      showStatus('error', 'Failed to create boundary');
    } finally {
      setLoading(false);
    }
  };

  const handleIngest = async (event) => {
    event.preventDefault();
    if (!noteText.trim() || !selectedBoundary) return;
    setLoading(true);
    try {
      const response = await fetch('/api/v1/aiService/ingest', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          note: noteText,
          contextBoundaryId: selectedBoundary.id,
          actorId: currentUser.id,
        }),
      });
      if (!response.ok) throw new Error('Ingestion failed');
      setNoteText('');
      await fetchLevel(level.current?.id, true);
      showStatus('success', 'Hierarchy updated');
    } catch (error) {
      console.error(error);
      showStatus('error', 'Failed to ingest note');
    } finally {
      setLoading(false);
    }
  };

  const handlePrompt = async (event) => {
    event.preventDefault();
    if (!promptText.trim() || !selectedBoundary) return;
    setLoading(true);
    setPromptAnswer('');
    try {
      const response = await fetch('/api/v1/aiService/prompt', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: promptText,
          contextBoundaryId: selectedBoundary.id,
          actorId: currentUser.id,
        }),
      });
      if (!response.ok) throw new Error('Prompt failed');
      const data = await response.json();
      setPromptAnswer(data.answer);
    } catch (error) {
      console.error(error);
      showStatus('error', 'Failed to answer prompt');
    } finally {
      setLoading(false);
    }
  };

  if (!currentUser) {
    return (
      <div className="auth-shell">
        <form className="auth-card glass-panel" onSubmit={handleAuth}>
          <Brain size={48} />
          <h1>Knodeledge</h1>
          <p>Recursive knowledge, one useful level at a time.</p>
          <input
            value={authUsername}
            onChange={(event) => setAuthUsername(event.target.value)}
            placeholder="Username"
          />
          <input
            type="password"
            value={authPassword}
            onChange={(event) => setAuthPassword(event.target.value)}
            placeholder="Password"
          />
          <button className="primary" disabled={loading}>
            {loading ? <RefreshCw className="spin" size={16} /> : <CircleUserRound size={16} />}
            {isRegistering ? 'Create account' : 'Log in'}
          </button>
          <button
            className="link-button"
            type="button"
            onClick={() => setIsRegistering((value) => !value)}
          >
            {isRegistering ? 'Use existing account' : 'Create new account'}
          </button>
        </form>
        {status && <Status status={status} />}
      </div>
    );
  }

  const previousBreadcrumb =
    level.breadcrumbs.length > 1 ? level.breadcrumbs[level.breadcrumbs.length - 2] : null;

  return (
    <div className="app-shell">
      <header className="topbar glass-panel">
        <div className="brand">
          <Brain size={28} />
          <div>
            <strong>Knodeledge</strong>
            <span>Hierarchy Explorer</span>
          </div>
        </div>
        <div className="user-actions">
          <span>{currentUser.username}</span>
          <button
            className="icon-button"
            title="Log out"
            onClick={() => {
              localStorage.removeItem('user');
              setCurrentUser(null);
            }}
          >
            <LogOut size={17} />
          </button>
        </div>
      </header>

      <aside className="sidebar">
        <section className="panel glass-panel">
          <div className="panel-heading">
            <div>
              <span>Boundary</span>
              <strong>{selectedBoundary?.name || 'None selected'}</strong>
            </div>
            <button className="icon-button" onClick={() => setShowCreateBoundary((value) => !value)}>
              <Plus size={16} />
            </button>
          </div>
          {showCreateBoundary ? (
            <form className="stack" onSubmit={handleCreateBoundary}>
              <input
                value={newBoundaryName}
                onChange={(event) => setNewBoundaryName(event.target.value)}
                placeholder="Subject name, e.g. Kuku"
              />
              <textarea
                value={newBoundaryDesc}
                onChange={(event) => setNewBoundaryDesc(event.target.value)}
                placeholder="Boundary context"
                rows={3}
              />
              <button className="primary" disabled={loading}>
                Create boundary
              </button>
            </form>
          ) : (
            <div className="boundary-list">
              {boundaries.map((boundary) => (
                <button
                  key={boundary.id}
                  className={selectedBoundary?.id === boundary.id ? 'active' : ''}
                  onClick={() => setSelectedBoundary(boundary)}
                >
                  <strong>{boundary.name}</strong>
                  <span>{boundary.context}</span>
                </button>
              ))}
              {!boundaries.length && <p>No boundaries yet.</p>}
            </div>
          )}
        </section>

        <section className="panel glass-panel workspace-panel">
          <div className="mode-tabs">
            <button className={mode === 'ingest' ? 'active' : ''} onClick={() => setMode('ingest')}>
              <NetworkIcon size={15} /> Ingest
            </button>
            <button className={mode === 'prompt' ? 'active' : ''} onClick={() => setMode('prompt')}>
              <MessageSquareText size={15} /> Ask
            </button>
            <button
              className={mode === 'debug' ? 'active' : ''}
              onClick={() => {
                setMode('debug');
                fetchDebugHierarchy();
              }}
            >
              <Bug size={15} /> Debug
            </button>
          </div>

          {mode === 'ingest' && (
            <form className="stack workspace-form" onSubmit={handleIngest}>
              <textarea
                value={noteText}
                onChange={(event) => setNoteText(event.target.value)}
                placeholder="Add knowledge about this subject..."
                rows={8}
                disabled={!selectedBoundary || loading}
              />
              <button className="primary" disabled={!noteText.trim() || loading}>
                {loading ? <RefreshCw className="spin" size={16} /> : <Send size={16} />}
                Ingest note
              </button>
            </form>
          )}

          {mode === 'prompt' && (
            <form className="stack workspace-form" onSubmit={handlePrompt}>
              <textarea
                value={promptText}
                onChange={(event) => setPromptText(event.target.value)}
                placeholder="Ask the hierarchy..."
                rows={5}
                disabled={!selectedBoundary || loading}
              />
              {promptAnswer && <div className="answer-card">{promptAnswer}</div>}
              <button className="primary" disabled={!promptText.trim() || loading}>
                {loading ? <RefreshCw className="spin" size={16} /> : <Send size={16} />}
                Ask
              </button>
            </form>
          )}

          {mode === 'debug' && (
            <div className="stack workspace-form">
              <p>Full hierarchy export. Debug only.</p>
              <button className="secondary" onClick={fetchDebugHierarchy} disabled={loading}>
                <RefreshCw className={loading ? 'spin' : ''} size={16} />
                Refresh export
              </button>
            </div>
          )}
        </section>
      </aside>

      <main className="workspace">
        {mode === 'debug' ? (
          <section className="debug-view glass-panel">
            <div className="debug-header">
              <div>
                <span>Authoritative hierarchy</span>
                <h2>{selectedBoundary?.name || 'No boundary'}</h2>
              </div>
              <div className="version-badge">v{debugData?.version || 0}</div>
            </div>
            <pre>{debugData ? JSON.stringify(debugData, null, 2) : 'No debug data loaded.'}</pre>
          </section>
        ) : !selectedBoundary ? (
          <EmptyState text="Create or select a boundary." />
        ) : !level.current ? (
          <EmptyState text={levelLoading ? 'Loading hierarchy...' : 'No hierarchy level found.'} />
        ) : (
          <>
            <div className="explorer-header">
              <div className="breadcrumbs">
                {level.breadcrumbs.map((node, index) => (
                  <span key={node.id}>
                    {index > 0 && <ChevronRight size={14} />}
                    <button onClick={() => fetchLevel(node.id)}>{node.name}</button>
                  </span>
                ))}
              </div>
              <div className="explorer-actions">
                <span>v{level.hierarchyVersion}</span>
                <button
                  className="secondary"
                  disabled={!previousBreadcrumb || levelLoading}
                  onClick={() => previousBreadcrumb && fetchLevel(previousBreadcrumb.id)}
                >
                  <ArrowLeft size={15} /> Back
                </button>
                <button
                  className="icon-button"
                  onClick={() => fetchLevel(level.current.id, true)}
                  title="Refresh level"
                >
                  <RefreshCw className={levelLoading ? 'spin' : ''} size={16} />
                </button>
              </div>
            </div>

            <div className={`explorer-stage ${levelLoading ? 'loading' : ''}`}>
              <div ref={canvasRef} className="hierarchy-canvas" />
              {level.leaf && (
                <div className="leaf-card glass-panel">
                  <span>{level.current.kind}</span>
                  <h3>{level.current.name}</h3>
                  {level.current.statement && <strong>{level.current.statement}</strong>}
                  <p>{level.current.summary}</p>
                </div>
              )}
              {selectedNode && !(level.leaf && selectedNode.id === level.current.id) && (
                <aside className="node-inspector glass-panel">
                  <div className="kind-line">
                    <i className={`kind-dot ${selectedNode.kind.toLowerCase()}`} />
                    {selectedNode.kind}
                  </div>
                  <h3>{selectedNode.name}</h3>
                  {selectedNode.relationToParent && (
                    <code>{selectedNode.relationToParent}</code>
                  )}
                  {selectedNode.statement && <strong>{selectedNode.statement}</strong>}
                  <p>{selectedNode.summary}</p>
                  {selectedNode.childCount > 0 && (
                    <span>{selectedNode.childCount} child nodes</span>
                  )}
                </aside>
              )}
            </div>
          </>
        )}
      </main>

      {status && <Status status={status} />}
    </div>
  );
}

function Status({ status }) {
  return (
    <div className={`status-toast ${status.type}`}>
      {status.type === 'error' ? <AlertCircle size={17} /> : <Database size={17} />}
      {status.text}
    </div>
  );
}

function EmptyState({ text }) {
  return (
    <div className="empty-state">
      <Brain size={58} />
      <h2>Hierarchy Explorer</h2>
      <p>{text}</p>
    </div>
  );
}

export default App;
