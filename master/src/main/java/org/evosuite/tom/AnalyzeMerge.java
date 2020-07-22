package org.evosuite.tom;

import depends.LangRegister;
import depends.entity.Entity;
import depends.entity.repo.EntityRepo;
import depends.extractor.AbstractLangProcessor;
import depends.extractor.LangProcessorRegistration;
import depends.relations.Relation;
import depends.relations.ReverseRelation;
import depends.util.FileUtil;
import depends.util.FolderCollector;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.util.*;

public class AnalyzeMerge {

    private String gitFilePath;
    private RepoUtils repoUtils;
    private Map<String,Set<String>> toTestMethods;
    private Set<String> checkedOutFiles;
    private final int depth;
    private final int maxUUT;
    private boolean build;
    private String srcPath;

    private HashMap<String, Set<String>> extendedClasses = new HashMap<>();

    public AnalyzeMerge(String gitFilePath, String srcPath, Git git, int depth, int maxUUT, boolean build) {
        this.gitFilePath = gitFilePath;
        this.srcPath = srcPath;
        this.repoUtils = new RepoUtils(git,srcPath);
        this.depth=depth;
        this.maxUUT = maxUUT;
        this.build = build;
        checkedOutFiles = new HashSet<>();
    }

    public Map<String,Set<String>> getUUTs(){
        return toTestMethods;
    }

    public boolean prepare(String mergeSHA) {

        List<RevCommit> mergeInvolvedCommits = repoUtils.getMergeInvolvedCommits(mergeSHA);
        toTestMethods = getUUTs(mergeSHA);

        if (toTestMethods != null) {
            if (toTestMethods.size() == 0) {
                System.out.println("* IGNORED: cannot find any UUT");
                return false;
            } else {
                FileUtils fileUtils = new FileUtils(gitFilePath, mergeInvolvedCommits.size());
                if(build){
                    fileUtils.initDir("jars");
                }
                fileUtils.initDir("src");

                CompileRepo compileRepo = new CompileRepo();
                for (int i = 0; i < mergeInvolvedCommits.size(); i++) {
                    RevCommit mergeInvolvedCommit = mergeInvolvedCommits.get(i);
                    repoUtils.checkout(mergeInvolvedCommit);
                    System.out.println("* checked out the version: "+mergeInvolvedCommit.getName());
                    if(build) {
                        boolean compileResult = compileRepo.compile(gitFilePath, "clean package -DskipTests dependency:copy-dependencies");
                        if (!compileResult)
                            return false;
                    }
                    fileUtils.copy(i, checkedOutFiles);
                }
                return true;
            }
        }
        return false;
    }

    private Map<String,Set<String>> getUUTs(String mergeSHA) {
        System.out.println("* Analyzing the commit:" + mergeSHA);
        List<RevCommit> allMergeInvolvedCommits = repoUtils.getMergeInvolvedCommits(mergeSHA);
        RevCommit base = allMergeInvolvedCommits.get(0);
        List<RevCommit> parents = allMergeInvolvedCommits.subList(1, allMergeInvolvedCommits.size() - 1);
        RevCommit merge = allMergeInvolvedCommits.get(allMergeInvolvedCommits.size() - 1);

        if (parents.contains(base)) {
            System.out.println("* IGNORED: this is one fast-forward merge");
            return null;
        }
        for (RevCommit parent : parents) {
            if (repoUtils.isEqualTree(parent, merge)) {
                System.out.println("* IGNORED: the merge is the same with one of its parents");
                return null;
            }
            if (repoUtils.getDiffLines(parent,merge).size() == 0) {
                System.out.println("* IGNORED: the merge does not modify any file when compared to one of its parents");
                return null;
            }
        }

        if (base == null) {
            if (parents.size() == 2) {
                System.out.println("2-way merge");
            } else {
                System.out.println("NOTE: I have not seen this case");
            }
        } else {
            if (parents.size() == 2) {
                System.out.println("3-way merge");
            } else {
                System.out.println("octopus merge");
            }
        }

        Map<String, Set<String>> allUUTs = new HashMap<>();
        EntityRepo mergeEntityRepo = getEntityRepo(gitFilePath, merge);
        mergeEntityRepo.constructReverseRelations();
        constructClassesWithParents(mergeEntityRepo);

        allUUTs.put("m"+parents.size(),getUUTs(mergeEntityRepo,parents,merge));
        // clear this map after being used in the getUUTs
        mergeEntityRepo.clear();
        extendedClasses.clear();

        if(base!=null){
            List<RevCommit> baseAndMerge = new ArrayList<>(Arrays.asList(base,merge));
            for(int i = 0;i<parents.size();i++){
                RevCommit parent = parents.get(i);
                EntityRepo parentEntityRepo = getEntityRepo(gitFilePath, parent);
                parentEntityRepo.constructReverseRelations();
                constructClassesWithParents(parentEntityRepo);

                String targetVersion = "p"+(i+1);
                allUUTs.put(targetVersion,getUUTs(parentEntityRepo, baseAndMerge, parent));
                parentEntityRepo.clear();
                extendedClasses.clear();
            }
        }
        return allUUTs;
    }

