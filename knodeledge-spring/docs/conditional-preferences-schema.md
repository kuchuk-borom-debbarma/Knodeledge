# Conditional Preferences — Schema Design Decision

**Status:** Implemented  
**Date:** 2026-06-15  
**Area:** Knowledge Graph / Edge Schema

---

## 1. The Problem

Simple preferences like "amelia likes roses" map cleanly to a single edge:

```
amelia ──LIKES──> rose
```

But conditional preferences introduce a compound structure not expressible with a plain predicate:

> *"amelia dislikes ice cream **if it contains dry fruit**"*
> *"amelia likes ice cream **but not if it has dry fruit**"*

The condition *"contains dry_fruit"* is a **qualifier** on the relationship, not a property of `ice_cream` itself.

---

## 2. Options Considered

### Option A — Composite Node (rejected)

Merge the condition into the target node: `ice_cream_with_dry_fruit`.

**Why rejected:**
- Creates artificial nodes that are not real-world entities.
- Embedding quality is poor — the vector mixes two concepts (ice cream + dry fruit).
- Impossible to answer "what ingredients does amelia avoid?" via graph traversal.
- Breaks when the same person has multiple variants of the same entity.

### Option B — Reification / Relationship-as-Node (rejected for primary use)

Model the relationship itself as a node:

```
amelia ──HAS_PREF──> pref_1 ──ABOUT──> ice_cream
                     pref_1 ──TYPE──> DISLIKES
                     pref_1 ──CONDITION──> dry_fruit
```

**Why rejected:**
- Requires 2+ tool calls per preference query in a chatbot context.
- Clutters the visual graph significantly.
- Academically correct for general RDF, but over-engineered for this domain.

### Option C — Edge Conditions + CONDITIONED_BY (Hybrid) ✅ Chosen

Store the condition **on the edge as a structured list** AND ensure the condition entity
exists as a **first-class node** with a dedicated traversal edge (`CONDITIONED_BY`).

```
amelia ──LIKES──────────────────> ice_cream
amelia ──DISLIKES───────────────> ice_cream   [conditions: ["dry_fruit"]]
amelia ──CONDITIONED_BY─────────> dry_fruit   [context: "condition for DISLIKES ice_cream"]
```

---

## 3. The Hybrid Schema (Implemented)

### 3.1 Edge Schema

`EdgeDto` and `ExtractedEdgeDto` carry a `conditions` field:

```java
public record EdgeDto(
    String source,
    String target,
    String predicate,
    String context,
    List<String> conditions   // node IDs that qualify this relationship; empty = unconditional
) {}
```

- **Unconditional edge:** `conditions = []` or `null`
- **Conditional edge:** `conditions = ["dry_fruit"]`

### 3.2 Auto-generated CONDITIONED_BY Edge

When the service processes an edge with non-empty `conditions`, it automatically creates
a `CONDITIONED_BY` edge for each condition node:

```
source ──CONDITIONED_BY──> condition_node_id
                           [context: "Condition for {source} {predicate} {target}"]
```

This makes condition entities **traversable** in the graph without client-side string matching.

### 3.3 Condition Nodes are Real Nodes

Every entity referenced in `conditions` must be extracted as a proper node with a valid
`id`, `label`, and `categories`. The LLM is instructed to always extract condition entities
as standalone nodes.

---

## 4. Why This Hybrid Wins

### For the Chatbot (Tool Calling)

| Query | Mechanism |
|---|---|
| "What does amelia think about ice cream?" | One traversal: `edges WHERE source=amelia AND target=ice_cream` → returns LIKES (no condition) and DISLIKES (condition: dry_fruit) |
| "What ingredients should I avoid for amelia?" | Graph traversal: `amelia -CONDITIONED_BY-> *` → returns dry_fruit, nuts, etc. as real nodes |
| "Find all of amelia's conditional dislikes" | Filter: edges WHERE predicate=DISLIKES AND conditions is not empty |

### For Graph DB (Neo4j)

```cypher
// Store
CREATE (amelia)-[:DISLIKES {conditions: ["dry_fruit"], context: "..."}]->(ice_cream)
CREATE (amelia)-[:CONDITIONED_BY {context: "condition for DISLIKES ice_cream"}]->(dry_fruit)

// Query: what does amelia avoid as ingredients?
MATCH (amelia)-[:CONDITIONED_BY]->(ingredient)
RETURN ingredient.label

// Query: unconditional preferences only
MATCH (amelia)-[r]->(e)
WHERE size(r.conditions) = 0
RETURN e.label, type(r)
```

