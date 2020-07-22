package org.evosuite.tom;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class RepoUtils {
    private Git git;
    private Repository repo;
    private String srcPath;
    private RevWalk revWalk;

    public RepoUtils(Git git, String srcPath) {
        this.git = git;
        this.repo = git.getRepository();
        this.revWalk = new RevWalk(git.getRepository());
        this.srcPath = srcPath;
    }

    public void checkout(RevCommit commit) {
        try {
            git.checkout().setName(commit.getName()).call();
//        git.checkout().setCreateBranch( true ).setName( "new-branch" ).setStartPoint( "<id-to-commit>" ).call();
            git.clean().setCleanDirectories(true).call();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
    }

    public List<RevCommit> getMergeInvolvedCommits(String mergeSHA) {
        List<RevCommit> revCommits = new ArrayList<>();
        try {
            ObjectId commitSHA = ObjectId.fromString(mergeSHA);
            RevCommit merge = revWalk.parseCommit(commitSHA);

            revWalk.setRevFilter(RevFilter.MERGE_BASE);
            for (RevCommit parentCommit : merge.getParents()) {
                revWalk.markStart(parentCommit);
            }
            RevCommit mergeBase = revWalk.next();
            revCommits.add(mergeBase);
            revCommits.addAll(Arrays.asList(merge.getParents()));
            revCommits.add(merge);
            revWalk.dispose();
        } catch (IncorrectObjectTypeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return revCommits;
    }

    public String gitCatFile(RevCommit commit, String filePath){
        ObjectReader reader = repo.newObjectReader();
        RevTree tree = commit.getTree();
        try {
            TreeWalk treeWalk = TreeWalk.forPath(reader, filePath, tree);
            if(treeWalk!=null){
                byte[] data = reader.open(treeWalk.getObjectId(0)).getBytes();
                String sourceCode = new String(data,"utf-8");
                return sourceCode;
            }
        } catch (IOException e) {
            return "";
        }
        return "";
    }

    public List<String> getNonDeletedEntities(RevCommit leftCommit, RevCommit rightCommit){
        List<String> changedEntities = getChangedEntities(leftCommit,rightCommit);
        List<String> modifiedEntities = new ArrayList<>();
        for(String changedEntity: changedEntities){
            String status = changedEntity.split(":")[0];
            if(!status.equals("D")){
                modifiedEntities.add(changedEntity.substring(2));
            }
        }
        return modifiedEntities;
    }

    private boolean isJavaClass(String filePath){
        if(filePath.endsWith(".java") && filePath.contains(this.srcPath)){
            if(!filePath.endsWith("package-info.java")){
                return true;
            }
        }
        return false;
    }

    public List<String> getChangedEntities(RevCommit leftCommit, RevCommit rightCommit){
        if(leftCommit==null){
            System.out.println("ERROR: the left commit is null");
            return null;
        }
        List<DiffEntry> diffEntries = getDiffEntries(leftCommit,rightCommit);
        List<String> changedEntities = new ArrayList<>();
        for(DiffEntry diffEntry: diffEntries){
            String oldPath = diffEntry.getOldPath();
            String newPath = diffEntry.getNewPath();
            if(oldPath.equals("/dev/null") && isJavaClass(newPath)){
                String source = gitCatFile(rightCommit, newPath);
                String className = newPath.substring(newPath.lastIndexOf(File.separator)+1).split("\\.")[0];
                for(String entity: SourceParser.getInstance().getEntities(className, source).keySet()){
                    changedEntities.add("A:"+entity);
                }
            }else if(isJavaClass(oldPath) && newPath.equals("/dev/null")){
                changedEntities.add("D:"+oldPath);
            }else{
                if(oldPath.equals(newPath)){
                    if(isJavaClass(newPath))
                        changedEntities.addAll(getChangedEntities(leftCommit,rightCommit,oldPath));
                }
            }
        }
        return changedEntities;
    }

    private List<String> getChangedEntities(RevCommit left, RevCommit right, String filePath){
        String className = filePath.substring(filePath.lastIndexOf(File.separator)+1).split("\\.")[0];
        String sourceLeft = gitCatFile(left, filePath);
        Map<String,List<String>> entitiesLeft = SourceParser.getInstance().getEntities(className, sourceLeft);
        String sourceRight = gitCatFile(right, filePath);
        Map<String,List<String>> entitiesRight = SourceParser.getInstance().getEntities(className, sourceRight);

        List<String> changedEntities = new ArrayList<>();

        List<String> leftLines = new ArrayList<>();
        List<String> rightLines = new ArrayList<>();
        for(String entityLeft: entitiesLeft.keySet()){
            boolean flag=false;
            for(String entityRight: entitiesRight.keySet()){
                if(entityLeft.equals(entityRight)){
                    leftLines.addAll(entitiesLeft.get(entityLeft));
                    rightLines.addAll(entitiesRight.get(entityRight));
                    if(leftLines.size()!=rightLines.size()){
                        changedEntities.add("M:"+entityLeft);
                    }else{
                        leftLines.removeAll(rightLines);
                        if(leftLines.size()>0){
                            changedEntities.add("M:"+entityLeft);
                        }
                    }
                    leftLines.clear();
                    rightLines.clear();
                    flag=true;
                    break;
                }
            }
            if(!flag){
                changedEntities.add("D:"+entityLeft);
            }
        }

        for(String entityRight:entitiesRight.keySet()){
            boolean flag=false;
            for(String methodLeft:entitiesLeft.keySet()){
                if(entityRight.equals(methodLeft)){
                    flag=true;
                    break;
                }
            }
            if(!flag){
                changedEntities.add("A:"+entityRight);
            }
        }
        return changedEntities;
    }

//    public boolean nonCommentChange(FileHeader fileHeaderLeft, FileHeader fileHeaderRight, String sourceCode) {
//        SourceParser sourceParser = new SourceParser();
//        List<Integer> commentLines = sourceParser.getCommentLines(sourceCode);
//        boolean flagLeft = false;
//        for (HunkHeader hunkHeader : (List<HunkHeader>) fileHeaderLeft.getHunks()) {
//            for (Edit edit : hunkHeader.toEditList()) {
//                for (int i = edit.getBeginA(); i < edit.getBeginA() + edit.getLengthA(); i++) {
//                    if (!commentLines.contains(i)) {
//                        flagLeft = true;
//                    }
//                }
//            }
//        }
//
//        boolean flagRight = false;
//        for (HunkHeader hunkHeader : (List<HunkHeader>) fileHeaderRight.getHunks()) {
//            for (Edit edit : hunkHeader.toEditList()) {
//                for (int i = edit.getBeginA(); i < edit.getBeginA() + edit.getLengthA(); i++) {
//                    if (!commentLines.contains(i)) {
//                        flagRight = true;
//                    }
//                }
//            }
//        }
//        if (flagLeft && flagRight) {
//            return true;
//        }
//        return false;
//    }

    public DiffEntry getDiffEntry(RevCommit leftCommit, RevCommit rightCommit, String filePath) {

        AbstractTreeIterator oldTree = prepareTreeParser(leftCommit);
        AbstractTreeIterator newTree = prepareTreeParser(rightCommit);

        List<DiffEntry> diff = null;
        try {
            diff = git.diff().setOldTree(oldTree).setNewTree(newTree).setPathFilter(PathFilter.create(filePath)).call();
            if (diff == null || diff.size() != 1) {
                System.out.println("ERROR");
            }
            return diff.get(0);
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<DiffEntry> getDiffEntries(RevCommit leftCommit, RevCommit rightCommit) {
        AbstractTreeIterator oldTree = prepareTreeParser(leftCommit);
        AbstractTreeIterator newTree = prepareTreeParser(rightCommit);

        List<DiffEntry> diff = null;
        try {
            diff = git.diff().setOldTree(oldTree).setNewTree(newTree).setShowNameAndStatusOnly(true).call();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return diff;
    }

    private List<String> getRevTree(RevCommit commit){
        List<String> files = new ArrayList<>();
        ObjectId treeId = commit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repo)) {
            treeWalk.reset(treeId);
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                files.add(path);
            }
        } catch (IncorrectObjectTypeException e) {
            e.printStackTrace();
        } catch (CorruptObjectException e) {
            e.printStackTrace();
        } catch (MissingObjectException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }

    public Map<String,List<Integer>> getDiffLines(RevCommit leftCommit, RevCommit rightCommit) {
        Map<String,List<Integer>> diffLines = new HashMap<>();
        List<DiffEntry> diff = getDiffEntries(leftCommit, rightCommit);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiffFormatter df = new DiffFormatter(out);
        df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
        df.setRepository(repo);

        for (DiffEntry diffEntry : diff) {
            System.out.println(diffEntry.toString());
            if(!isJavaClass(diffEntry.getNewPath())){
                continue;
            }
            DiffEntry.ChangeType changeType  = diffEntry.getChangeType();
            if(changeType==DiffEntry.ChangeType.ADD|| changeType== DiffEntry.ChangeType.MODIFY){
                try {
                    List<Integer> locs = new ArrayList<>();
                    for (Edit edit : df.toFileHeader(diffEntry).toEditList()) {
                        System.out.println(edit.toString());
                        for(int i=edit.getBeginB();i<edit.getEndB();i++){
                            locs.add(i);
                        }
                    }
                    diffLines.put(diffEntry.getNewPath(),locs);
                }catch (Exception e){

                }
            }
        }
        return diffLines;
    }

    public boolean isEqualTree(RevCommit leftCommit, RevCommit rightCommit) {
        boolean isEqual = false;

        AbstractTreeIterator oldTree = prepareTreeParser(leftCommit);
        AbstractTreeIterator newTree = prepareTreeParser(rightCommit);

        List<DiffEntry> diff = null;
        try {
            diff = git.diff().setOldTree(oldTree).setNewTree(newTree).setShowNameAndStatusOnly(true).call();
            if (diff == null || diff.size() == 0) {
                isEqual = true;
            }
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return isEqual;
    }

    private AbstractTreeIterator prepareTreeParser(RevCommit commit) {
        try {
            RevTree tree = revWalk.parseTree(commit.getTree().getId());
            CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
            try (ObjectReader oldReader = repo.newObjectReader()) {
                oldTreeParser.reset(oldReader, tree.getId());
            }
            revWalk.dispose();
            return oldTreeParser;
        } catch (Exception e) {
            // TODO: handle exception
        }
        return null;
    }

    public Repository getRepo() {
        return repo;
    }

}
