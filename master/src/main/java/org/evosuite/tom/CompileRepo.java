package org.evosuite.tom;

import org.apache.maven.shared.invoker.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;

public class CompileRepo {
    public boolean compile(String repoPath, String goals) {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(repoPath+File.separator+"pom.xml"));
        request.setGoals(Collections.singletonList(goals));

        Invoker invoker = new DefaultInvoker();
        invoker.setMavenHome(new File("/opt/maven-3.6.1"));

        invoker.setLogger(new PrintStreamLogger(System.err, InvokerLogger.ERROR) {
        });
        invoker.setOutputHandler(new InvocationOutputHandler() {
            @Override
            public void consumeLine(String s) throws IOException {
            }
        });

        try {
            invoker.execute(request);
            if (invoker.execute(request).getExitCode() == 0) {
                System.out.println("build: success");
                return true;
            } else {
                System.err.println("build: error");
                return false;
            }
        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        return false;
    }
}
