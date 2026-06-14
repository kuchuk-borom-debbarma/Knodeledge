import React, { useState, useEffect, useRef } from 'react';
import { Network } from 'vis-network';
import { 
  Brain, User, Plus, Send, RefreshCw, AlertCircle, 
  LogOut, Database, Network as NetworkIcon, Eye, Info
} from 'lucide-react';
import './App.css';

function App() {
  // Authentication State
  const [currentUser, setCurrentUser] = useState(() => {
    const saved = localStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });
  const [authUsername, setAuthUsername] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [isRegistering, setIsRegistering] = useState(false);

  // Context Boundary State
  const [boundaries, setBoundaries] = useState([]);
  const [selectedBoundary, setSelectedBoundary] = useState(null);
  const [newBoundaryName, setNewBoundaryName] = useState('');
  const [newBoundaryDesc, setNewBoundaryDesc] = useState('');
  const [showCreateBoundary, setShowCreateBoundary] = useState(false);

  // Graph and Ingestion State
  const [graphData, setGraphData] = useState({ nodes: [], edges: [] });
  const [noteText, setNoteText] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedElement, setSelectedElement] = useState(null);

  // Status/Toast Feedback
  const [status, setStatus] = useState(null);

  const graphRef = useRef(null);

  // Auto-clear status feedback after 4 seconds
  useEffect(() => {
    if (status) {
      const timer = setTimeout(() => setStatus(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [status]);

  // Fetch context boundaries for logged in user
  const fetchBoundaries = async (userId) => {
    try {
      const res = await fetch(`/api/v1/context-boundary/user/${userId}`);
      if (res.ok) {
        const data = await res.json();
        setBoundaries(data);
        if (data.length > 0 && !selectedBoundary) {
          setSelectedBoundary(data[0]);
        }
      }
    } catch (err) {
      console.error("Failed to fetch boundaries", err);
    }
  };

  useEffect(() => {
    if (currentUser) {
      fetchBoundaries(currentUser.id);
    } else {
      setBoundaries([]);
      setSelectedBoundary(null);
      setGraphData({ nodes: [], edges: [] });
    }
  }, [currentUser]);

  // Fetch complete graph for the selected boundary
  const fetchGraph = async () => {
    if (!selectedBoundary || !currentUser) return;
    try {
      const res = await fetch(`/api/v1/graph/${selectedBoundary.id}?userId=${currentUser.id}`);
      if (res.ok) {
        const data = await res.json();
        setGraphData(data);
      }
    } catch (err) {
      showStatus('error', "Failed to load knowledge graph");
      console.error(err);
    }
  };

  useEffect(() => {
    if (selectedBoundary) {
      fetchGraph();
      setSelectedElement(null);
    }
  }, [selectedBoundary]);

  // Network Visualizer initializer
  useEffect(() => {
    const container = graphRef.current;
    if (container && graphData.nodes.length > 0) {
      const visNodes = graphData.nodes.map(n => ({
        id: n.id,
        label: n.label,
        title: `${n.label} (${n.category || 'Concept'})\n${n.description || ''}`,
        color: getNodeColor(n.category),
        font: { color: '#ffffff', face: 'Inter', size: 13, bold: true },
        borderWidth: 2,
        shadow: { enabled: true, color: 'rgba(0,0,0,0.4)', size: 4 },
        shape: 'dot',
        size: 18
      }));

      const visEdges = graphData.edges.map((e, idx) => ({
        id: `edge-${idx}`,
        from: e.source,
        to: e.target,
        label: e.predicate,
        arrows: 'to',
        font: { 
          color: '#9ca3af', 
          align: 'horizontal', 
          size: 10, 
          face: 'Inter', 
          background: 'rgba(15, 17, 26, 0.95)', 
          strokeWidth: 0 
        },
        color: { color: 'rgba(255,255,255,0.15)', hover: '#8b5cf6', highlight: '#8b5cf6' },
        width: 1.5,
        shadow: { enabled: true, color: 'rgba(0,0,0,0.3)', size: 3 },
        smooth: { type: 'cubicBezier', roundness: 0.4 }
      }));

      const data = { nodes: visNodes, edges: visEdges };
      const options = {
        physics: {
          forceAtlas2Based: {
            gravitationalConstant: -70,
            centralGravity: 0.02,
            springLength: 120,
            springConstant: 0.08
          },
          solver: 'forceAtlas2Based',
          stabilization: { iterations: 100 }
        },
        interaction: {
          hover: true,
          selectConnectedEdges: false,
          tooltipDelay: 200
        }
      };

      const network = new Network(container, data, options);

      network.on('click', (params) => {
        if (params.nodes.length > 0) {
          const nodeId = params.nodes[0];
          const node = graphData.nodes.find(n => n.id === nodeId);
          setSelectedElement({ type: 'node', data: node });
        } else if (params.edges.length > 0) {
          const edgeId = params.edges[0];
          const edgeIdx = parseInt(edgeId.split('-')[1]);
          const edge = graphData.edges[edgeIdx];
          setSelectedElement({ type: 'edge', data: edge });
        } else {
          setSelectedElement(null);
        }
      });

      return () => {
        network.destroy();
      };
    }
  }, [graphData]);

  // Color mapper based on node categories
  const getNodeColor = (category) => {
    const cat = (category || '').toLowerCase();
    if (cat.includes('person') || cat.includes('user')) {
      return { background: '#1e1b4b', border: '#818cf8', highlight: { background: '#312e81', border: '#a5b4fc' } };
    }
    if (cat.includes('sport') || cat.includes('activity') || cat.includes('hobby')) {
      return { background: '#064e3b', border: '#34d399', highlight: { background: '#065f46', border: '#6ee7b7' } };
    }
    if (cat.includes('fruit') || cat.includes('food')) {
      return { background: '#7c2d12', border: '#fb923c', highlight: { background: '#9a3412', border: '#fdba74' } };
    }
    if (cat.includes('place') || cat.includes('location')) {
      return { background: '#0c4a6e', border: '#38bdf8', highlight: { background: '#075985', border: '#7dd3fc' } };
    }
    // Default Concept color
    return { background: '#1f2937', border: '#9ca3af', highlight: { background: '#374151', border: '#d1d5db' } };
  };

  const showStatus = (type, text) => {
    setStatus({ type, text });
  };

  // Auth Operations
  const handleAuth = async (e) => {
    e.preventDefault();
    if (!authUsername || !authPassword) {
      showStatus('error', 'Username and Password are required');
      return;
    }
    setLoading(true);
    const endpoint = isRegistering ? '/api/v1/auth/register' : '/api/v1/auth/login';
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: authUsername, password: authPassword })
      });

      if (res.ok) {
        if (isRegistering) {
          showStatus('success', 'User registered! Please log in.');
          setIsRegistering(false);
        } else {
          const userId = await res.text();
          const user = { id: userId, username: authUsername };
          setCurrentUser(user);
          localStorage.setItem('user', JSON.stringify(user));
          showStatus('success', `Welcome back, ${authUsername}!`);
        }
        setAuthPassword('');
      } else {
        const errorText = await res.text();
        showStatus('error', errorText || 'Authentication failed');
      }
    } catch (err) {
      showStatus('error', 'Network error during authentication');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    setCurrentUser(null);
    localStorage.removeItem('user');
    showStatus('success', 'Logged out successfully');
  };

  // Context Boundary creation
  const handleCreateBoundary = async (e) => {
    e.preventDefault();
    if (!newBoundaryName || !newBoundaryDesc) {
      showStatus('error', 'Name and Context description are required');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch(`/api/v1/context-boundary/?userId=${currentUser.id}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newBoundaryName, context: newBoundaryDesc })
      });

      if (res.ok) {
        const data = await res.json();
        setBoundaries(prev => [...prev, data]);
        setSelectedBoundary(data);
        setNewBoundaryName('');
        setNewBoundaryDesc('');
        setShowCreateBoundary(false);
        showStatus('success', `Created Context: ${data.name}`);
      } else {
        showStatus('error', 'Failed to create context boundary');
      }
    } catch (err) {
      showStatus('error', 'Network error');
    } finally {
      setLoading(false);
    }
  };

  // Ingest Note
  const handleIngestNote = async (e) => {
    e.preventDefault();
    if (!noteText.trim() || !selectedBoundary) return;
    setLoading(true);
    try {
      const res = await fetch('/api/v1/aiService/ingest', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          note: noteText,
          contextBoundaryId: selectedBoundary.id,
          actorId: currentUser.id
        })
      });

      if (res.ok) {
        setNoteText('');
        showStatus('success', 'Note processed & Graph Restructured!');
        await fetchGraph(); // Reload graph data to update viz
      } else {
        showStatus('error', 'Failed to process note');
      }
    } catch (err) {
      showStatus('error', 'Network error while ingesting note');
    } finally {
      setLoading(false);
    }
  };

  // Register UI fallback
  if (!currentUser) {
    return (
      <div className="app-container" style={{ justifyContent: 'center', alignItems: 'center' }}>
        <form onSubmit={handleAuth} className="glass-panel panel-card animate-fade-in" style={{ width: '380px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
            <Brain size={48} color="#8b5cf6" style={{ filter: 'drop-shadow(0 0 8px var(--accent-purple-glow))' }} />
            <h1 style={{ fontFamily: 'var(--font-heading)', fontSize: '28px', margin: 0 }}>Knodeledge</h1>
            <p style={{ color: 'var(--text-secondary)', fontSize: '13px' }}>AI-Powered Dynamic Knowledge Graph</p>
          </div>

          <h2 style={{ fontSize: '18px', textAlign: 'center', marginBottom: '8px' }}>
            {isRegistering ? 'Create Account' : 'Welcome Back'}
          </h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div>
              <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>Username</label>
              <input 
                type="text" 
                placeholder="e.g. kuku" 
                value={authUsername}
                onChange={e => setAuthUsername(e.target.value)} 
              />
            </div>

            <div>
              <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>Password</label>
              <input 
                type="password" 
                placeholder="••••••••" 
                value={authPassword} 
                onChange={e => setAuthPassword(e.target.value)}
              />
            </div>
          </div>

          {status && (
            <div className={`status-banner ${status.type} animate-fade-in`}>
              <AlertCircle size={16} />
              <span>{status.text}</span>
            </div>
          )}

          <button type="submit" className="primary" style={{ marginTop: '8px' }} disabled={loading}>
            {loading ? <RefreshCw className="spin" size={16} /> : null}
            {isRegistering ? 'Register' : 'Login'}
          </button>

          <p style={{ fontSize: '13px', textAlign: 'center', marginTop: '12px', color: 'var(--text-secondary)' }}>
            {isRegistering ? 'Already have an account?' : "Don't have an account?"}{' '}
            <span 
              onClick={() => { setIsRegistering(!isRegistering); setStatus(null); }}
              style={{ color: 'var(--accent-purple)', cursor: 'pointer', fontWeight: 600 }}
            >
              {isRegistering ? 'Login' : 'Register'}
            </span>
          </p>
        </form>
      </div>
    );
  }

  return (
    <div className="app-container">
      {/* Sidebar Panel */}
      <aside className="sidebar">
        {/* Header Branding */}
        <div className="brand">
          <Brain size={32} color="#8b5cf6" style={{ filter: 'drop-shadow(0 0 6px var(--accent-purple-glow))' }} />
          <div>
            <h1>Knodeledge</h1>
            <div className="brand-subtitle">Dynamic Graph Workspace</div>
          </div>
        </div>

        {/* User Stats Badge */}
        <div className="glass-panel panel-card" style={{ padding: '14px 16px', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{ background: 'var(--accent-purple-glow)', padding: '6px', borderRadius: '50%' }}>
              <User size={16} color="var(--accent-purple)" />
            </div>
            <div>
              <div style={{ fontSize: '13px', fontWeight: 600 }}>{currentUser.username}</div>
              <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>Workspace Actor</div>
            </div>
          </div>
          <button className="secondary" onClick={handleLogout} style={{ padding: '6px 10px', borderRadius: '6px' }}>
            <LogOut size={14} />
          </button>
        </div>

        {/* Status Toast Notification */}
        {status && (
          <div className={`status-banner ${status.type} animate-fade-in`}>
            <AlertCircle size={16} />
            <span>{status.text}</span>
          </div>
        )}

        {/* Context Boundary Selection Panel */}
        <div className="glass-panel panel-card">
          <div className="panel-header">
            <span className="panel-title">
              <Database size={16} color="var(--accent-blue)" />
              Context Boundaries
            </span>
            <button 
              className="secondary" 
              onClick={() => setShowCreateBoundary(!showCreateBoundary)}
              style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '11px' }}
            >
              {showCreateBoundary ? 'Cancel' : 'New'}
            </button>
          </div>

          {showCreateBoundary ? (
            <form onSubmit={handleCreateBoundary} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }} className="animate-fade-in">
              <input 
                type="text" 
                placeholder="Boundary Name (e.g. Sarah's Profile)" 
                value={newBoundaryName}
                onChange={e => setNewBoundaryName(e.target.value)}
              />
              <textarea 
                rows="2" 
                placeholder="Scope description for LLM..." 
                value={newBoundaryDesc}
                onChange={e => setNewBoundaryDesc(e.target.value)}
              />
              <button type="submit" className="primary" disabled={loading}>
                <Plus size={16} /> Create Boundary
              </button>
            </form>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', maxHeight: '180px', overflowY: 'auto' }}>
              {boundaries.length === 0 ? (
                <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'center', padding: '10px 0' }}>
                  No boundaries found. Create one to get started!
                </div>
              ) : (
                boundaries.map(cb => (
                  <div 
                    key={cb.id} 
                    className={`list-item ${selectedBoundary?.id === cb.id ? 'active' : ''}`}
                    onClick={() => setSelectedBoundary(cb)}
                  >
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      <span style={{ fontSize: '13px', fontWeight: 500 }}>{cb.name}</span>
                      <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{cb.context.length > 35 ? cb.context.slice(0, 35) + '...' : cb.context}</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </div>

        {/* Note Ingestion Panel */}
        <div className="glass-panel panel-card" style={{ flex: 1, minHeight: '220px' }}>
          <div className="panel-header">
            <span className="panel-title">
              <NetworkIcon size={16} color="var(--accent-purple)" />
              Ingest Note
            </span>
          </div>

          <form onSubmit={handleIngestNote} style={{ display: 'flex', flexDirection: 'column', gap: '12px', height: '100%' }}>
            <textarea 
              style={{ flex: 1, resize: 'none' }}
              placeholder="Dump a free-form note here... e.g. 'Sarah loves hiking and her favorite fruit is apple'"
              value={noteText}
              onChange={e => setNoteText(e.target.value)}
              disabled={!selectedBoundary || loading}
            />
            <button 
              type="submit" 
              className="primary" 
              disabled={!selectedBoundary || !noteText.trim() || loading}
              style={{ width: '100%' }}
            >
              {loading ? (
                <>
                  <RefreshCw className="spin" size={16} /> Processing & Restructuring...
                </>
              ) : (
                <>
                  <Send size={16} /> Submit to Graph
                </>
              )}
            </button>
          </form>
        </div>
      </aside>

      {/* Main Canvas Workspace */}
      <main className="canvas-container">
        {graphData.nodes.length === 0 ? (
          <div className="empty-state">
            <Brain size={64} color="var(--text-muted)" style={{ opacity: 0.3 }} />
            <h3>No Graph Data</h3>
            <p>
              {!selectedBoundary 
                ? "Select or create a Context Boundary in the sidebar to start mapping your knowledge graph."
                : "This context boundary has no ingested facts yet. Try typing a note and submitting it to the graph!"}
            </p>
          </div>
        ) : (
          <>
            {/* Visualizer Canvas */}
            <div ref={graphRef} className="graph-canvas" />

            {/* Refresh Floating Action Button */}
            <div className="floating-controls">
              <button className="secondary" onClick={fetchGraph} style={{ borderRadius: '50%', padding: '10px' }} title="Refresh Visualizer">
                <RefreshCw size={16} />
              </button>
            </div>

            {/* Glassmorphic Detail Inspector Card */}
            {selectedElement && (
              <div className="glass-panel inspector-panel">
                <div className="inspector-header">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Info size={16} color="var(--accent-purple)" />
                    <span style={{ fontSize: '13px', fontWeight: 600 }}>Element Inspector</span>
                  </div>
                  <span className={`badge ${selectedElement.type === 'node' ? 'node-state' : 'edge-state'}`}>
                    {selectedElement.type}
                  </span>
                </div>

                {selectedElement.type === 'node' ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    <div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Entity Label</div>
                      <div style={{ fontSize: '16px', fontWeight: 700, fontFamily: 'var(--font-heading)' }}>
                        {selectedElement.data.label}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Category / Type</div>
                      <div style={{ fontSize: '13px', color: 'var(--accent-purple)', fontWeight: 500 }}>
                        {selectedElement.data.category || 'Concept'}
                      </div>
                    </div>
                    {selectedElement.data.description && (
                      <div>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Description</div>
                        <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                          {selectedElement.data.description}
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    <div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Relationship</div>
                      <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-primary)' }}>
                        {selectedElement.data.source} ── {selectedElement.data.predicate} ──&gt; {selectedElement.data.target}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Raw Ingest Sentence</div>
                      <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.4, fontStyle: 'italic' }}>
                        "{selectedElement.data.context}"
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}

export default App;
