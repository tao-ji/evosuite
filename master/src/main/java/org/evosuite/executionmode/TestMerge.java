package org.evosuite.executionmode;

import org.apache.commons.cli.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.evosuite.EvoSuite;
import org.evosuite.Properties;
import org.evosuite.tom.AnalyzeMerge;
import org.evosuite.tom.UUT;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class TestMerge {
    public static final String NAME = "tom";

    public static Option[] getOptions() {
        return new Option[]{
                new Option(NAME, "TOM: generate tests for merges"),
                new Option("repoPath", true, "the repo's path"),
                new Option("srcPath",true,"the path of source files"),
                new Option("mergeSHA", true, "the sha of one merge"),
                new Option("depth", true, "the depth of dependency"),
                new Option("build",  "whether build different versions"),
                new Option("generate","whether generate tests"),
                new Option("uutLimit",true,"the number limit of uuts"),
                new Option("timeBudget",true,"time budget"),
                new Option("workingDir",true,"the working dirs of jars and source files"),
        };
    }

    public static Object execute(Options options, List<String> javaOpts, CommandLine line) {
        String repoPath = line.getOptionValue("repoPath");

        String projectName = repoPath.substring(repoPath.lastIndexOf("/"));
        Properties.working_dir = line.getOptionValue("workingDir")+File.separator+projectName;
        System.out.println("working_dir:"+Properties.working_dir);

        int uutLimit = 3;
        if(line.getOptionValue("uutLimit")!=null)
            uutLimit=Integer.valueOf(line.getOptionValue("uutLimit"));
        // check the repo path
        if (!new File(repoPath + File.separator + ".git").exists()) {
            System.out.println("ERROR: cannot find the .git in this directory");
            return null;
        }

        try {
            Git git = Git.open(new File(repoPath));
            String depthStr = line.getOptionValue("depth");
            int depth = 0;
            if (depthStr != null) {
                depth = Integer.valueOf(line.getOptionValue("depth"));
            }
            String srcPath = line.getOptionValue("srcPath");
            AnalyzeMerge analyzeMerge = new AnalyzeMerge(repoPath, srcPath, git, depth, uutLimit, line.hasOption("build"));


            String mergeSHA = line.getOptionValue("mergeSHA");
            if (analyzeMerge.prepare(mergeSHA)) {
                Map<String, Set<String>> allUUTs = analyzeMerge.getUUTs();
                List<String> sortedKeys = new ArrayList<>();
                for(String type: allUUTs.keySet()) {
                    if (type.startsWith("m")) {
                        sortedKeys.add(type);
                        break;
                    }
                }
                for(String type: allUUTs.keySet()) {
                    if (type.startsWith("p")) {
                        sortedKeys.add(type);
                    }
                }
                for(String type: sortedKeys){
                    Set<String> uuts = allUUTs.get(type);
                    System.out.println(type);
                    for(String uut: uuts){
                        invokeTestGeneration(repoPath, mergeSHA, uut,type,line.getOptionValue("timeBudget"),line.hasOption("generate"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static void invokeTestGeneration(String target_project, String merge_sha, String uut, String targetVersion, String timeout, boolean generate) {
        if(!uut.contains("(")){
            return;
        }
        List<String> argsList = new ArrayList<>();
        argsList.add("-projectCP");

        File jarsDir = null;
        if(targetVersion.startsWith("m")){
            jarsDir = new File(Properties.working_dir+"/jars/merge");
        }else{
            jarsDir = new File(Properties.working_dir+"/jars/"+targetVersion);
        }
        List<String> jarsList = new ArrayList<>();
        for (File f : jarsDir.listFiles()) {
            jarsList.add(f.getAbsolutePath());
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

//        argsList.add("-Dtarget_project");
//        argsList.add(target_project);

        argsList.add("-Dworking_dir");
        argsList.add(Properties.working_dir);

//        argsList.add("-Dmerge_sha");
//        argsList.add(merge_sha);

        argsList.add("-class");
        argsList.add(fileName);

        argsList.add("-Dtarget_method_prefix");
        if(!classToTest.endsWith(methodToTest)){
            argsList.add(methodToTest);
        }else{
			argsList.add("<init>");
		}

        argsList.add("-Dtarget_version");
        argsList.add(targetVersion);

        argsList.addAll(Arrays.asList("-criterion diffline:branch -Dstopping_condition mergeconflict".split(" ")));
        argsList.add("-Dglobal_timeout");

        if(timeout!=null){
            argsList.add(timeout);
        } else {
            argsList.add("3000");
        }

        if(generate) {
            EvoSuite evosuite = new EvoSuite();
            System.out.println("command line");
            System.out.println(String.join(" ", argsList));
            evosuite.parseCommandLine(argsList.toArray(new String[argsList.size()]));
        }else{
            writeFile(target_project, String.join(" ", argsList));
        }
    }

//    private static List<String> getCheckedOutClasses() {
//        List<String> checkedOutClasses = new ArrayList<>();
//        File mergeSrcDir = new File("/home/jitao/.cache/evosuite/src/merge/");
//        for (File f : mergeSrcDir.listFiles()) {
//            checkedOutClasses.add(f.getName().replace(".java", ""));
//        }
//        return checkedOutClasses;
//    }

    private static void writeFile(String target_project, String line){
        target_project = target_project.substring(target_project.lastIndexOf("/")+1);
        Path file = Paths.get("commands_"+target_project);
        List<String> lines = new ArrayList<>();
        lines.add(line);
        try {
            if(!file.toFile().exists()) {
                file.toFile().createNewFile();
            }
            Files.write(file, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
