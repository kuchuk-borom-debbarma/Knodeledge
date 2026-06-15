# Graph Report - Knodeledge  (2026-06-15)

## Corpus Check
- 43 files · ~13,789 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 181 nodes · 256 edges · 20 communities detected
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 39 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]

## God Nodes (most connected - your core abstractions)
1. `GraphPatchProcessor` - 22 edges
2. `GraphPatchProcessorTests` - 10 edges
3. `LLMAIService` - 10 edges
4. `InMemoryGraphRepository` - 8 edges
5. `InMemoryContextBoundaryRepository` - 6 edges
6. `InMemoryAuthService` - 6 edges
7. `GraphRepository` - 5 edges
8. `InMemoryUserRepository` - 5 edges
9. `AuthService` - 5 edges
10. `StubContextBoundaryService` - 5 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "Community 0"
Cohesion: 0.23
Nodes (1): GraphPatchProcessor

### Community 1 - "Community 1"
Cohesion: 0.13
Nodes (6): ContextBoundaryRepository, ContextBoundaryService, ContextBoundaryController, InMemoryContextBoundaryRepository, InMemoryContextBoundaryService, StubContextBoundaryService

### Community 2 - "Community 2"
Cohesion: 0.16
Nodes (5): AuthService, AuthController, InMemoryAuthService, InMemoryUserRepository, UserRepository

### Community 3 - "Community 3"
Cohesion: 0.16
Nodes (5): GraphController, GraphRepository, GraphService, InMemoryGraphRepository, InMemoryGraphService

### Community 4 - "Community 4"
Cohesion: 0.24
Nodes (3): AIService, AIController, LLMAIService

### Community 5 - "Community 5"
Cohesion: 0.44
Nodes (1): GraphPatchProcessorTests

### Community 6 - "Community 6"
Cohesion: 0.33
Nodes (1): GraphRepository

### Community 7 - "Community 7"
Cohesion: 0.33
Nodes (1): AuthService

### Community 8 - "Community 8"
Cohesion: 0.4
Nodes (1): TracingHookConfiguration

### Community 9 - "Community 9"
Cohesion: 0.5
Nodes (1): KnodeledgeTracingAspect

### Community 10 - "Community 10"
Cohesion: 0.4
Nodes (1): ContextBoundaryRepository

### Community 11 - "Community 11"
Cohesion: 0.4
Nodes (1): UserRepository

### Community 12 - "Community 12"
Cohesion: 0.4
Nodes (1): ContextBoundaryService

### Community 13 - "Community 13"
Cohesion: 0.5
Nodes (1): KnodeledgeSpringApplication

### Community 14 - "Community 14"
Cohesion: 0.5
Nodes (1): GraphService

### Community 15 - "Community 15"
Cohesion: 0.5
Nodes (1): AIService

### Community 16 - "Community 16"
Cohesion: 0.67
Nodes (1): KnodeledgeSpringApplicationTests

### Community 17 - "Community 17"
Cohesion: 0.67
Nodes (1): KnodeledgeImportance

### Community 18 - "Community 18"
Cohesion: 1.0
Nodes (1): GraphDto

### Community 19 - "Community 19"
Cohesion: 1.0
Nodes (1): LLMFlowDto

## Knowledge Gaps
- **2 isolated node(s):** `GraphDto`, `LLMFlowDto`
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 0`** (23 nodes): `GraphPatchProcessor`, `.apply()`, `.completeReferences()`, `.count()`, `.edgeKey()`, `.findCategoryEdgeKey()`, `.isBlank()`, `.list()`, `.nodesById()`, `.normalizeNode()`, `.putEdges()`, `.putNodes()`, `.requireCount()`, `.requireNonNullPatch()`, `.requireSemanticEdge()`, `.roleNodes()`, `.validateConditionTree()`, `.validateEdgeFields()`, `.validateEdgeRef()`, `.validateFinalGraph()`, `.validateNode()`, `.validateStructuralNodes()`, `GraphPatchProcessor.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 5`** (11 nodes): `GraphPatchProcessorTests`, `.appliesValidConditionalStatementGraph()`, `.deletesSupersededEdgeAndOrphanNode()`, `.edge()`, `.node()`, `.rejectsCategoryCacheWithoutTaxonomyEdge()`, `.rejectsDanglingEdge()`, `.rejectsNewPlaceholderPredicate()`, `.rejectsStatementWhoseWhenTargetsEntity()`, `.restoresNodeDroppedByPatchValidator()`, `GraphPatchProcessorTests.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 6`** (6 nodes): `GraphRepository.java`, `GraphRepository`, `.findEdgesByBoundaryId()`, `.findNodesByBoundaryId()`, `.saveEdges()`, `.saveNodes()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 7`** (6 nodes): `AuthService`, `.authenticate()`, `.createUser()`, `.getUserById()`, `.getUserByUsername()`, `AuthService.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 8`** (5 nodes): `TracingHookConfiguration.java`, `TracingHookConfiguration`, `.chatClient()`, `.slf4jLogHook()`, `.topoTracerCustomizer()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 9`** (5 nodes): `KnodeledgeTracingAspect.java`, `KnodeledgeTracingAspect`, `.getKnodeledgeImportance()`, `.KnodeledgeTracingAspect()`, `.traceMethod()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 10`** (5 nodes): `ContextBoundaryRepository.java`, `ContextBoundaryRepository`, `.findById()`, `.findByUserId()`, `.save()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 11`** (5 nodes): `UserRepository.java`, `UserRepository`, `.findById()`, `.findByUsername()`, `.save()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 12`** (5 nodes): `ContextBoundaryService`, `.createContextBoundary()`, `.getContextBoundariesByUserId()`, `.getContextBoundaryById()`, `ContextBoundaryService.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 13`** (4 nodes): `KnodeledgeSpringApplication`, `.main()`, `.restTemplate()`, `KnodeledgeSpringApplication.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 14`** (4 nodes): `GraphService`, `.getCompleteGraphByBoundaryId()`, `.saveGraph()`, `GraphService.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 15`** (4 nodes): `AIService`, `.ingestNote()`, `.promptGraph()`, `AIService.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 16`** (3 nodes): `KnodeledgeSpringApplicationTests`, `.contextLoads()`, `KnodeledgeSpringApplicationTests.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 17`** (3 nodes): `KnodeledgeImportance.java`, `KnodeledgeImportance`, `.KnodeledgeImportance()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 18`** (2 nodes): `GraphDto.java`, `GraphDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 19`** (2 nodes): `LLMFlowDto.java`, `LLMFlowDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `InMemoryContextBoundaryService` connect `Community 1` to `Community 2`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **What connects `GraphDto`, `LLMFlowDto` to the rest of the system?**
  _2 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.13 - nodes in this community are weakly interconnected._