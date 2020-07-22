package org.evosuite.tom;

import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.testcase.fm.MethodDescriptor;
import org.evosuite.testcase.statements.FunctionalMockStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.utils.LoggingUtils;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RevealConflictsTests {
    private static RevealConflictsTests instance = new RevealConflictsTests();
    private List<TestChromosome> tests = new ArrayList<>();

    public static RevealConflictsTests getInstance(){
        return instance;
    }

    public void addTest(TestChromosome test){
        tests.add(test);
    }

    public void checkAgain(){
        List<TestChromosome> toRemoveTests = new ArrayList<>();
        for(TestChromosome test: tests){
            ExecutionResult result = TestCaseExecutor.getInstance().execute(test.getTestCase());
            if(result.getAllThrownExceptions().size()>0){
                toRemoveTests.add(test);
            }
        }
        for(TestChromosome testChromosome: toRemoveTests){
            tests.remove(testChromosome);
        }
    }

    public List<TestChromosome> getTests(){
        return tests;
    }
    public void showClassLoaders(TestCase t) {
        for (Statement s : t) {
            if (s instanceof FunctionalMockStatement) {
                Class targetClass = ((FunctionalMockStatement) s).getTargetClass();
                if (targetClass != null) {
                    if (targetClass.getClassLoader() != null)
                        LoggingUtils.getEvoLogger().info("target class:" + targetClass.getClassLoader().toString());
                }
                List<MethodDescriptor> mds = ((FunctionalMockStatement) s).getMockedMethods();
                for (MethodDescriptor md : mds) {
                    if (md != null && md.getReturnClass() != null && md.getReturnClass().getRawClass() != null && md.getReturnClass().getRawClass().getClassLoader() != null)
                        LoggingUtils.getEvoLogger().info("method: " + md.getReturnClass().getRawClass().getClassLoader().toString());
                }
            }
            if(s instanceof MethodStatement){
                Class clz = ((MethodStatement) s).getCallee().getType().getClass();
                if(clz!=null){
                    if(clz.getClassLoader()!=null){
                        LoggingUtils.getEvoLogger().info("the classloader of method:"+clz.getClassLoader().toString());
                    }
                }
            }
        }

    }
}
