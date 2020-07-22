/*
MIT License

Copyright (c) 2018-2019 Gang ZHANG

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package depends.entity.repo;

import java.util.*;

import depends.entity.Entity;
import depends.entity.MultiDeclareEntities;
import depends.relations.Relation;
import depends.relations.ReverseRelation;

public class EntityRepo extends IdGenerator {
	// allEntitiesByName is constructed before updating the method's qualifiedName,
	// we would update the keys of this map after the construction of the whole entityrepo
    // and we add the varEntities into the map
	private HashMap<String, Entity> allEntieisByName = new HashMap<>();
	private HashMap<Integer, Entity> allEntitiesById = new HashMap<>();
	private List<Entity> allEntitiesByOrder = new ArrayList<>();
	public static final String GLOBAL_SCOPE_NAME = "::GLOBAL::";

	public EntityRepo() {
	}

	public Entity getEntity(String entityName) {
		Entity entity = allEntieisByName.get(entityName);
		// the updated functionEntity may fail to be found due to the inconsistencies between representations of types
		if(entity==null){
			if(entityName.contains("(")) {
				for(Entity e: allEntitiesByOrder){
					if (e.getQualifiedName().startsWith(entityName.substring(0, entityName.lastIndexOf("(")))) {
						entity = e;
						break;
					}
				}
			}
		}
		return entity;
	}
	
	public Entity getEntity(Integer entityId) {
		return allEntitiesById.get(entityId);
	}
	
	public void add(Entity entity) {
		allEntitiesByOrder.add(entity);
		allEntitiesById.put(entity.getId(), entity);
		String name = entity.getRawName();
		if (entity.getQualifiedName()!=null && !(entity.getQualifiedName().isEmpty()) ) {
			name = entity.getQualifiedName();
		}
		if (allEntieisByName.containsKey(name)) {
			Entity existedEntity = allEntieisByName.get(name);
			if (existedEntity instanceof MultiDeclareEntities) {
				((MultiDeclareEntities)existedEntity).add(entity);
			}else {
				MultiDeclareEntities eMultiDeclare = new MultiDeclareEntities(existedEntity,this.generateId());
				eMultiDeclare.add(entity);
				allEntieisByName.put(name, eMultiDeclare);
			}
		}else {
			allEntieisByName.put(name, entity);
		}
		if (entity.getParent()!=null)
			this.setParent(entity, entity.getParent());
	}
		
	public Collection<Entity> getEntities() {
		return allEntitiesByOrder;
	}
	
	public void setParent(Entity child, Entity parent) {
		if (parent==null) return;
		if (child==null) return;
		if (parent.equals(child.getParent())) return;
		child.setParent(parent);
		parent.addChild(child);
	}

	public void clear(){
		for(Entity entity: allEntitiesByOrder){
			entity.getRelations().clear();
			entity = null;
		}
		allEntitiesByOrder.clear();
		allEntieisByName.clear();
		allEntitiesById.clear();
	}

	// the qualifiedname of methods would be updated (adding parameters) after added above
	private void updateMap(){
		allEntieisByName.clear();
		// varEntities are not added into entityRepo
		Set<Entity> allEntities = new HashSet();
	    for(Entity entity: allEntitiesByOrder) {
			allEntities.add(entity);
			for (Relation relation : entity.getRelations()) {
				allEntities.add(relation.getEntity());
			}
		}
	    for(Entity entity: allEntities){
	    	String name = entity.getRawName();
	    	if (entity.getQualifiedName()!=null && !(entity.getQualifiedName().isEmpty()) ) {
	    		name = entity.getQualifiedName();
	    	}
	    	if (allEntieisByName.containsKey(name)) {
	    		Entity existedEntity = allEntieisByName.get(name);
				if (existedEntity instanceof MultiDeclareEntities) {
					((MultiDeclareEntities)existedEntity).add(entity);
				}else {
					MultiDeclareEntities eMultiDeclare = new MultiDeclareEntities(existedEntity,this.generateId());
					eMultiDeclare.add(entity);
					allEntieisByName.put(name, eMultiDeclare);
				}
			}else {
				allEntieisByName.put(name, entity);
			}

	    	if(entity.getQualifiedName()!=null && !entity.getQualifiedName().isEmpty()){
	    		allEntieisByName.put(entity.getQualifiedName(),entity);
			}else{
	    		allEntieisByName.put(entity.getRawName(),entity);
			}
		}
	    allEntities.clear();
	}

	//this method is used in TOM only
	public void constructReverseRelations(){
		updateMap();
		for(Entity entity: allEntitiesByOrder){
			for(Relation relation: entity.getRelations()){
				Entity toEntity = relation.getEntity();
				ReverseRelation reverseRelation = new ReverseRelation(relation.getType(),entity);
				toEntity.addReverseRelation(reverseRelation);
			}
		}
	}
}
