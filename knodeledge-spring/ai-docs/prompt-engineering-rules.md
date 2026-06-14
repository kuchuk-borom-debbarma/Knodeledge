# Prompt Engineering Rules: Graph Construction Prompts

This document defines the rules and principles for writing and maintaining
LLM prompts in the Knodeledge graph extraction pipeline. Any developer writing
or modifying a prompt (`.st` file under `resources/prompts/`) must follow these rules.

---

## 1. The Two-Stage Pipeline

Every note goes through two sequential prompts before touching the graph.

```
Raw Note
   │
   ▼
[Stage 1: Clean Prompt]  →  Declarative facts (plain text)
   │
   ▼
[Stage 2: Ingest Prompt]  →  JSON graph delta {nodes[], edges[]}
   │
   ▼
Backend merges into graph
```

**Stage 1 (Clean)** strips noise and normalises language.
**Stage 2 (Ingest)** extracts structured graph data.

These stages are deliberately separate. Never merge them into one prompt.
Merging causes the model to conflate noise filtering with structural extraction,
producing worse results on both.

---

## 2. Prompt Structure Rules

Every prompt file must follow this layout in order:

```
1. Role statement      — one sentence: what the model IS and what its job is
2. Output schema       — exact JSON schema or output format, inline
3. Rules               — numbered, labelled, principle-based
4. Examples            — 3–4 few-shot examples demonstrating the rules
```

### 2.1 Role Statement
- One sentence. State what the model is and what it must produce.
- Do not include system background, motivation, or design rationale.
- Bad:  "You are an AI that helps build knowledge graphs for personal use..."
- Good: "You are a State-Aware Knowledge Graph Extraction Engine."

### 2.2 Output Schema
- Always document the output schema inline, before the rules.
- The model needs to see the schema before it reads the rules that reference field names.
- For JSON output: show the full shape with field names and allowed values annotated.
  Never reference a field name in a rule without it appearing in the schema first.

### 2.3 Rules
See Section 3.

### 2.4 Examples
See Section 4.

---

## 3. Rule Writing Principles

### 3.1 Rules Must Be Principles, Not Domain Examples

Rules must express a universal principle the model can apply to any input.
Rules must NOT enumerate domain-specific cases as a substitute for the principle.

**Wrong approach (domain enumeration):**
```
- "gaming" / "game" / "games" → use id "game"
- "music making" / "music"    → use id "music"
- "anime shows" / "anime"     → use id "anime"
```

**Right approach (principle):**
```
The same real-world concept must always map to the same ID regardless of phrasing.
Morphological variants, plurals, gerunds, and compound phrasings all collapse to one ID.
```

If you find yourself writing "X in domain A → do Y", "X in domain B → do Y",
"X in domain C → do Y", you are describing a principle, not three rules. Write the principle.
Domain examples may appear in the few-shot *examples* section, never in the rules themselves.

### 3.2 Rules Must Be Exhaustive for Their Concern

A rule must cover all cases within its concern area completely.
If a rule has edge cases handled differently, they must be part of the same rule,
not separate rules added later as patches.

Example: the SUBCATEGORY_OF rule covers ALL domains (games, media, food, professions,
academic fields, music genres) and ALL graph states (existing node or same-batch new node)
within a single rule. It does not need separate rules per domain.

### 3.3 Open vs Closed Vocabulary

If a field accepts a fixed set of values, say so explicitly and list them all.
If a field accepts an open vocabulary, say so explicitly so the model knows to invent values.

- `taxonomyType` — **closed**: `EVENT | PREFERENCE | STATE` — list every valid value.
- `predicate`    — **open**: use the most precise verb available, invent one if none fit.
  State explicitly: *"the list below is non-exhaustive — invent any verb that fits."*

Never leave vocabulary openness ambiguous. A list without an explicit openness statement
will be treated as closed by the model, causing vague fallback predicates (HAS, RELATED_TO).

### 3.4 Disambiguate Similar Predicates

When two predicates could be confused, document the decision rule explicitly in the rule body.

Required disambiguation pairs for this pipeline:

| Correct | Wrong | When |
|---|---|---|
| `PLAYS` (EVENT) | `LIKES` | Active performance: instrument, game, sport |
| `CREATES` (EVENT) | `INTERESTED_IN` or `LIKES` | Produces output: music, art, writing, code |
| `PREVIOUSLY_LIVED_IN` (STATE) | `LIVES_IN` | Past state, no longer current |
| `SUBCATEGORY_OF` (STATE) | `LIKES` | Structural type relationship, not preference |

