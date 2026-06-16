CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE app_users (
    id uuid PRIMARY KEY,
    username text NOT NULL UNIQUE,
    password text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notes (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    title text,
    content text NOT NULL,
    index_status text NOT NULL DEFAULT 'pending',
    index_version integer NOT NULL DEFAULT 1,
    index_error text,
    indexed_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT notes_index_status_check
        CHECK (index_status IN ('pending', 'indexing', 'ready', 'failed'))
);

CREATE TABLE note_chunks (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    note_id uuid NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    embedding vector(1536),
    search_text tsvector GENERATED ALWAYS AS
        (to_tsvector('english', coalesce(content, ''))) STORED,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX notes_user_id_idx ON notes(user_id);
CREATE INDEX notes_user_active_idx ON notes(user_id, updated_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX note_chunks_user_id_idx ON note_chunks(user_id);
CREATE INDEX note_chunks_note_id_idx ON note_chunks(note_id);
CREATE INDEX note_chunks_search_text_gin_idx ON note_chunks USING gin(search_text);
CREATE INDEX note_chunks_embedding_hnsw_idx ON note_chunks
    USING hnsw (embedding vector_cosine_ops);
