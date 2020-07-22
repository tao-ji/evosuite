package depends.relations;

import depends.entity.Entity;

public class ReverseRelation {
    private String type;
    private Entity fromEntity;

    public ReverseRelation(String type, Entity fromEntity) {
        this.fromEntity = fromEntity;
        this.type = type;
    }
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Relation[" + type + "]<--" + fromEntity.getId() + "(" + fromEntity.getQualifiedName() + ")";
    }
    public Entity getEntity() {
        return fromEntity;
    }

}
