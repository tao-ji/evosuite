package org.evosuite.tom;

public class ImpactedEntity {
    private String changedEntity;
    private String impactedEntity;
    private int depth;

    public ImpactedEntity(String changedEntity, String impactedEntity, int depth){
        this.changedEntity = changedEntity;
        this.impactedEntity = impactedEntity;
        this.depth = depth;
    }

    public String getChangedEntity(){
        return this.changedEntity;
    }

    public String getImpactedEntity(){
        return this.impactedEntity;
    }

    public int getDepth(){
        return this.depth;
    }
}
