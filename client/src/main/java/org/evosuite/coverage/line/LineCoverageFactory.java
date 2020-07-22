/**
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.coverage.line;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.*;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.coverage.MethodNameMatcher;
import org.evosuite.graphs.cfg.BytecodeInstruction;
import org.evosuite.instrumentation.LinePool;
import org.evosuite.tom.DiffLinesExtractor;
import org.evosuite.testsuite.AbstractFitnessFactory;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.rmi.runtime.Log;

/**
 * <p>
 * MethodCoverageFactory class.
 * </p>
 * 
 * @author Gordon Fraser, Andre Mis, Jose Miguel Rojas
 */
public class LineCoverageFactory extends
		AbstractFitnessFactory<LineCoverageTestFitness> {

	private static final Logger logger = LoggerFactory.getLogger(LineCoverageFactory.class);
	private final MethodNameMatcher matcher = new MethodNameMatcher();

	private boolean isEnumDefaultConstructor(String className, String methodName) {
		if(!methodName.equals("<init>(Ljava/lang/String;I)V")) {
			return false;
		}
		try {
			Class<?> targetClass = Class.forName(className, false, TestGenerationContext.getInstance().getClassLoaderForSUT());
			if (!targetClass.isEnum()) {
				logger.debug("Class is not enum");
				return false;
			}
			return Modifier.isPrivate(targetClass.getDeclaredConstructor(String.class, int.class).getModifiers());
		} catch(ClassNotFoundException | NoSuchMethodException e) {
			logger.debug("Exception "+e);
			return false;
		}
	}

	private boolean diff=false;

	public LineCoverageFactory(){
		this.diff=false;
	}

	public LineCoverageFactory(boolean diff){
		this.diff=diff;
		String versSrcPath = Properties.working_dir+"/src";
//		String targetClassName = Properties.getTargetClassAndDontInitialise().getName();

		List<String> filePaths = new ArrayList<>();

		if(Properties.target_version.startsWith("m")){
			int numVersions = Integer.valueOf(Properties.target_version.substring(1));
			for(int i=1;i<=numVersions;i++){
				filePaths.add(versSrcPath+File.separator+"p"+i);
				}
				filePaths.add(versSrcPath+File.separator+"merge");
		}else{
		    filePaths.add(versSrcPath+File.separator+"base");
			filePaths.add(versSrcPath+File.separator+"merge");
			filePaths.add(versSrcPath+File.separator+Properties.target_version);
		}

		DiffLinesExtractor.getInstance().setFilePaths(filePaths);
		DiffLinesExtractor.getInstance().diff();

	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.evosuite.coverage.TestCoverageFactory#getCoverageGoals()
	 */
	/** {@inheritDoc} */
	@Override
	public List<LineCoverageTestFitness> getCoverageGoals() {
		List<LineCoverageTestFitness> goals = new ArrayList<LineCoverageTestFitness>();

		long start = System.currentTimeMillis();

		if(diff){
			List<Map<String,List<Integer>>> diffLinesList = DiffLinesExtractor.getInstance().getDiffLinesList();
			LoggingUtils.getEvoLogger().info("extracted lines:"+diffLinesList.toString());
			for(String className : LinePool.getKnownClasses()) {
				LoggingUtils.getEvoLogger().info("known class:" + className);

				//className may be the name of the inner class
				Set<Integer> diffLines = new HashSet<>();
				for (Map<String, List<Integer>> delta : diffLinesList) {
					for (String key : delta.keySet()) {
						if (className.startsWith(key)) {
							diffLines.addAll(delta.get(key));
						}
					}
				}

				for (String methodName : LinePool.getKnownMethodsFor(className)) {
					if (isEnumDefaultConstructor(className, methodName)) {
						continue;
					}
					Set<Integer> lines = LinePool.getLines(className, methodName);
					int begin = Collections.min(lines);
					int end = Collections.max(lines);
//					if (diffLines.size() > 0) {
//						LoggingUtils.getEvoLogger().info(className + "." + methodName);
//						LoggingUtils.getEvoLogger().info(lines.toString());
//					}
					// the lines may skip some lines in the method body, we compare the line with the range
					Set<Integer> line_goals = new HashSet<>();
					for (Integer diffline : diffLines) {
						if (diffline.intValue() >= begin && diffline.intValue() <= end) {
						    if(!lines.contains(diffline)){
						    	int i=0;
						    	boolean flag=false;
						    	while(true){
						    		if(lines.contains(diffline.intValue()-i)){
						    			flag=true;
						    			line_goals.add(diffline.intValue()-i);
									}
									if(lines.contains(diffline.intValue()+i)){
										flag=true;
										line_goals.add(diffline.intValue()+i);
									}
									if(flag){
										break;
									}
									i++;
								}
							}else {
						    	line_goals.add(diffline);
							}
						}
					}
					for(Integer line_num: line_goals){
						goals.add(new LineCoverageTestFitness(className,methodName,line_num));
					}
				}
			}
		}
		else{
			for(String className : LinePool.getKnownClasses()) {
				// Only lines in CUT
				if(!isCUT(className))
					continue;

				for(String methodName : LinePool.getKnownMethodsFor(className)) {
					if(isEnumDefaultConstructor(className, methodName)) {
						continue;
					}
					Set<Integer> lines = LinePool.getLines(className, methodName);
					if (!matcher.methodMatches(methodName)) {
						logger.info("Method {} does not match criteria. ",methodName);
						continue;
					}
					for (Integer line : lines) {
						logger.info("Adding goal for method " + className + "." + methodName + ", Line " + line + ".");
						goals.add(new LineCoverageTestFitness(className, methodName, line));
					}
				}
			}
		}
		goalComputationTime = System.currentTimeMillis() - start;
		return goals;
	}



	/**
	 * Create a fitness function for branch coverage aimed at covering the root
	 * branch of the given method in the given class. Covering a root branch
	 * means entering the method.
	 * 
	 * @param className
	 *            a {@link java.lang.String} object.
	 * @param method
	 *            a {@link java.lang.String} object.
	 * @return a {@link org.evosuite.coverage.branch.BranchCoverageTestFitness}
	 *         object.
	 */
	public static LineCoverageTestFitness createLineTestFitness(
			String className, String method, Integer line) {

		return new LineCoverageTestFitness(className,
				method.substring(method.lastIndexOf(".") + 1), line);
	}

	/**
	 * Convenience method calling createMethodTestFitness(class,method) with
	 * the respective class and method of the given BytecodeInstruction.
	 * 
	 * @param instruction
	 *            a {@link org.evosuite.graphs.cfg.BytecodeInstruction} object.
	 * @return a {@link org.evosuite.coverage.branch.BranchCoverageTestFitness}
	 *         object.
	 */
	public static LineCoverageTestFitness createLineTestFitness(
			BytecodeInstruction instruction) {
		if (instruction == null)
			throw new IllegalArgumentException("null given");

		return createLineTestFitness(instruction.getClassName(),
				instruction.getMethodName(), instruction.getLineNumber());
	}
}