    private Set<String> intersection(List<List<ImpactedEntity>> allImpactedEntities){
        List<Set<String>> allImpactedEntityNames = new ArrayList<>();
        for(int i=0;i<allImpactedEntities.size();i++){
            List<ImpactedEntity> impactEntities = allImpactedEntities.get(i);
            Set<String> impactedEntityNames = new HashSet<>();
            for(ImpactedEntity impactedEntity: impactEntities){
                impactedEntityNames.add(impactedEntity.getImpactedEntity());
                System.out.println(impactedEntity.getChangedEntity()+":"+impactedEntity.getImpactedEntity()+":"+impactedEntity.getDepth());
                checkedOutFiles.add(impactedEntity.getChangedEntity());
            }
            allImpactedEntityNames.add(impactedEntityNames);
        }
        Set<String> uutNames = new HashSet<>();
        uutNames.addAll(allImpactedEntityNames.get(0));

        for(int i=1;i<allImpactedEntityNames.size();i++) {
            uutNames.retainAll(allImpactedEntityNames.get(i));
        }
        Set<String> uuts = new HashSet<>();
        for(String uutName: uutNames){
            if(uutName.contains("(")){
                uuts.add(uutName);
            }
        }
        return uuts;
    }

    public Set<String> getUUTs(EntityRepo entityRepo, List<RevCommit> commits, RevCommit targetCommit) {
        List<List<ImpactedEntity>> allImpactedEntities = new ArrayList<>();
        List<String> changedEntities = new ArrayList<>();

        for(int index=0;index<commits.size();index++){
            RevCommit commit = commits.get(index);
            allImpactedEntities.add(new ArrayList<>());
            for (String changedEntity : repoUtils.getNonDeletedEntities(commit, targetCommit)) {
                changedEntities.add(changedEntity);
                allImpactedEntities.get(index).add(new ImpactedEntity(changedEntity,changedEntity,0));
            }
        }

        Set<String> uuts = new HashSet<>();

        for(int i=1;i<=depth;i++){
            for(int index=0;index<commits.size();index++){
                List<ImpactedEntity> deeperImpactedEntities = new ArrayList<>();
                int count = 0;
                for(ImpactedEntity impactedEntity: allImpactedEntities.get(index)){
                    if(impactedEntity.getDepth()==i-1){
                        // arbitrary threshold
                        if(count>1000) break;
                        List<ImpactedEntity> newFoundImpactedEntities = getImpactedEntities(impactedEntity.getChangedEntity(),impactedEntity.getImpactedEntity(),entityRepo,i);
                        count+= newFoundImpactedEntities.size();
                        deeperImpactedEntities.addAll(newFoundImpactedEntities);
                    }
                }
                allImpactedEntities.get(index).addAll(deeperImpactedEntities);
            }
            uuts = intersection(allImpactedEntities);
            if(uuts.size() >= maxUUT){
                Set<String> tmp = new HashSet<>();
                for(String uut: uuts){
                    if(tmp.size()<maxUUT){
                        tmp.add(uut);
                    }
                }
                return tmp;
            }
        }
        if(uuts.size()==0){
            uuts.addAll(changedEntities);
        }
        return uuts;
    }

