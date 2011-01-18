/*
 * Copyright (C) 2010 Saarland University
 * 
 * This file is part of EvoSuite.
 * 
 * EvoSuite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * EvoSuite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with EvoSuite.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.unisb.cs.st.evosuite.javaagent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import de.unisb.cs.st.evosuite.Properties;
import de.unisb.cs.st.evosuite.cfg.CFGClassAdapter;
import de.unisb.cs.st.evosuite.primitives.PrimitiveClassAdapter;
import de.unisb.cs.st.javalanche.coverage.distance.Hierarchy;
import de.unisb.cs.st.javalanche.mutation.javaagent.classFileTransfomer.mutationDecision.Excludes;


/**
 * The bytecode transformer - transforms bytecode depending on package
 * and whether it is the class under test
 * 
 * @author Gordon Fraser
 *
 */
public class BytecodeInstrumentation implements ClassFileTransformer {

	private static Hierarchy hierarchy;
	
	static {
		if(Properties.INSTRUMENT_PARENT) 
			hierarchy = Hierarchy.readFromDefaultLocation();
	}

	protected static Logger logger = Logger
	.getLogger(BytecodeInstrumentation.class);

	//private static RemoveSystemExitTransformer systemExitTransformer = new RemoveSystemExitTransformer();

	private static String target_class = Properties.TARGET_CLASS;
	
	protected boolean static_hack = Properties.getPropertyOrDefault("static_hack", false);
	
	private boolean makeAllAccessible = Properties.getPropertyOrDefault("make_accessible", false);
	
	private boolean isTargetClass(String className) {
		if(className.equals(target_class) || className.startsWith(target_class+"$")) {
			return true;
		}

		if(Properties.INSTRUMENT_PARENT) {
			return hierarchy.getAllSupers(target_class).contains(className);
		}
		
		
		return false;
	}
	
	static {
		logger.info("Loading bytecode transformer for "+Properties.PROJECT_PREFIX);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.instrument.ClassFileTransformer#transform(java.lang.ClassLoader, java.lang.String, java.lang.Class, java.security.ProtectionDomain, byte[])
	 */
	public byte[] transform(ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {
		
		if (className != null) {
			try {
				String classNameWithDots = className.replace('/', '.');
				
				// Some packages we shouldn't touch - hard-coded
				if (!classNameWithDots.startsWith(Properties.PROJECT_PREFIX) && (classNameWithDots.startsWith("java")
						|| classNameWithDots.startsWith("sun")
						|| classNameWithDots.startsWith("org.aspectj.org.eclipse")
						|| classNameWithDots.startsWith("org.mozilla.javascript.gen.c"))) {
					return classfileBuffer;
				}
				if (Excludes.getInstance().shouldExclude(classNameWithDots)) {
					return classfileBuffer;
				}
				if (classNameWithDots.startsWith(Properties.PROJECT_PREFIX)) {

					//logger.debug("Removing calls to System.exit() from class: "
					//			+ classNameWithDots);
					//classfileBuffer = systemExitTransformer
					//.transformBytecode(classfileBuffer);

					ClassReader reader = new ClassReader(classfileBuffer);
					ClassWriter writer = new ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);

					ClassVisitor cv = writer;
					
					// Print out bytecode if debug is enabled
					//if(logger.isDebugEnabled())
						//cv = new TraceClassVisitor(cv, new PrintWriter(System.out));

					// Apply transformations to class under test and its owned classes
					if(isTargetClass(classNameWithDots)) {
						cv = new AccessibleClassAdapter(cv, className);
						cv = new ExecutionPathClassAdapter(cv, className);
						cv = new CFGClassAdapter(cv, className);

					} else if(makeAllAccessible) {
						// Convert protected/default access to public access
						cv = new AccessibleClassAdapter(cv, className);
					}
					
					// Collect constant values for the value pool
					if(!Properties.MUTATION) {
						cv = new PrimitiveClassAdapter(cv, className);
					}
					
					// If we need to reset static constructors, make them explicit methods
					if(static_hack)
						cv = new StaticInitializationClassAdapter(cv, className);
					
					// Print out bytecode if debug is enabled
					//if(logger.isDebugEnabled())
						//cv = new TraceClassVisitor(cv, new PrintWriter(System.out));
					
					reader.accept(cv, ClassReader.SKIP_FRAMES);
					classfileBuffer = writer.toByteArray();
					return classfileBuffer;

				}

			} catch (Throwable t) {
				logger.fatal(
						"Transformation of class " + className + " failed", t);
				StringWriter writer = new StringWriter();
				t.printStackTrace(new PrintWriter(writer));
				logger.fatal(writer.getBuffer().toString());
				t.printStackTrace();
				System.exit(0);
				// throw new RuntimeException(e.getMessage());
			}
		}
		return classfileBuffer;
		
	}

}
