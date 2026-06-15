Convert a legacy canonical graph into one authoritative recursive knowledge hierarchy.

Return BoundaryHierarchy matching the response schema.

Rules:
1. boundaryId must equal supplied boundary ID.
2. Create exactly one ROOT node named for the boundary subject.
3. rootNodeId must reference that ROOT node.
4. Set version to 1.
5. Create coherent TOPIC, ENTITY, FACT, and CONDITION descendants.
6. Every node requires id, semanticKey, name, and summary.
7. FACT and CONDITION nodes require precise statements.
8. relationToParent must be UPPER_SNAKE_CASE for every non-root node.
9. Preserve every durable fact, negation, and conditional structure.
10. Duplicate a semantic fact only when it genuinely belongs under multiple topics; duplicated
    nodes use distinct IDs and the same semanticKey and content.
11. Do not exceed depth 32.
