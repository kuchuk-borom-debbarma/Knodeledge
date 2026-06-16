# Knodeledge

Knodeledge is a personal knowledge base RAG app. Paste raw notes, let the backend index
them asynchronously, and ask questions that are answered only from saved notes with citations.

## Core Features

- raw unstructured notes
- async chunking and indexing
- dense retrieval with pgvector
- sparse retrieval with Postgres full-text search
- hybrid merge and reranking
- cited answers with not-enough-info behavior
- strict user-scoped note and chunk access

## Development

Start Postgres:

```bash
cd knodeledge-spring
docker compose up -d
```

Backend:

```bash
cd knodeledge-spring
mvn test
mvn spring-boot:run
```

Frontend:

```bash
cd web-dev
npm install
npm run dev
```

## Documentation

1. [System overview](knodeledge-spring/docs/1.system_overview.md)
2. [RAG architecture](knodeledge-spring/docs/2.rag_architecture.md)
3. [Chunking and indexing](knodeledge-spring/docs/3.ingestion/3.1.chunking_and_indexing.md)
4. [API reference](knodeledge-spring/docs/4.api_reference.md)
