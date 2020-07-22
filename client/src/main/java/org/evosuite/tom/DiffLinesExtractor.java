package org.evosuite.tom;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.evosuite.Properties;

public class DiffLinesExtractor {


    private List<Map<String,Set<Integer>>> allDiffLines = new ArrayList<>();

    private static DiffLinesExtractor diffLinesExtractor = new DiffLinesExtractor();

    public static DiffLinesExtractor getInstance(){
        return diffLinesExtractor;
    }

    public List<Map<String,Set<Integer>>> diff(){
        if(allDiffLines.size()==0){
            List<String> allLines = readFileByLines("/tmp/difflines-"+Properties.target_commit+":"+Properties.mutant_commits);
            Map<String,Set<Integer>> diffLines = new HashMap<>();
            for(String line:allLines){
                if(line.equals("---")){
                    allDiffLines.add(diffLines);
                }else{
                    String diffClz = line.split(":")[0];
                    String nums = line.split(":")[1];
                    diffLines.put(diffClz,new HashSet<>());
                    for(String num: nums.split(",")){
                        diffLines.get(diffClz).add(Integer.valueOf(num));
                    }
                }
            }
        }
        return allDiffLines;
    }

    public List<String> readFileByLines(String fileName) {
        File file = new File(fileName);
        List<String> allLines = new ArrayList<>();
        if (!file.exists()) {
            return allLines;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            int line = 1;
            while ((tempString = reader.readLine()) != null) {
                allLines.add(tempString);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return allLines;
    }
}
