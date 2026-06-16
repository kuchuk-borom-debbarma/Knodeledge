import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  BookOpenText,
  CheckCircle2,
  CircleUserRound,
  Clock3,
  FilePlus2,
  Loader2,
  LogOut,
  MessageSquareText,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Send,
  Trash2,
  XCircle,
} from 'lucide-react';
import './App.css';

const emptyDraft = { id: null, title: '', content: '' };

function App() {
  const [currentUser, setCurrentUser] = useState(() => {
    const saved = localStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });
  const [authUsername, setAuthUsername] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [isRegistering, setIsRegistering] = useState(false);
  const [notes, setNotes] = useState([]);
  const [selectedNoteId, setSelectedNoteId] = useState(null);
  const [draft, setDraft] = useState(emptyDraft);
  const [question, setQuestion] = useState('');
  const [chatResult, setChatResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [notesLoading, setNotesLoading] = useState(false);
  const [chatLoading, setChatLoading] = useState(false);
  const [status, setStatus] = useState(null);

  const selectedNote = useMemo(
    () => notes.find((note) => note.id === selectedNoteId) || null,
    [notes, selectedNoteId]
  );

  const authHeaders = useCallback(
    () => ({
      'Content-Type': 'application/json',
      Authorization: `Bearer ${currentUser?.token || currentUser?.id}`,
    }),
    [currentUser]
  );

  const showStatus = (type, text) => setStatus({ type, text });

  const loadNotes = useCallback(async () => {
    if (!currentUser) return;
    setNotesLoading(true);
    try {
      const response = await fetch('/api/v1/notes', { headers: authHeaders() });
      if (!response.ok) throw new Error('Failed to load notes');
      const data = await response.json();
      setNotes(data);
      setSelectedNoteId((current) => {
        if (current && data.some((note) => note.id === current)) return current;
        return data[0]?.id || null;
      });
    } catch (error) {
      showStatus('error', error.message);
    } finally {
      setNotesLoading(false);
    }
  }, [authHeaders, currentUser]);

  useEffect(() => {
    if (currentUser) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      loadNotes();
    }
  }, [currentUser, loadNotes]);

  useEffect(() => {
    if (!status) return undefined;
    const timeout = setTimeout(() => setStatus(null), 4200);
    return () => clearTimeout(timeout);
  }, [status]);

  useEffect(() => {
    if (!currentUser) return undefined;
    const hasActiveIndexing = notes.some((note) =>
      ['pending', 'indexing'].includes(note.indexStatus)
    );
    if (!hasActiveIndexing) return undefined;
    const interval = setInterval(loadNotes, 3000);
    return () => clearInterval(interval);
  }, [currentUser, loadNotes, notes]);

  useEffect(() => {
    if (selectedNote) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDraft({
        id: selectedNote.id,
        title: selectedNote.title || '',
        content: selectedNote.content || '',
      });
    } else {
      setDraft(emptyDraft);
    }
  }, [selectedNote]);

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
          body: JSON.stringify({ username: authUsername.trim(), password: authPassword }),
        }
      );
      if (!response.ok) throw new Error('Authentication failed');
      const user = await response.json();
      localStorage.setItem('user', JSON.stringify(user));
      setCurrentUser(user);
      setAuthPassword('');
    } catch (error) {
      showStatus('error', error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveNote = async (event) => {
    event.preventDefault();
    if (!draft.content.trim()) return;
    setLoading(true);
    try {
      const isUpdate = Boolean(draft.id);
      const response = await fetch(isUpdate ? `/api/v1/notes/${draft.id}` : '/api/v1/notes', {
        method: isUpdate ? 'PATCH' : 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          title: draft.title,
          content: draft.content,
        }),
      });
      if (!response.ok) throw new Error(isUpdate ? 'Failed to update note' : 'Failed to create note');
      const note = await response.json();
      await loadNotes();
      setSelectedNoteId(note.id);
      showStatus('success', isUpdate ? 'Note saved' : 'Note created');
    } catch (error) {
      showStatus('error', error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteNote = async () => {
    if (!draft.id || !window.confirm('Delete this note?')) return;
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/notes/${draft.id}`, {
        method: 'DELETE',
        headers: authHeaders(),
      });
      if (!response.ok) throw new Error('Failed to delete note');
      setSelectedNoteId(null);
      setDraft(emptyDraft);
      await loadNotes();
      showStatus('success', 'Note deleted');
    } catch (error) {
      showStatus('error', error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleReindex = async (noteId) => {
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/notes/${noteId}/reindex`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!response.ok) throw new Error('Failed to reindex note');
      await loadNotes();
      showStatus('success', 'Reindex queued');
    } catch (error) {
      showStatus('error', error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAsk = async (event) => {
    event.preventDefault();
    if (!question.trim()) return;
    setChatLoading(true);
    setChatResult(null);
    try {
      const response = await fetch('/api/v1/chat', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ question }),
      });
      if (!response.ok) throw new Error('Failed to answer question');
      setChatResult(await response.json());
    } catch (error) {
      showStatus('error', error.message);
    } finally {
      setChatLoading(false);
    }
  };

  const startNewNote = () => {
    setSelectedNoteId(null);
    setDraft(emptyDraft);
  };

  if (!currentUser) {
    return (
      <div className="auth-shell">
        <form className="auth-card" onSubmit={handleAuth}>
          <BookOpenText size={42} />
          <h1>Knodeledge</h1>
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
            {loading ? <Loader2 className="spin" size={16} /> : <CircleUserRound size={16} />}
            {isRegistering ? 'Create account' : 'Log in'}
          </button>
          <button
            className="ghost"
            type="button"
            onClick={() => setIsRegistering((value) => !value)}
          >
            {isRegistering ? 'Use existing account' : 'Create new account'}
          </button>
        </form>
        {status && <StatusToast status={status} />}
      </div>
    );
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <BookOpenText size={25} />
          <div>
            <strong>Knodeledge</strong>
            <span>Personal RAG</span>
          </div>
        </div>
        <div className="topbar-actions">
          <span>{currentUser.username}</span>
          <button
            className="icon-button"
            title="Refresh"
            onClick={loadNotes}
            disabled={notesLoading}
          >
            <RefreshCw className={notesLoading ? 'spin' : ''} size={17} />
          </button>
          <button
            className="icon-button"
            title="Log out"
            onClick={() => {
              localStorage.removeItem('user');
              setNotes([]);
              setSelectedNoteId(null);
              setDraft(emptyDraft);
              setChatResult(null);
              setCurrentUser(null);
            }}
          >
            <LogOut size={17} />
          </button>
        </div>
      </header>

      <aside className="notes-pane">
        <div className="pane-heading">
          <div>
            <span>Notes</span>
            <strong>{notes.length}</strong>
          </div>
          <button className="icon-button" title="New note" onClick={startNewNote}>
            <FilePlus2 size={17} />
          </button>
        </div>
        <div className="search-strip">
          <Search size={15} />
          <span>Saved notes</span>
        </div>
        <div className="note-list">
          {notes.map((note) => (
            <button
              key={note.id}
              className={`note-row ${note.id === selectedNoteId ? 'active' : ''}`}
              onClick={() => setSelectedNoteId(note.id)}
            >
              <span className="note-title">{note.title || 'Untitled note'}</span>
              <span className="note-preview">{note.content}</span>
              <IndexBadge status={note.indexStatus} />
            </button>
          ))}
          {!notes.length && <div className="empty-list">No saved notes.</div>}
        </div>
      </aside>

      <main className="workspace">
        <section className="editor-pane">
          <form className="editor-form" onSubmit={handleSaveNote}>
            <div className="section-toolbar">
              <div>
                <span>{draft.id ? 'Edit note' : 'New note'}</span>
                {selectedNote && <IndexBadge status={selectedNote.indexStatus} />}
              </div>
              <div className="toolbar-actions">
                {draft.id && (
                  <>
                    <button
                      className="secondary"
                      type="button"
                      onClick={() => handleReindex(draft.id)}
                      disabled={loading}
                    >
                      <RotateCcw size={15} />
                      Retry
                    </button>
                    <button
                      className="danger"
                      type="button"
                      onClick={handleDeleteNote}
                      disabled={loading}
                    >
                      <Trash2 size={15} />
                      Delete
                    </button>
                  </>
                )}
                <button className="primary" disabled={loading || !draft.content.trim()}>
                  {loading ? <Loader2 className="spin" size={15} /> : <Save size={15} />}
                  Save
                </button>
              </div>
            </div>
            {selectedNote?.indexError && (
              <div className="inline-error">
                <AlertCircle size={15} />
                <span>{selectedNote.indexError}</span>
              </div>
            )}
            <input
              className="title-input"
              value={draft.title}
              onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))}
              placeholder="Title"
            />
            <textarea
              value={draft.content}
              onChange={(event) =>
                setDraft((current) => ({ ...current, content: event.target.value }))
              }
              placeholder="Paste messy notes here..."
            />
          </form>
        </section>

        <section className="chat-pane">
          <div className="section-toolbar">
            <div>
              <span>Chat</span>
              <strong>Ask saved notes</strong>
            </div>
          </div>
          <form className="chat-form" onSubmit={handleAsk}>
            <textarea
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask a question..."
            />
            <button className="primary" disabled={chatLoading || !question.trim()}>
              {chatLoading ? <Loader2 className="spin" size={15} /> : <Send size={15} />}
              Ask
            </button>
          </form>
          <div className="answer-panel">
            {!chatResult && (
              <div className="empty-answer">
                <MessageSquareText size={34} />
              </div>
            )}
            {chatResult && (
              <>
                <div className={chatResult.notEnoughInfo ? 'answer warning' : 'answer'}>
                  {chatResult.inference && <span className="inference-label">Inference</span>}
                  <p>{chatResult.answer}</p>
                </div>
                {!!chatResult.citations?.length && (
                  <div className="citations">
                    {chatResult.citations.map((citation) => (
                      <span key={citation.chunkId}>
                        {citation.noteTitle} · chunk {citation.chunkIndex + 1}
                      </span>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </section>
      </main>

      {status && <StatusToast status={status} />}
    </div>
  );
}

function IndexBadge({ status }) {
  const Icon = {
    pending: Clock3,
    indexing: Loader2,
    ready: CheckCircle2,
    failed: XCircle,
  }[status] || Clock3;

  return (
    <span className={`index-badge ${status}`}>
      <Icon className={status === 'indexing' ? 'spin' : ''} size={13} />
      {status || 'pending'}
    </span>
  );
}

function StatusToast({ status }) {
  return (
    <div className={`status-toast ${status.type}`}>
      {status.type === 'error' ? <AlertCircle size={17} /> : <CheckCircle2 size={17} />}
      <span>{status.text}</span>
    </div>
  );
}

export default App;