    private EntityRepo getEntityRepo(String gitFilePath, RevCommit commit) {

        System.out.println("* extracting the dependencies from the version: "+commit.getName());
        repoUtils.checkout(commit);

        LangRegister langRegister = new LangRegister();
        langRegister.register();

        String inputDir = FileUtil.uniqFilePath(gitFilePath+ File.separator+this.srcPath);
        AbstractLangProcessor langProcessor = LangProcessorRegistration.getRegistry().getProcessorOf("java");

        String[] includeDir = new String[]{};
        FolderCollector includePathCollector = new FolderCollector();
        List<String> additionalIncludePaths = includePathCollector.getFolders(inputDir);
        additionalIncludePaths.addAll(Arrays.asList(includeDir));
        includeDir = additionalIncludePaths.toArray(new String[]{});

        langProcessor.buildDependencies(inputDir, includeDir);
        return langProcessor.getEntityRepo();
    }

    private List<ImpactedEntity> getImpactedEntities(String changedEntity, String qualifiedName, EntityRepo entityRepo, int depth) {
//        System.out.println("qualifiedName:"+qualifiedName);
        String[] types = {"Contain","Parameter","Use","Call","Create","Return","Throw"};
        List<String> constructorTypes = new ArrayList<>();
        constructorTypes.addAll(Arrays.asList(types));

        List<ImpactedEntity> impactedEntities = new ArrayList<>();
		Set<String> parentsAndInterfaces = new HashSet<>();
		if(isMethod(qualifiedName)){
		    String className = getClassName(qualifiedName);
		    if(this.extendedClasses.containsKey(className)){
		        parentsAndInterfaces = this.extendedClasses.get(className);
            }else{
                parentsAndInterfaces = getParentsInterfaces(className);
                this.extendedClasses.put(className,parentsAndInterfaces);
            }
		}
		Set<String> toAnalzeCallers = new HashSet<>();

        Entity toEntity = entityRepo.getEntity(qualifiedName);
        if(toEntity==null){
            return impactedEntities;
        }

        //if it is the constructor, all methods call the methods in the class of the constructor should be analyzed
        //because we have to instantiate the class before invoking
        if(isConstructor(qualifiedName)) {
            Entity parent = toEntity.getParent();
            for(ReverseRelation reverseRelation: parent.getReverseRelations()) {
                String rType = reverseRelation.getType();
                if(constructorTypes.contains(rType)){
                    toAnalzeCallers.add(reverseRelation.getEntity().getQualifiedName());
                }
            }
        }

        for(ReverseRelation reverseRelation: toEntity.getReverseRelations()){
            Entity entity = reverseRelation.getEntity();
            if(reverseRelation.getType().equals("Use") || reverseRelation.getType().equals("Call")) {
                toAnalzeCallers.add(entity.getQualifiedName());
            }
		}
        // the method may be the implementation or the override method
        // if A calls B.m, and C.m implements B.m, we would add A
        if(isMethod(qualifiedName)) {
            String className = getClassName(qualifiedName);
            for (String parentClassInterface : parentsAndInterfaces) {
                Entity tmp = entityRepo.getEntity(qualifiedName.replace(className, parentClassInterface));
                if (tmp == null) {
                    continue;
                }
                for (ReverseRelation reverseRelation : tmp.getReverseRelations()) {
                    Entity entity = reverseRelation.getEntity();
                    if (reverseRelation.getType().equals("Use") || reverseRelation.getType().equals("Call")) {
                        continue;
                    }
                    toAnalzeCallers.add(entity.getQualifiedName());
                }
            }
        }
        if(isConstructor(qualifiedName)){
            Entity entity = entityRepo.getEntity(qualifiedName);
            if(entity!=null){
                for(Relation relation: entity.getRelations()) {
                    if (relation.getType().equals("Use")) {
                        Entity toEntity_1 = relation.getEntity();
                        String qualifiedNameToEntity = toEntity_1.getQualifiedName();
                        if (!qualifiedNameToEntity.contains(".")) {
                            continue;
                        }
                        String parentName = qualifiedNameToEntity.substring(0, qualifiedNameToEntity.lastIndexOf("."));
                        if (!parentName.equals(qualifiedName.substring(0, qualifiedName.lastIndexOf("(")))) {
                            //if the constructor calls "FastMath.floor", depends would report FastMath is used
                            String simpleName = qualifiedNameToEntity.substring(qualifiedNameToEntity.lastIndexOf(".") + 1);
                            //the condition would remove all the fields with the upper case
                            //todo: rule out the classes
                            if (simpleName.charAt(0) != simpleName.toLowerCase().charAt(0)) {
                                continue;
                            }
                            toAnalzeCallers.add(relation.getEntity().getQualifiedName());
                        }
                    }
                }
            }
        }
        for(String toAnalyzeCaller: toAnalzeCallers){
            impactedEntities.add(new ImpactedEntity(changedEntity,toAnalyzeCaller,depth));
        }
        return impactedEntities;
    }

