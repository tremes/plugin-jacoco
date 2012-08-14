package org.jboss.forge.jacoco.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.jboss.forge.maven.MavenCoreFacet;
import org.jboss.forge.project.Project;
import org.jboss.forge.test.AbstractShellTest;
import org.junit.Test;

public class JaCoCoPluginTest extends AbstractShellTest{
		
	@Test
	public void testSetupCommand() throws Exception{
		Project project = initializeJavaProject();
		getShell().execute("jacoco setup");
		MavenCoreFacet facet = project.getFacet(MavenCoreFacet.class);
		Model pom = facet.getPOM();
		Profile jacocoProfile = pom.getProfiles().get(0);
		assertEquals("jacoco", jacocoProfile.getId());
		
		Plugin jacocoPlugin = jacocoProfile.getBuild().getPlugins().get(0);
		assertEquals("jacoco-maven-plugin", jacocoPlugin.getArtifactId());
		
		PluginExecution exec = jacocoPlugin.getExecutions().get(0);
		assertEquals("prepare-agent", exec.getGoals().get(0));
		
	}
	
	@Test
	public void testJacocoCommands() throws Exception {
	
		Project project = initializeJavaProject();
		Dependency junitDep = new Dependency();
		junitDep.setArtifactId("junit");
		junitDep.setGroupId("junit");
		junitDep.setVersion("4.10");
		junitDep.setScope("test");
		MavenCoreFacet facet = project.getFacet(MavenCoreFacet.class);
		Model pom = facet.getPOM();
		pom.addDependency(junitDep);
		facet.setPOM(pom);
	
        queueInputLines("");
        //create some simple classes
      	getShell().execute("java new-class --package com.test \"public class A {}\"");
		getShell().execute("java new-field \"private int numberA=1;\"");
		getShell().execute("java new-method  \"public int getNumberA(){return numberA;}\"");
		
		getShell().execute("java new-class --package com.test \"public class B {}\"");
		getShell().execute("java new-field \"private int numberB=2;\"");
		getShell().execute("java new-method  \"public int getNumberB(){return numberB;}\"");
		
		//create and copy test class
		getShell().execute("java new-class --package com.test \"package com.test; import org.junit.Test;import static org.junit.Assert.*; public class ABTest {}\"");
		getShell().execute("java new-method  \"@Test public void testClasses(){A a = new A();B b = new B();assertEquals(3,a.getNumberA()+b.getNumberB());}\"");
		getShell().execute("cd "+project.getProjectRoot().getFullyQualifiedName());
		getShell().execute("cp src/main/java/com/test/ABTest.java src/test/java/");
		getShell().execute("rm -rf src/main/java/com/test/ABTest.java");
		
		//run jacoco plugin commands
		getShell().execute("jacoco setup");
		getShell().execute("jacoco run-tests --package com.test");
		queueInputLines("N");
		getShell().execute("jacoco create-report");
		
		assertTrue("Missing jacoco.exec file!", project.getProjectRoot().getChildDirectory("target").getChild("jacoco.exec").exists());
		assertTrue("Missing index.html report file!", project.getProjectRoot().getChildDirectory("target").getChildDirectory("site").getChildDirectory("jacoco").getChild("index.html").exists());
		
	}

}
