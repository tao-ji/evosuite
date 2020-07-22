package org.evosuite.tom;

import org.evosuite.Properties;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class FileUtils {

    private Path project;
    private List<String> postfixes = new ArrayList<>();

    public FileUtils(String gitFilePath, int numVersions) {
        this.project = Paths.get(gitFilePath);
        if (numVersions == 3) {
            postfixes.addAll(Arrays.asList(new String[]{"p1", "p2", "merge"}));
        } else if (numVersions > 3) {
            postfixes.add("base");
            for (int i = 1; i <= numVersions - 2; i++)
                postfixes.add("p" + i);
            postfixes.add("merge");
        }
    }

    public void initDir(String subDir) {

        String tmpPath = Properties.working_dir+File.separator+subDir;
        File tmp = new File(tmpPath);
        if (tmp.exists()) {
            try {
                Files.walk(tmp.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String dstDirPath = Properties.working_dir+File.separator+subDir;
        for (String postfix : postfixes) {
            File dstSrcSubDir = new File(dstDirPath, postfix);
            dstSrcSubDir.mkdirs();
        }
    }

    public void copy(int index, Set<String> fullyQualifiedNames) {

        for(String fullyQualifiedName: fullyQualifiedNames){
            if(fullyQualifiedName.contains("(")){
                fullyQualifiedName = fullyQualifiedName.substring(0,fullyQualifiedName.indexOf("("));
            }
            String className = fullyQualifiedName.substring(0,fullyQualifiedName.lastIndexOf("."));
            String classPath = className.replaceAll("\\.",File.separator);
            List<Path> foundFiles = findFile(project,classPath+".java",".java");
            if(foundFiles.size()==1){
                try {
                    Files.copy(foundFiles.get(0), Paths.get(Properties.working_dir+"/src/" + postfixes.get(index), className), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else if(foundFiles.size()>1){
                System.out.println("Something may be wrong in FileUtils");
                System.out.println(classPath);
                System.out.println(foundFiles.toString());
            }
        }

        List<Path> jars = findFile(project, "",".jar");
        for (Path jar : jars) {
            String jarName = jar.getFileName().toString();
            try {
                Files.copy(jar, Paths.get(Properties.working_dir+"/jars/",postfixes.get(index), jarName), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public List<Path> findFile(Path directory, String keyword, String fileType) {
        List<Path> paths = new ArrayList<>();
        try {
            Files.walk(directory).filter(file ->
                    file.toString().contains(keyword) && file.toString().endsWith(fileType)).forEach(paths::add);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return paths;
    }

    private static String getFullyQualifiedName(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String packageName = "";
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("package")) {
                    packageName = line.split(" ")[1];
                    packageName = packageName.substring(0, packageName.length() - 1);
                    break;
                }
            }
            String className = path.toFile().getName().replace(".java","");
            if(packageName.equals("")){
                return className;
            } else{
                return packageName+"."+className;
            }
        } catch (IOException e) {
            System.out.println("Error: failed to read file");
        }
        return null;

    }
}