Format in the rule: state the verb → correct choice, then the counter-example.
```
"makes music"       → CREATES (EVENT)          not INTERESTED_IN or LIKES
"plays guitar"      → PLAYS (EVENT)             not LIKES
"used to live in X" → PREVIOUSLY_LIVED_IN       not LIVES_IN
```

### 3.5 Temporal State Handling — Dedicated Rule

Past-tense states are a common failure mode. They must have their own dedicated rule,
not be buried inside a normalisation rule.

**Principle**: when a note describes a state that is no longer current ("used to",
"previously", "formerly", "moved from", "ex-"), the predicate must be a distinct past-form
variant so it does not overwrite the current-state edge in the graph.

Convention: use the `PREVIOUSLY_` prefix.
```
"used to live in London"   → PREVIOUSLY_LIVED_IN    (not LIVES_IN)
"previously worked at X"   → PREVIOUSLY_WORKED_AT   (not WORKS_AT)
"formerly studied chem"    → PREVIOUSLY_STUDIED      (not STUDIES)
```

### 3.6 Conditional Preference Handling — Dedicated Rule

Conditional preferences ("likes X but not when Y") must always produce two coexisting edges.
The rule must state all five sub-points together:

1. Both X and Y are extracted as separate real nodes (no composite nodes).
2. Unconditional edge: `conditions: []`
3. Conditional edge: `conditions: ["y_id"]`
4. Both edges coexist — neither is dropped.
5. `conditions: []` must always be present on every edge — never omit the field.

Composite nodes (`"coffee_with_sugar"`, `"ice_cream_with_dry_fruit"`) are forbidden.
They destroy graph traversal by encoding multiple entities into a single node ID.

### 3.7 Hyponym Linking (SUBCATEGORY_OF) — Dedicated Rule

When the note implies entity X is a specific instance or specialisation of broader concept Y,
a `SUBCATEGORY_OF` STATE edge must be created: X → Y.

**This rule is domain-agnostic.** The trigger is the semantic relationship "X is a kind of Y",
detected from context — not from specific keywords or domain membership.

The rule applies uniformly across all domains:
- Media:       "anime like Attack on Titan" → attack_on_titan SUBCATEGORY_OF anime
- Games:       "plays Rainbow Six Siege game" → rainbow_six_siege SUBCATEGORY_OF game
- Professions: "programmer who likes game dev" → game_development SUBCATEGORY_OF programmer
- Food:        "chef specialising in sushi" → sushi SUBCATEGORY_OF cuisine
- Academia:    "studies CS, interested in ML" → machine_learning SUBCATEGORY_OF computer_science
- Music:       "listens to jazz" (in a music context) → jazz SUBCATEGORY_OF music

The rule applies whether Y pre-exists in the graph OR is newly created in the same extraction
batch. The condition "Y must already exist" is wrong and must never appear in this rule.

This edge is what makes specific instances reachable via general concept traversal —
essential for chatbot queries like "what games does the actor play?".

### 3.8 Rule Numbering and Stability

- Rules are numbered sequentially from RULE 1.
- A rule's number must not change once established (it is referenced in commit history and docs).
- New rules are appended at the end unless they logically precede an existing rule.
- When a rule is split into two, both reference the original.

---

## 4. Few-Shot Example Rules

### 4.1 How Many Examples

3–4 examples per prompt. Fewer than 3 gives insufficient generalisation coverage.
More than 4 makes the prompt too long and deprioritises earlier rules.

### 4.2 What Each Example Must Demonstrate

Each example must show at least one non-obvious behaviour the model gets wrong without it.
Do not use examples to demonstrate trivial behaviour (basic node extraction).

Priority behaviours to cover across the 3–4 examples:

| Priority | Behaviour |
|---|---|
| 1 | Active verb semantics — PLAYS/CREATES vs LIKES (most common error) |
| 2 | Conditional preferences — coexisting LIKES + DISLIKES with conditions |
| 3 | Existing graph merging — only new/updated nodes and edges in output |
| 4 | Temporal or past states — PREVIOUSLY_ prefix |
| 5 | Preference supersession — FAVORITE changes |
| 6 | Same-batch SUBCATEGORY_OF — both X and Y are new in this batch |

