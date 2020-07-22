package org.evosuite.tom;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiffLinesExtractor {


    private static DiffLinesExtractor diffLinesExtractor = new DiffLinesExtractor();

    public static DiffLinesExtractor getInstance(){
        return diffLinesExtractor;
    }

    private List<String> filePaths;
    public void setFilePaths(List<String> filePaths){
        this.filePaths = filePaths;
    }

    private List<Map<String,List<Integer>>> diffLinesList = new ArrayList<>();

    public List<Map<String,List<Integer>>> getDiffLinesList(){
        return diffLinesList;
    }

    public void diff(){
        String dstPath = filePaths.get(filePaths.size()-1);
        for(int i=0;i<filePaths.size()-1;i++){
            String srcPath = filePaths.get(i);
            diffLinesList.add(diff(srcPath, dstPath));
        }
    }

    public Map<String, List<Integer>> diff(String srcPath, String dstPath){
        File src = new File(srcPath);
        File dst = new File(dstPath);

        Map<String,List<Integer>> diffLines = new HashMap<>();
        for(File dstFile: dst.listFiles()){
            boolean flag = false;
            for(File srcFile: src.listFiles()){
                if(dstFile.getName().equals(srcFile.getName())){
                    ArrayList<String> patch = diffCommand(srcFile.getAbsolutePath(),dstFile.getAbsolutePath());
                    List<Integer> lineNums = getDiffLines(patch);
                    diffLines.put(dstFile.getName(),lineNums);
                    flag=true;
                    break;
                }
            }
            if(!flag){
                ArrayList<String> patch = diffCommand("/dev/null",dstFile.getAbsolutePath());
                List<Integer> lineNums = getDiffLines(patch);
                diffLines.put(dstFile.getName(),lineNums);
            }
        }
        return diffLines;
    }

    private List<Integer> getDiffLines(ArrayList<String> patch){
        List<Integer> lineNums = new ArrayList<>();
        for(String line: patch){
            if(line.startsWith("@@")){
                String lineInfo = line.split("\\@\\@")[1];
                String targetInfo = lineInfo.split("\\+")[1];
                int beginNum, total;
                if(targetInfo.contains(",")){
                    beginNum = Integer.valueOf(targetInfo.split("\\,")[0].trim());
                    total = Integer.valueOf(targetInfo.split("\\,")[1].trim());
                    if(total==0) total=1;
                }else{
                    beginNum = Integer.valueOf(targetInfo.trim());
                    total = 1;
                }
                for(int i =beginNum;i<beginNum+total;i++){
                    lineNums.add(i);
                }
            }
        }
        return lineNums;
    }

    private ArrayList<String> diffCommand(String srcPath, String dstPath){
        try {
            Process process = new ProcessBuilder(new String[] {"bash", "-c", "diff -u0 "+srcPath+" "+dstPath+"|cat"})
                            .redirectErrorStream(true)
                            .directory(new File("."))
                            .start();

            ArrayList<String> output = new ArrayList<String>();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line = null;
            while ( (line = br.readLine()) != null )
                output.add(line);

            //There should really be a timeout here.
            if (0 != process.waitFor())
                return null;

            return output;

        } catch (Exception e) {
            //Warning: doing this is no good in high quality applications.
            //Instead, present appropriate error messages to the user.
            //But it's perfectly fine for prototyping.
            e.printStackTrace();
            return null;
        }
    }
}
