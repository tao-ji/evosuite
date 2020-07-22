package org.evosuite.executionmode;

import org.apache.commons.cli.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.evosuite.EvoSuite;
import org.evosuite.utils.LoggingUtils;

import java.io.*;
import java.util.*;

public class TestMerge {
    public static final String NAME = "tom";

    public static Option[] getOptions() {
        return new Option[]{
                new Option(NAME, "TOM: generate tests for merges"),
                new Option("repoPath", true, "the repo's path"),
                new Option("jarsPath",true,"the path of jars"),
                new Option("jarName",true,"the name of jar"),
                new Option("targetCommit", true, "the sut"),
                new Option("mutantCommits", true, "the mutants should be killed"),
                new Option("uuts",true,"uuts"),
                new Option("timeBudget",true,"time budget"),
        };
    }

    public static Object execute(Options options, List<String> javaOpts, CommandLine line) {

        String target_commit=line.getOptionValue("targetCommit");
        String mutant_commits = line.getOptionValue("mutantCommits");
        DiffLinesExtractor diffLinesExtractor = new DiffLinesExtractor(line.getOptionValue("repoPath"),target_commit,mutant_commits);
        for(String uut: line.getOptionValue("uuts").split(":")){
            invokeTestGeneration(uut,line);
        }

        File diffLines=new File("/tmp/difflines-"+target_commit+":"+mutant_commits);
        diffLines.delete();
        return null;
    }

    private static void invokeTestGeneration(String uut, CommandLine line) {
        if(!uut.contains("(")){
            return;
        }
        List<String> argsList = new ArrayList<>();
        argsList.add("-projectCP");

        File jarsDir = new File(line.getOptionValue("jarsPath")+"/"+line.getOptionValue("targetCommit"));

        List<String> jarsList = new ArrayList<>();
        for (File f : jarsDir.listFiles()) {
            if(f.getAbsolutePath().endsWith(".jar")&&!f.getAbsolutePath().endsWith("tests.jar")){
                jarsList.add(f.getAbsolutePath());
            }
        }

        String cp = String.join(":", jarsList);
        argsList.add(cp);

        String[] parts = uut.split("\\.");
        String fileName = "";
        for(String part: parts){
            if(part.toLowerCase().equals(part)){
                fileName += (part+".");
            }else{
                fileName += part;
                break;
            }
        }

        String className = uut.substring(0,uut.lastIndexOf("("));
        String classToTest = className.substring(0,className.lastIndexOf("."));
        String methodToTest = className.substring(className.lastIndexOf(".")+1);

        argsList.add("-class");
        argsList.add(fileName);

        argsList.add("-Drepo_path");
        argsList.add(line.getOptionValue("repoPath"));

        argsList.add("-Djars_path");
        argsList.add(line.getOptionValue("jarsPath"));

        argsList.add("-Dtarget_commit");
        argsList.add(line.getOptionValue("targetCommit"));

        argsList.add("-Dmutant_commits");
        argsList.add(line.getOptionValue("mutantCommits"));

        argsList.add("-Dtarget_method_prefix");
        if(!classToTest.endsWith(methodToTest)){
            argsList.add(methodToTest);
        }else{
			argsList.add("<init>");
		}

        argsList.addAll(Arrays.asList("-criterion diffline:branch -Dstopping_condition mergeconflict".split(" ")));
        argsList.add("-Dglobal_timeout");
        String timeout = line.getOptionValue("timeBudget");
        if(timeout!=null){
            argsList.add(timeout);
        } else {
            argsList.add("180");
        }

            EvoSuite evosuite = new EvoSuite();
            System.out.println("command line");
            System.out.println(String.join(" ", argsList));
            evosuite.parseCommandLine(argsList.toArray(new String[argsList.size()]));
    }

    static class DiffLinesExtractor{
        private Git git;
        private Repository repo;
        private RevWalk revWalk;
        private Map<String,RevCommit> allCommits= new HashMap<>();
        private List<Map<String,Set<Integer>>> allDiffLines = new ArrayList<>();
        private String target_commit;
        private String mutant_commits;

        public DiffLinesExtractor(String repoPath, String target_commit, String mutant_commits){
            this.target_commit=target_commit;
            this.mutant_commits=mutant_commits;
            init(repoPath);
            diff(target_commit,mutant_commits);
            write();
        }

        private void write(){
            List<String> toWriteLines = new ArrayList<>();
            for(Map<String,Set<Integer>> difflines: allDiffLines){
                for(String clz: difflines.keySet()){
                    String line = clz;
                    line+=":";
                    for(int diffline: difflines.get(clz)){
                        line = line+diffline+",";
                    }
                    toWriteLines.add(line.substring(0,line.length()-1));
                }
                toWriteLines.add("---");
            }
            write2File("/tmp/difflines-"+this.target_commit+":"+this.mutant_commits,toWriteLines,false);
        }

