package org.evosuite.tom;

import org.evosuite.Properties;
import org.evosuite.assertion.*;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.stoppingconditions.StoppingConditionImpl;
import org.evosuite.testcase.DefaultTestCase;
import org.evosuite.testcase.fm.MethodDescriptor;
import org.evosuite.testcase.statements.FunctionalMockStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.utils.LoggingUtils;

import java.util.*;

public class MergeConflictStoppingCondition extends StoppingConditionImpl {

    private ArrayList<TestChromosome> checkedTestChromosomes = new ArrayList<>();
    private List<VersionsClassLoader> variantsClassLoaders = new ArrayList<>();

    /**
     * Maximum number of seconds
     */
    protected long maxSeconds = Properties.GLOBAL_TIMEOUT;

    protected long startTime;

    /**
     * {@inheritDoc}
     */
    @Override
    public void searchStarted(GeneticAlgorithm<?> algorithm) {
        startTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     * <p>
     * We are finished when the time is up
     */
    @Override
    public boolean isFinished() {
        long currentTime = System.currentTimeMillis();
        if ((currentTime - startTime) / 1000 > maxSeconds) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reset
     */
    @Override
    public void reset() {
        startTime = System.currentTimeMillis();
    }

    /* (non-Javadoc)
     * @see org.evosuite.ga.StoppingCondition#setLimit(int)
     */

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLimit(long limit) {
        maxSeconds = limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLimit() {
        return maxSeconds;
    }

    /* (non-Javadoc)
     * @see org.evosuite.ga.StoppingCondition#getCurrentValue()
     */

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCurrentValue() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - startTime) / 1000;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forceCurrentValue(long value) {
        startTime = value;
    }

    private void initClassLoaders() {
        String jarsPath = Properties.jars_path;
        for(String mutant: Properties.mutant_commits.split(":")){
            variantsClassLoaders.add(new VersionsClassLoader(jarsPath+mutant));
        }
        variantsClassLoaders.add(new VersionsClassLoader(jarsPath+Properties.target_commit));

//        if (Properties.target_version.startsWith("m")) {
//            int numParents = Integer.valueOf(Properties.target_version.substring(1));
//            for (int i = 1; i <= numParents; i++) {
//                variantsClassLoaders.add(new VersionsClassLoader(jarsPath + "p" + i));
//            }
//            variantsClassLoaders.add(new VersionsClassLoader(jarsPath + "merge"));
//        } else {
//            variantsClassLoaders.add(new VersionsClassLoader(jarsPath + "base"));
//            variantsClassLoaders.add(new VersionsClassLoader(jarsPath + "merge"));
//            variantsClassLoaders.add(new VersionsClassLoader(jarsPath + Properties.target_version));
//        }
    }

    private List<DefaultTestCase> cloneTest(DefaultTestCase testCase) {
        List<DefaultTestCase> clonedTests = new ArrayList<>();
        for (int i = 0; i < variantsClassLoaders.size(); i++) {
            DefaultTestCase clone = testCase.clone();
            ClassLoaderChangeListener.success = true;
            clone.changeClassLoader(variantsClassLoaders.get(i));
            if(!ClassLoaderChangeListener.success){
                return null;
            }
            clonedTests.add(clone);
        }
        return clonedTests;
    }

    @Override
    public void iteration(GeneticAlgorithm<?> algorithm) {

    }

    @Override
    public void searchFinished(GeneticAlgorithm<?> algorithm) {

    }

    @Override
    public void modification(Chromosome individual) {

    }

    @Override
    public void fitnessEvaluation(Chromosome individual) {

        if (!(individual instanceof TestSuiteChromosome)) {
            return;
        }

        for (TestChromosome testChromosome : ((TestSuiteChromosome) individual).getTestChromosomes()) {
            if(isFinished()){
                return;
            }
            boolean checked = false;
            for (TestChromosome checkedTestChromosome : checkedTestChromosomes) {
                if (checkedTestChromosome.equals(testChromosome)) {
                    checked = true;
                    break;
                }
            }
            if (!checked) {
                checkedTestChromosomes.add(testChromosome);

//                LoggingUtils.getEvoLogger().info("* coverage info:");
//                Map<String, Map<String, Map<Integer, Integer>>> coverageData = testChromosome.getLastExecutionResult().getTrace().getCoverageData();
//                for(String className: coverageData.keySet()){
//                    Map<String,Map<Integer,Integer>> coverageDataClass = coverageData.get(className);
//                    for(String method: coverageDataClass.keySet()){
//                        Map<Integer,Integer> coverage = coverageDataClass.get(method);
//                        LoggingUtils.getEvoLogger().info(className+"::"+method+"->"+coverage.toString());
//                    }
//                }

                boolean all_diff_flag = true;
                for (int i = 0; i < variantsClassLoaders.size() - 1; i++) {
                    boolean diff_flag = false;
                    for (String depend_class : DiffLinesExtractor.getInstance().diff().get(i).keySet()) {
                        Set<Integer> diffLines = DiffLinesExtractor.getInstance().diff().get(i).get(depend_class);
                        if(diffLines.size()==0){
                            continue;
                        }
                        //if different class is covered, we would consider it as one candidate
                        Set<Integer> coveredLines = testChromosome.getLastExecutionResult().getTrace().getCoveredLines(depend_class);
                        if (coveredLines.size() != 0) {
                            diff_flag=true;
                            break;
                        }
                    }
                    if (!diff_flag) {
                        all_diff_flag = false;
                        break;
                    }
                }
                if (!all_diff_flag) {
                    continue;
                }

                ExecutionResult executionResult = testChromosome.getLastExecutionResult();
                if (executionResult.getAllThrownExceptions().size() > 0 ) {
                    continue;
                }

                if (variantsClassLoaders.size() == 0) {
                    initClassLoaders();
                }

                List<DefaultTestCase> variantTests = cloneTest((DefaultTestCase) testChromosome.getTestCase());
                if (variantTests==null){
                    continue;
                }

                DefaultTestCase test = ((DefaultTestCase) testChromosome.getTestCase()).clone();

                List<List<Assertion>> allAssertions = new ArrayList<>();

                TomAssertionGenerator.getInstance().reset();
                for (int i = 0; i < variantTests.size() - 1; i++) {

                    TomAssertionGenerator.getInstance().addAssertions(test, variantTests.get(i));

                    List<Assertion> assertions = new ArrayList<>();
                    for(Statement s: test){
                        assertions.addAll(s.getAssertions());
                        s.setAssertions(new LinkedHashSet<>());
                    }
                    allAssertions.add(assertions);
                }

                if (unexpectedBehavior(allAssertions, (DefaultTestCase)testChromosome.getTestCase())) {
                    DefaultTestCase testNoInstrument = variantTests.get(variantTests.size()-1);
                    if (isStable(test,testNoInstrument)) {
//                        LoggingUtils.getEvoLogger().info("************found unexpected behavior***********");
//                        LoggingUtils.getEvoLogger().info(testChromosome.getTestCase().toCode());
//                        for(List<Assertion> assertions: allAssertions){
//                            for(Assertion assertion: assertions){
//                                LoggingUtils.getEvoLogger().info(assertion.getCode());
//                            }
//                            LoggingUtils.getEvoLogger().info("-----------------");
//                        }
//
//                        LoggingUtils.getEvoLogger().info("*********************************************************");
                        testChromosome.setConflict();
                        RevealConflictsTests.getInstance().addTest((TestChromosome)testChromosome.clone());
                    }
                }
            }
        }
    }

    public boolean unexpectedBehavior(List<List<Assertion>> allAssertions, DefaultTestCase test) {
        List<Assertion> commonAssertions = new ArrayList<>();
        for(Assertion assertion: allAssertions.get(0)){
            boolean flag_in_all=true;
            for(int i=1;i<allAssertions.size();i++){
                boolean flag=false;
                List<Assertion> assertions_i = allAssertions.get(i);
                for(Assertion assertion_tmp: assertions_i){
                    if(assertion.getCode().equals(assertion_tmp.getCode())){
                        flag=true;
                        break;
                    }
                }
                if(!flag){
                    flag_in_all=false;
                    break;
                }
            }
            if(flag_in_all){
                commonAssertions.add(assertion);
            }
        }
        if(commonAssertions.size()>0){
            for(Assertion assertion: commonAssertions){
                test.getStatement(assertion.getStatement().getPosition()).addAssertion(assertion);
            }
            return true;
        }
        return false;
    }

    public boolean isStable(DefaultTestCase test, DefaultTestCase variantTest){
        for(int i=0;i<5;i++){
            TomAssertionGenerator.getInstance().addAssertions(test,variantTest);
            if(test.getAssertions().size()>0 || TomAssertionGenerator.getInstance().getVariantResult().getAllThrownExceptions().size()>0){
                return false;
            }
        }
        return true;
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
        }

    }

}
