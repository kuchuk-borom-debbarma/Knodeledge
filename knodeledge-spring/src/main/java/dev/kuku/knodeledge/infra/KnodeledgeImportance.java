package dev.kuku.knodeledge.infra;

import dev.kuku.topotracer.sdk.TypedImportance;

public class KnodeledgeImportance extends TypedImportance<KnodeledgeImportanceLevel> {
    public static final KnodeledgeImportance CONTROLLER = new KnodeledgeImportance(KnodeledgeImportanceLevel.CONTROLLER, 0, "Controller");
    public static final KnodeledgeImportance SERVICE = new KnodeledgeImportance(KnodeledgeImportanceLevel.SERVICE, 0, "Service");
    public static final KnodeledgeImportance REPOSITORY = new KnodeledgeImportance(KnodeledgeImportanceLevel.REPOSITORY, 1, "Repository");
    public static final KnodeledgeImportance DATABASE = new KnodeledgeImportance(KnodeledgeImportanceLevel.DATABASE, 1, "Database");
    public static final KnodeledgeImportance EXTERNAL_API = new KnodeledgeImportance(KnodeledgeImportanceLevel.EXTERNAL_API, 1, "External API");
    public static final KnodeledgeImportance REMOTE_CALL = new KnodeledgeImportance(KnodeledgeImportanceLevel.REMOTE_CALL, 1, "Remote Call");
    public static final KnodeledgeImportance IO = new KnodeledgeImportance(KnodeledgeImportanceLevel.IO, 2, "I/O");
    public static final KnodeledgeImportance METHOD = new KnodeledgeImportance(KnodeledgeImportanceLevel.METHOD, 3, "Method");
    public static final KnodeledgeImportance DYNAMIC = new KnodeledgeImportance(KnodeledgeImportanceLevel.DYNAMIC, -1, "Dynamic");

    private KnodeledgeImportance(KnodeledgeImportanceLevel value, int level, String label) {
        super(value, level, label);
    }
}