### 4.3 Examples Must Use Indented JSON

Always use indented, human-readable JSON in examples. Never minified.
The model learns output format by reading the examples. Minified JSON degrades this.

```json
// Good
{
  "source": "leo",
  "target": "guitar",
  "predicate": "PLAYS",
  "taxonomyType": "EVENT",
  "conditions": []
}

// Bad
{"source":"leo","target":"guitar","predicate":"PLAYS","taxonomyType":"EVENT","conditions":[]}
```

### 4.4 Examples Must Use Generic, Domain-Neutral Names

Use generic names: leo, james, sarah, carlos, nina, priya.
Never use names from the development team, test users, or real project data.
Vary the domain across examples so the model sees rules apply universally.

### 4.5 Every Example Must Have a `Note:` Annotation

After each example's JSON output, include a `Note:` section that explicitly names
which rules are demonstrated and why the output is correct.

This prevents the model from pattern-matching against the example instead of applying
the principle. It closes the loop between rule and output.

```
Note: Both "game" and "counter_strike" are NEW in this batch (Existing Graph is empty).
SUBCATEGORY_OF is still created because Rule 5 applies to same-batch nodes too.
"creates music" → CREATES (EVENT), not INTERESTED_IN. (Rule 4)
```

---

## 5. Stage 1 (Clean Prompt) Specific Rules

The Stage 1 prompt's only job is producing clean declarative sentences.
It must NOT attempt any graph extraction.

Required rules for the clean prompt in this order:

| # | Rule | Why Critical |
|---|---|---|
| 1 | Anchor pronouns | Downstream extraction cannot resolve pronouns |
| 2 | Strip noise | Filler confuses the extraction stage |
| 3 | Expand lists | One fact per list item; extraction cannot split lists reliably |
| 4 | Preserve verb semantics | The exact verb maps to the graph predicate. Softening to "likes" causes wrong predicates downstream |
| 5 | Explicit conditionals | Extraction cannot reliably split an implicit conditional |
| 6 | Temporal tense | "used to" must survive cleaning so extraction can use PREVIOUSLY_ predicates |
| 7 | Do not invent | Hallucinated facts corrupt the graph |
| 8 | Output format | One sentence per line; no preamble |

**Rules 4 and 6 are the most commonly omitted and the most important.**

Without Rule 4: "makes music" becomes "likes music" → extraction produces INTERESTED_IN instead of CREATES.
Without Rule 6: "used to live in X" loses tense → extraction produces LIVES_IN, overwriting current data.

---

## 6. Anti-Patterns

| Anti-Pattern | Consequence |
|---|---|
| Merging Stage 1 and Stage 2 into one prompt | Model conflates cleaning with extraction; both degrade |
| Writing rules as domain enumerations | Breaks on any domain not in the list |
| Closed predicate list without saying so | Model uses vague fallbacks: HAS, RELATED_TO, CONNECTED_TO |
| Minified JSON in examples | Model produces structurally incorrect output |
| No `Note:` on examples | Model pattern-matches, doesn't generalise |
| `conditions` field absent on unconditional edges | Backend cannot distinguish unconditional from missing |
| Composite nodes ("coffee_with_sugar") | Destroys graph traversal; entities unreachable |
| IS_A predicate | Prevents category-based queries; use `categories[]` instead |
| Same predicate for current and past states | Past state silently overwrites current data |
| "Y must exist" condition on SUBCATEGORY_OF | Specific instances unreachable when both X and Y are new |
| Reactive domain-specific patches | The next unseen domain breaks the same rule again |

---

## 7. Maintenance Rules

1. **Every prompt change must be committed** with a message referencing the rule number
   and the reason: e.g., `"fix Rule 5: apply SUBCATEGORY_OF to same-batch new nodes"`.

2. **Do not patch rules reactively for specific inputs.** When an input reveals a failure,
   identify the missing principle and update the rule to cover all cases of that class.

3. **After any rule change, re-test with at least 3 diverse notes** to verify the change
   does not break existing behaviour.

4. **Prompt files are code.** They are version-controlled, reviewed, and documented like
   any other source file. The same standards apply.

5. **Do not add a new rule for every new domain or predicate seen in production.**
   If a new predicate (e.g., COACHED_BY) is needed, it is an example of the open vocabulary
   rule working correctly — no rule change is needed. Only add a rule when a new *class*
   of behaviour is missing.
