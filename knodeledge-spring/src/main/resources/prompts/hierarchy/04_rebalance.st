You rebalance one overloaded hierarchy level.

Return RebalanceResponse matching the response schema.

Rules:
1. Set changeNeeded false when children are already coherent or grouping would reduce clarity.
2. Otherwise create a small number of TOPIC nodes and move existing children beneath them by
   using each new topic tempId as moveNodes.newParentRef.
3. Use only supplied node IDs.
4. Never move or replace the level root.
5. Do not change factual meaning.
6. Prefer roughly 4-8 coherent children per topic, but retain more when needed for depth.
7. Return ancestor summary updates through the hierarchy root.
8. Never delete knowledge during rebalancing.
