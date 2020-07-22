package org.evosuite.tom;

import org.evosuite.Properties;
import org.evosuite.assertion.Assertion;
import org.evosuite.assertion.AssertionGenerator;
import org.evosuite.assertion.NullAssertion;
import org.evosuite.assertion.OutputTrace;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.utils.LoggingUtils;
import sun.rmi.runtime.Log;

import java.util.*;

public class TomAssertionGenerator extends AssertionGenerator {

    private ExecutionResult result = null;
    private ExecutionResult variantResult =null;

    public void addAssertions(TestCase test, TestCase variantTest){
        if(result==null)
            result = runTest(test);

        Properties.BREAK_ON_EXCEPTION=false;
        variantResult = runTest(variantTest);
        Properties.BREAK_ON_EXCEPTION=true;

        //if the trace of result.getTraces does not match with the traceEntry of variantResult
        //the corresponding assertions would not be generated.
        for(OutputTrace<?> trace: result.getTraces()){
            for(OutputTrace<?> otherTrace: variantResult.getTraces()){
                if(trace.getClass().equals(otherTrace.getClass())||trace.getClass().getName().equals(otherTrace.getClass().getName())){
                    trace.getAssertions(test,otherTrace);
                }
            }
        }
        for(Integer exception: variantResult.getPositionsWhereExceptionsWereThrown()){
            Set<Assertion> assertions = new HashSet<>();
            Set<Assertion> assertions_variant = new HashSet<>();

            for (OutputTrace<?> trace : result.getTraces()) {
                trace.getAllAssertions(test, exception);
                assertions.addAll(test.getStatement(exception).getAssertions());
            }
            test.getStatement(exception).setAssertions(new HashSet<>());

            for (OutputTrace<?> trace : variantResult.getTraces()) {
                trace.getAllAssertions(test, exception);
                assertions_variant.addAll(test.getStatement(exception).getAssertions());
            }
            test.getStatement(exception).setAssertions(new HashSet<>());

            List<Assertion> sameAssertions = new ArrayList<>();
            for(Assertion assertion: assertions){
                for(Assertion assertion_variant: assertions_variant){
                    if(assertion.getCode()==assertion_variant.getCode()){
                        sameAssertions.add(assertion);
                        break;
                    }
                }
            }
            for(Assertion sameAssertion: sameAssertions){
                assertions.remove(sameAssertion);
            }
            test.getStatement(exception).setAssertions(assertions);
        }
    }

    public ExecutionResult getVariantResult(){
        return variantResult;
    }

    public void reset(){
        result=null;
        variantResult=null;
    }

    @Override
    public void addAssertions(TestCase test) {

    }

    private static TomAssertionGenerator singleton = new TomAssertionGenerator();

    public static TomAssertionGenerator getInstance(){
        return singleton;
    }

}
