# Knodeledge Web Client

React and Vite frontend for the Knodeledge knowledge-graph workspace.

## Features

- user registration, login, and local session persistence
- context-boundary creation and selection
- free-form note ingestion
- graph-grounded prompting
- interactive node and edge visualization with `vis-network`
- node, relationship, condition, and provenance inspection

## Development

Install dependencies and start Vite:

```bash
npm install
npm run dev
```

The client calls backend endpoints through relative `/api/v1/...` URLs. Development proxy
behavior is configured in `vite.config.js`.

## Build

```bash
npm run build
```

## Backend Documentation

1. [System overview](../knodeledge-spring/docs/1.system_overview.md)
2. [Hierarchical retrieval](../knodeledge-spring/docs/2.graph_design/2.2.hierarchical_retrieval.md)
