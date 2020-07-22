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

package depends.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import depends.relations.Inferer;

/**
 * ContainerEntity for example file, class, method, etc.
 * they could contain vars, functions, ecpressions, type parameters, etc.
 */
public abstract class ContainerEntity extends DecoratedEntity {
	private static final Logger logger = LoggerFactory.getLogger(ContainerEntity.class);

	private ArrayList<depends.entity.VarEntity> vars;
	private ArrayList<depends.entity.FunctionEntity> functions;
	private HashMap<Object, depends.entity.Expression> expressions;
	private ArrayList<depends.entity.Expression> expressionList;
	private Collection<String> mixins;
	private Collection<ContainerEntity> resolvedMixins;

	public ContainerEntity(String rawName, depends.entity.Entity parent, Integer id) {
		super(rawName, parent, id);
		vars = new ArrayList<>();
		functions = new ArrayList<>();
		mixins = new ArrayList<>();
		resolvedMixins = new ArrayList<>();
		expressions = new HashMap<>();
		expressionList = new  ArrayList<>();
	}
	


	public void addVar(depends.entity.VarEntity var) {
		if (logger.isDebugEnabled()) {
			logger.debug("var found: "+var.getRawName() +  ":" + var.getRawType());
		}
		this.vars.add(var);
	}

	public ArrayList<depends.entity.VarEntity> getVars() {
		return this.vars;
	}

	public void addFunction(depends.entity.FunctionEntity functionEntity) {
		this.functions.add(functionEntity);
	}

	public ArrayList<depends.entity.FunctionEntity> getFunctions() {
		return this.functions;
	}

	public HashMap<Object, depends.entity.Expression> expressions() {
		return expressions;
	}

	public void addExpression(Object key, depends.entity.Expression expression) {
		expressions.put(key, expression);
		expressionList.add(expression);
	}

	

	/**
	 * For all data in the class, infer their types.
	 * Should be override in sub-classes 
	 */
	public void inferLocalLevelEntities(Inferer inferer) {
		super.inferLocalLevelEntities(inferer);
		for (depends.entity.VarEntity var : this.vars) {
			var.inferLocalLevelEntities(inferer);
		}
		for (depends.entity.FunctionEntity func:this.functions) {
			func.inferLocalLevelEntities(inferer);
		}
		resolvedMixins = identiferToContainerEntity(inferer, mixins);
		if (inferer.isEagerExpressionResolve()) {
			this.resolveExpressions(inferer);
		}
	}

	private Collection<ContainerEntity> identiferToContainerEntity(Inferer inferer, Collection<String> identifiers) {
		ArrayList<ContainerEntity> r = new ArrayList<>();
		for (String identifier : identifiers) {
			depends.entity.Entity entity = inferer.resolveName(this, identifier, true);
			if (entity==null) {
				continue;
			}
			if (entity instanceof ContainerEntity)
				r.add((ContainerEntity)entity);
		}
		return r;
	}

	/**
	 * Resolve all expression's type
	 * @param inferer
	 */
	public void resolveExpressions(Inferer inferer) {
		for (depends.entity.Expression expression : expressionList) {
			//1. if expression's type existed, break;
			if (expression.getType() != null)
				continue;
			if (expression.isDot){ //wait for previous
				continue;
			}
			if (expression.rawType==null && expression.identifier ==null)
				continue;
			
			//2. if expression's rawType existed, directly infer type by rawType
			//   if expression's rawType does not existed, infer type based on identifiers
			if (expression.rawType != null) {
				expression.setType(inferer.inferTypeFromName(this, expression.rawType),null,inferer);
				if (expression.getType() !=null) {
					 continue;
				}
			}
			if (expression.identifier!=null) { 
				Entity entity = inferer.resolveName(this, expression.identifier, true);
				if (entity!=null) {
					expression.setType(entity.getType(),entity,inferer);
					continue;
				}
				if (expression.isCall) {
					depends.entity.FunctionEntity func = this.lookupFunctionInVisibleScope(expression.identifier);
					if (func!=null) {
						expression.setType(func.getType(),func,inferer);
					}
				}else {
					
					depends.entity.VarEntity varEntity = this.lookupVarInVisibleScope(expression.identifier);
					if (varEntity!=null) {
						expression.setType( varEntity.getType(),varEntity,inferer);
					}
				}
			}
		}
	}

