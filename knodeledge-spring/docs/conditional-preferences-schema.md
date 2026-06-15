# Conditional Facts - Statement Subgraph Design

**Status:** Implemented  
**Date:** 2026-06-15  
**Area:** Knowledge Graph / Qualified Relations

## Decision

Unconditional facts remain direct edges:

```text
amelia --LIKES--> ice_cream
```

Qualified facts are reified as traversable statement subgraphs:

```text
statement_1 --STATEMENT_SUBJECT--> amelia
statement_1 --DISLIKES--> ice_cream
statement_1 --WHEN--> condition_group_1

condition_group_1 --ALL_OF--> condition_1

condition_1 --CONDITION_SUBJECT--> ice_cream
condition_1 --CONTAINS--> dry_fruit
```

The graph does not store:

- `conditions` metadata on an edge
- `CONDITIONED_BY` shortcuts
- hypothetical condition facts as global facts
- composite entities such as `ice_cream_with_dry_fruit`

## Node Types

All nodes use the normal graph node schema.

| Node | Purpose |
|---|---|
| Entity | Real subject/object such as `amelia`, `ice_cream`, `dry_fruit` |
| Category | Traversable taxonomy such as `food`, `ingredient`, `statement` |
| Statement | One qualified assertion |
| Condition group | Boolean grouping for conditions |
| Condition | One atomic condition |

Structural nodes are classified through normal taxonomy edges:

```text
statement_1 --GRAPH_ROLE--> statement
condition_group_1 --GRAPH_ROLE--> condition_group
condition_1 --GRAPH_ROLE--> condition
```

## Structural Edges

| Predicate | Meaning |
|---|---|
| `STATEMENT_SUBJECT` | Statement to the assertion's real subject |
| Semantic predicate | Statement to the assertion's real object |
| `WHEN` | Statement to its root boolean condition group |
| `ALL_OF` | Every child condition/group must hold |
| `ANY_OF` | At least one child condition/group must hold |
| `NOT` | Its single child condition/group must not hold |
| `CONDITION_SUBJECT` | Condition to the entity being evaluated |
| Semantic predicate | Condition to its real object |

## Why

- Conditions remain attached to the exact assertion they qualify.
- Multiple conditional assertions about the same entities cannot collide.
- AND, OR, and negation are explicit and traversable.
- Condition predicates retain meaning: `CONTAINS`, `LOCATED_IN`, `AFTER`, etc.
- Provenance and future confidence/time metadata can attach to statement nodes.

## Query Examples

Find Amelia's conditional dislikes:

```cypher
MATCH (statement)-[:STATEMENT_SUBJECT]->(amelia {id: "amelia"}),
      (statement)-[:DISLIKES]->(target),
      (statement)-[:WHEN]->(group)
RETURN statement, target, group
```

Resolve all positive conditions:

```cypher
MATCH (statement)-[:WHEN]->(group),
      (group)-[:ALL_OF|ANY_OF|NOT*1..]->(condition),
      (condition)-[:GRAPH_ROLE]->({id: "condition"}),
      (condition)-[:CONDITION_SUBJECT]->(subject)
MATCH (condition)-[predicate]->(object)
WHERE NOT type(predicate) IN ["CONDITION_SUBJECT", "GRAPH_ROLE"]
RETURN subject, type(predicate), object
```

## Supersession

Conditional statement identity includes:

```text
subject + semantic predicate + object + normalized condition subgraph
```

- Exact duplicates collapse.
- Opposing preferences supersede only with equivalent condition subgraphs.
- Singular predicates supersede only with equivalent condition subgraphs.
- Different condition subgraphs coexist.
- Unconditional and conditional facts coexist.

## Legacy Migration

During reconciliation, old `conditions` arrays and `CONDITIONED_BY` edges are converted into
statement subgraphs. The prompt reconstructs condition predicates from source context. When the
predicate cannot be recovered safely, it uses `REFERENCES_CONDITION` instead of inventing one.
