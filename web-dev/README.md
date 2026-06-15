# Knodeledge Web Client

React and Vite client for the Knodeledge recursive hierarchy workspace.

## Features

- registration, login, and local session persistence
- context-boundary creation and selection
- free-form note ingestion
- hierarchy-grounded prompting
- one-level radial hierarchy explorer
- typed relation labels
- child drill-down plus breadcrumb and back navigation
- exact leaf statements and summaries
- debug-only full hierarchy export

Normal exploration requests only the current node, its immediate children, and breadcrumbs.

## Development

```bash
npm install
npm run dev
```

The client uses relative `/api/v1/...` URLs. Vite proxy behavior is configured in
`vite.config.js`.

## Verification

```bash
npm run lint
npm run build
```

## Backend Documentation

1. [System overview](../knodeledge-spring/docs/1.system_overview.md)
2. [Hierarchy architecture](../knodeledge-spring/docs/2.hierarchy_design/2.1.hierarchy_architecture.md)
