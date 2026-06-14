package dev.kuku.knodeledge.services.ai.dto;

import java.util.List;

public record Kgraph(
    List<Knode> nodes,
    List<Kedge> edges
) {}