### For Vector DB (Pinecone / Weaviate / Qdrant)

Each edge statement is embedded independently as a full declarative sentence:

```
"amelia dislikes ice cream when it contains dry fruit"  -> vector_1
"amelia likes ice cream"                                -> vector_2
"dry fruit is an ingredient"                            -> vector_3
```

Semantic search for "what does amelia feel about ice cream?" retrieves both vector_1 and
vector_2 with their full context. Conditions are embedded in the text — no stitching needed.

---

## 5. Supersession Logic for Conditional Edges

A conditional edge must NOT supersede an unconditional edge for the same source-target-predicate
family. Two edges coexist when their condition set differs.

| Existing | Incoming | Result |
|---|---|---|
| `amelia LIKES ice_cream []` | `amelia DISLIKES ice_cream []` | Unconditional flip → LIKES superseded |
| `amelia LIKES ice_cream []` | `amelia DISLIKES ice_cream [dry_fruit]` | Condition differs → **both coexist** |
| `amelia DISLIKES ice_cream [dry_fruit]` | `amelia LIKES ice_cream [dry_fruit]` | Same condition, flip → DISLIKES superseded |
| `amelia LIKES ice_cream []` | `amelia LIKES ice_cream []` | Same triple → updated in-place |

Key rule in `LLMAIService.isSuperseded()`:

> An unconditional edge and a conditional edge always coexist.
> Supersession only applies when both edges have the same condition set.

---

## 6. Future Migration Path

### Phase 1 (Current): In-Memory
- `conditions: List<String>` on `EdgeDto` / `ExtractedEdgeDto`
- Auto-generated `CONDITIONED_BY` edges in `LLMAIService`

### Phase 2: Neo4j
- Map `conditions` to a relationship property array: `r.conditions`
- Map `CONDITIONED_BY` to a native Neo4j relationship
- No schema migration needed — both already exist in the in-memory model

### Phase 3: Vector DB
- Embed edge statements as full declarative sentences (conditions included in text)
- `CONDITIONED_BY` edges embedded as "X is a condition for amelia's Y of Z"
- Store node IDs as metadata for hybrid Graph + Vector retrieval

---

## 7. Complete Example

**Input note:**
> "amelia likes ice cream but she dislikes it when it has dry fruit in it"

**Stage 1 output (cleaned):**
```
amelia likes ice cream.
amelia dislikes ice cream when it contains dry fruit.
```

**Stage 2 LLM extraction:**
```json
{
  "nodes": [
    {"id": "amelia",    "label": "amelia",    "categories": ["person"],             "description": "Subject of context"},
    {"id": "ice_cream", "label": "Ice Cream", "categories": ["food", "dessert"],    "description": "Frozen dairy dessert"},
    {"id": "dry_fruit", "label": "Dry Fruit", "categories": ["food", "ingredient"], "description": "Dried fruits used as ingredient"}
  ],
  "edges": [
    {
      "source": "amelia", "target": "ice_cream", "predicate": "LIKES",
      "taxonomyType": "PREFERENCE", "confidence": "HIGH",
      "context": "amelia likes ice cream", "conditions": []
    },
    {
      "source": "amelia", "target": "ice_cream", "predicate": "DISLIKES",
      "taxonomyType": "PREFERENCE", "confidence": "HIGH",
      "context": "amelia dislikes ice cream when it contains dry fruit",
      "conditions": ["dry_fruit"]
    }
  ]
}
```

**Auto-generated by LLMAIService (CONDITIONED_BY):**
```json
{
  "source": "amelia", "target": "dry_fruit",
  "predicate": "CONDITIONED_BY",
  "context": "Condition for amelia DISLIKES ice_cream",
  "conditions": []
}
```

**Final graph:**
```
amelia ──LIKES──────────> ice_cream
amelia ──DISLIKES───────> ice_cream    [conditions: ["dry_fruit"]]
amelia ──CONDITIONED_BY─> dry_fruit
```

**Chatbot tool response for "anything I should know before buying ice cream?":**
```json
{
  "preferences_about_ice_cream": [
    {"predicate": "LIKES",    "conditions": [],            "context": "amelia likes ice cream"},
    {"predicate": "DISLIKES", "conditions": ["dry_fruit"], "context": "amelia dislikes ice cream when it contains dry fruit"}
  ],
  "conditions_to_avoid": [{"id": "dry_fruit", "label": "Dry Fruit"}]
}
```