        public RevCommit parseCommit(String sha){
            try{
                ObjectId id = repo.resolve(sha);
                RevCommit commit = revWalk.parseCommit(id);
                revWalk.reset();
                return commit;
            } catch (Exception e) {
                revWalk.reset();
                return null;
            }
        }

        private void init(String repoPath) {
            try {
                this.git = Git.open(new File(repoPath));
                this.repo = git.getRepository();
                this.revWalk = new RevWalk(git.getRepository());

                Ref head = repo.exactRef("refs/heads/master");
                // a RevWalk allows to walk over commits based on some filtering that is defined
                RevCommit headcommit = revWalk.parseCommit(head.getObjectId());
                revWalk.setRevFilter(RevFilter.ALL);
                revWalk.markStart(headcommit);
                while(true){
                    RevCommit commit = revWalk.next();
                    if(commit ==null) break;
                    if(!allCommits.containsKey(commit.getName())){
                        allCommits.put(commit.getName(),commit);
                    }else{
                        break;
                    }
                }
                revWalk.dispose();
            } catch (Exception e) {
                LoggingUtils.getEvoLogger().info("Something wrong:"+e.getMessage());
                e.printStackTrace();
            }
        }

        public List<Map<String,Set<Integer>>> diff(String target_commit, String mutant_commits){
            if(allDiffLines.size()==0){
                String[] mutants = mutant_commits.split(":");
                for(String mutant:mutants){
                    allDiffLines.add(getDiffLines(mutant,target_commit));
                }
            }
            return allDiffLines;
        }

        public RevCommit getCommit(String commit){
            return allCommits.get(commit);
        }

        public Map<String, Set<Integer>> getDiffLines(String leftCommit, String rightCommit){
            Map<String,Set<Integer>> dstLines = new HashMap<>();
            if(leftCommit==null){
                System.out.println("ERROR: the left commit is null");
                return null;
            }
            List<DiffEntry> diffEntries = getDiffEntries(parseCommit(leftCommit),parseCommit(rightCommit));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter df = new DiffFormatter(out);
            df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            df.setRepository(repo);

            for (DiffEntry diffEntry : diffEntries) {
                if(!isJavaClass(diffEntry.getNewPath())){
                    continue;
                }
                if(diffEntry.getNewPath().contains("src/test/")){
                    continue;
                }
                DiffEntry.ChangeType changeType  = diffEntry.getChangeType();
                if(changeType==DiffEntry.ChangeType.ADD|| changeType== DiffEntry.ChangeType.MODIFY){
                    try {
                        Set<Integer> locs = new HashSet<>();
                        for (Edit edit : df.toFileHeader(diffEntry).toEditList()) {
                            for(int i=edit.getBeginB();i<edit.getEndB();i++){
                                locs.add(i+1);
                            }
                        }
                        if(locs.size()>0){
                            dstLines.put(getFullyQualifiedName(diffEntry.getNewPath(),parseCommit(rightCommit)),locs);
                        }
                    }catch (Exception e){

                    }
                }

            }
            return dstLines;
        }

        public List<DiffEntry> getDiffEntries(RevCommit leftCommit, RevCommit rightCommit) {
            AbstractTreeIterator oldTree = prepareTreeParser(leftCommit);
            AbstractTreeIterator newTree = prepareTreeParser(rightCommit);

            List<DiffEntry> diff = null;
            try {
                //diff = git.diff().setOldTree(oldTree).setNewTree(newTree).setShowNameAndStatusOnly(true).call();
                diff = git.diff().setOldTree(oldTree).setNewTree(newTree).call();
            } catch (GitAPIException e) {
                e.printStackTrace();
            }
            return diff;
        }

        private boolean isJavaClass(String filePath){
//        if(filePath.endsWith(".java") && filePath.contains(this.srcPath)){
            if(filePath.endsWith(".java")){
                if(!filePath.endsWith("package-info.java")){
                    return true;
                }
            }
            return false;
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

        private String getFullyQualifiedName(String filePath, RevCommit commit) {
            String content = gitCatFile(commit,filePath);
            String packageName = "";
            for (String line : content.split(System.lineSeparator())) {
                line = line.trim();
                if (line.startsWith("package")) {
                    packageName = line.split(" ")[1];
                    packageName = packageName.substring(0, packageName.length() - 1);
                    break;
                }
            }
            String className = filePath.replace(".java","");
            className=className.substring(filePath.lastIndexOf("/")+1);

            if(packageName.equals("")){
                return className;
            } else{
                return packageName+"."+className;
            }
        }

        private String gitCatFile(RevCommit commit, String filePath){
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

        public void write2File(String fileName, Collection<String> lines, boolean append){
            File file = new File(fileName);
            FileWriter fr = null;
            BufferedWriter br = null;
            try{
                fr = new FileWriter(file,append);
                br = new BufferedWriter(fr);
                for(String line: lines){
                    br.write(line+System.lineSeparator());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally{
                try {
                    br.close();
                    fr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