    private HashMap<String, Set<String>> classesWithParents = new HashMap<>();

    private void constructClassesWithParents(EntityRepo entityRepo){
        classesWithParents.clear();
        for(Entity entity: entityRepo.getEntities()){
            for(Relation relation: entity.getRelations()){
                if(relation.getType().equals("Extend")||relation.getType().equals("Implement")){
                    Entity toEntity = relation.getEntity();
                    if(!(toEntity.getQualifiedName().equals("external") || toEntity.getQualifiedName().equals("built-in"))){
                        if (!classesWithParents.containsKey(entity.getQualifiedName())) {
                            Set<String> parents = new HashSet<>();
                            classesWithParents.put(entity.getQualifiedName(), parents);
                        }
                        classesWithParents.get(entity.getQualifiedName()).add(relation.getEntity().getQualifiedName());
                    }
                }
            }
        }
    }

	private Set<String> getParentsInterfaces(String className){
		Set<String> parentsAndInterfaces = new HashSet<>();
		if(classesWithParents.containsKey(className)){
            parentsAndInterfaces.addAll(classesWithParents.get(className));
            for(String parent: classesWithParents.get(className)){
                parentsAndInterfaces.addAll(getParentsInterfaces(parent));
            }
        }
		return parentsAndInterfaces;
	}

	private boolean isConstructor(String qualifiedName){
        if(!isMethod(qualifiedName)){
            return false;
        }
		String className = getClassName(qualifiedName);
		String method = getMethodName(qualifiedName);
		if(className.endsWith(method)){
			return true;
		}
		return false;
	}

	private boolean isMethod(String qualifiedName){
        if(!qualifiedName.contains("(")){
            return false;
        }
        return true;
    }

	private String getClassName(String qualifiedName){
		String partialQualifiedName = qualifiedName.substring(0,qualifiedName.lastIndexOf("("));
		String className = partialQualifiedName.substring(0,partialQualifiedName.lastIndexOf("."));
		return className;
	}

	private String getMethodName(String qualifiedName){
        String partialQualifiedName = qualifiedName.substring(0,qualifiedName.lastIndexOf("("));
        String method = partialQualifiedName.substring(partialQualifiedName.lastIndexOf(".")+1);
        return method;
    }

	private String getMethodSignature(String qualifiedName){
        String method = qualifiedName.substring(qualifiedName.lastIndexOf(".")+1);
        return method;
	}


}
