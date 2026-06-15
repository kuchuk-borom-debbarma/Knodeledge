# Knodeledge

Knodeledge is an AI-powered workspace that turns raw notes into a recursive knowledge hierarchy.
Each workspace starts with one subject root, organizes knowledge into progressively detailed topic
levels, preserves exact facts and conditions, and supports hierarchy-grounded questions.

## Core Features

- bounded subject workspaces
- AI extraction of atomic facts and conditions
- top-down hierarchy routing without full-context loading
- typed parent-child relations
- synchronized multi-topic fact placements
- continuous local branch rebalancing
- radial drill-down explorer
- hierarchy-grounded prompting
- exact in-memory prompt-response cache

## Development

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
2. [Hierarchy architecture](knodeledge-spring/docs/2.hierarchy_design/2.1.hierarchy_architecture.md)
3. [Prompt engineering rules](knodeledge-spring/docs/3.ai_pipeline/3.1.prompt_engineering_rules.md)