	public TypeEntity getLastExpressionType() {
		for (int i=this.expressionList.size()-1;i>=0;i--) {
			depends.entity.Expression expr= this.expressionList.get(i);
			if (expr.isStatement)
				return expr.getType();
		}
		return null;
	}

	public String dumpExpressions() {
		StringBuilder sb = new StringBuilder();
		for (Expression exp:expressionList) {
			sb.append(exp.toString()).append("\n");
		}
		return sb.toString();
	}


	
	/**
	 * The entry point of lookup functions. It will treat multi-declare entities and normal
	 * entity differently.
	 * - for multiDeclare entity, it means to lookup all entities
	 * - for normal entity, it means to lookup entities from current scope still root 
	 * @param functionName
	 * @return
	 */
	public depends.entity.FunctionEntity lookupFunctionInVisibleScope(String functionName) {
		if (this.getMutliDeclare()!=null) {
			for (ContainerEntity fromEntity:this.getMutliDeclare().getEntities()) {
				depends.entity.FunctionEntity f = lookupFunctionBottomUpTillTopContainer(functionName, fromEntity);
				if (f!=null)
					return f;
			}
		}else {
			ContainerEntity fromEntity = this;
			return lookupFunctionBottomUpTillTopContainer(functionName, fromEntity);
		}
		return null;
	}

	/**
	 * lookup function bottom up till the most outside container
	 * @param functionName
	 * @param fromEntity
	 * @return
	 */
	private depends.entity.FunctionEntity lookupFunctionBottomUpTillTopContainer(String functionName, ContainerEntity fromEntity) {
		while (fromEntity != null) {
			if (fromEntity instanceof ContainerEntity) {
				depends.entity.FunctionEntity func = ((ContainerEntity) fromEntity).lookupFunctionLocally(functionName);
				if (func != null)
					return func;
			}
			fromEntity = (ContainerEntity) this.getAncestorOfType(ContainerEntity.class);
		}
		return null;
	}
	
	/**
	 * lookup function in local entity. 
	 * It could be override such as the type entity (it should also lookup the 
	 * inherit/implemented types
	 * @param functionName
	 * @return
	 */
	public depends.entity.FunctionEntity lookupFunctionLocally(String functionName) {
		for (FunctionEntity func : getFunctions()) {
			if (func.getRawName().equals(functionName))
				return func;
		}
		return null;
	}

	/**
	 * The entry point of lookup var. It will treat multi-declare entities and normal
	 * entity differently.
	 * - for multiDeclare entity, it means to lookup all entities
	 * - for normal entity, it means to lookup entities from current scope still root 
	 * @param varName
	 * @return
	 */
	public depends.entity.VarEntity lookupVarInVisibleScope(String varName) {
		ContainerEntity fromEntity = this;
		return lookupVarBottomUpTillTopContainer(varName, fromEntity);
	}

	/**
	 * To found the var. 
	 * @param fromEntity
	 * @param varName
	 * @return
	 */
	private depends.entity.VarEntity lookupVarBottomUpTillTopContainer(String varName, ContainerEntity fromEntity) {
		while (fromEntity != null) {
			if (fromEntity instanceof ContainerEntity) {
				depends.entity.VarEntity var = ((ContainerEntity) fromEntity).lookupVarLocally(varName);
				if (var != null)
					return var;
			}
			fromEntity = (ContainerEntity) this.getAncestorOfType(ContainerEntity.class);
		}
		return null;
	}

	public depends.entity.VarEntity lookupVarLocally(String varName) {
		for (VarEntity var:getVars()) {
			if (var.getRawName().equals(varName))
				return var;
		}
		return null;
	}

	public void addMixin(String moduleName) {
		mixins.add(moduleName);
	}

	public Collection<ContainerEntity> getResolvedMixins() {
		return resolvedMixins;
	}
}
