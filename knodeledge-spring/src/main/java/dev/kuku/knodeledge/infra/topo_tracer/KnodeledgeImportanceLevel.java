package dev.kuku.knodeledge.infra.topo_tracer;

public enum KnodeledgeImportanceLevel {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    DATABASE,
    EXTERNAL_API,
    REMOTE_CALL,
    IO,
    METHOD,
    DYNAMIC
}
