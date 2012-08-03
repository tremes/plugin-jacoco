package org.jboss.forge.jacoco;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.jboss.forge.maven.MavenCoreFacet;
import org.jboss.forge.parser.xml.Node;
import org.jboss.forge.parser.xml.XMLParser;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.shell.PromptType;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.Help;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.forge.shell.plugins.SetupCommand;

/**
 * 
 * Plugin for installing, running and reporting code coverage with JaCoCo
 * 
 * @author tremes@redhat.com
 * 
 */
@Alias("jacoco")
@RequiresFacet(DependencyFacet.class)
@Help("A plugin that helps setting up Java code coverage")
public class JaCoCoPlugin implements org.jboss.forge.shell.plugins.Plugin {

	@Inject
	private Project project;

	@Inject
	private Shell shell;

	@SetupCommand(help = "Setup JaCoCo Maven plugin to the project.")
	public void installJaCocoProfile() {
		addJacocoProfile("jacoco");
	}

	private void addJacocoProfile(String profileName) {
		MavenCoreFacet facet = project.getFacet(MavenCoreFacet.class);
		Profile profile = new Profile();
		profile.setId(profileName);

		BuildBase build = new BuildBase();
		org.apache.maven.model.Plugin plugin = new org.apache.maven.model.Plugin();

		plugin.setGroupId("org.jacoco");
		plugin.setArtifactId("jacoco-maven-plugin");
		plugin.setVersion("0.5.8.201207111220");

		PluginExecution prepare = new PluginExecution();
		prepare.addGoal("prepare-agent");
		List<PluginExecution> executions = new ArrayList<PluginExecution>();
		executions.add(prepare);

		plugin.setExecutions(executions);

		build.addPlugin(plugin);
		profile.setBuild(build);

		Model pom = facet.getPOM();
		Profile existingProfile = findProfileById(profileName, pom);
		if (existingProfile != null) {
			pom.removeProfile(existingProfile);
		}
		pom.addProfile(profile);
		facet.setPOM(pom);
	}

	@Command(value = "run-tests", help = "Run tests and prepares property pointing to the JaCoCo runtime agent.")
	public void runTestsAndPrepareCodeCoverageAgent(
			@Option(name = "package", required = true, type = PromptType.JAVA_PACKAGE, description = "Package containing classes for code coverage instrumentation.") String packageName) {

		boolean arquillianTesting = shell.promptBoolean(
				"Do you want to run Arquillian tests?", false);

		if (arquillianTesting) {
			Profile profile = shell.promptChoiceTyped(
					"Select managed container profile:",
					project.getFacet(MavenCoreFacet.class).getPOM()
							.getProfiles());
			adjustArquillianConfig();
			setupAdditionalArquillianProfile(packageName);
			try {
				shell.execute("mvn clean test -Pjacoco-arq -P" + profile.getId());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			setupPackageToProfile(
					packageName,
					findProfileById("jacoco",
							project.getFacet(MavenCoreFacet.class).getPOM()));
			try {
				shell.execute("mvn clean test -Pjacoco");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Command(value = "create-report", help = "Creates code coverage report to ${project}/target/site/jacoco.")
	public void generateReport() {
		try {
			if (findProfileById("jacoco-arq",
					project.getFacet(MavenCoreFacet.class).getPOM()) != null) {
				shell.execute("mvn jacoco:report -Pjacoco -Pjacoco-arq");
			} else {
				shell.execute("mvn jacoco:report -Pjacoco");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setupPackageToProfile(String packageName, Profile profile) {
		Plugin plugin = findPluginByArtifactId("jacoco-maven-plugin",
				profile.getBuild());

		packageName = packageName.replaceAll("\\.", "/");
		packageName = packageName.concat("/**");
		String config = "<configuration><includes><include>" + packageName
				+ "</include></includes></configuration>";

		if (profile.getId().equals("jacoco-arq")) {
			config = "<configuration><includes><include>"
					+ packageName
					+ "</include></includes><propertyName>jacoco.agent</propertyName></configuration>";
			org.apache.maven.model.Plugin surefirePlugin = new org.apache.maven.model.Plugin();

			surefirePlugin.setArtifactId("maven-surefire-plugin");
			surefirePlugin.setVersion("2.12");

			String surefireConfiguration = "<configuration><systemProperties>"
					+ "<jacoco.agent>${jacoco.agent}</jacoco.agent>"
					+ "<arquillian.launch>jacoco</arquillian.launch>"
					+ "</systemProperties></configuration>";

			Xpp3Dom dom = null;
			try {
				dom = Xpp3DomBuilder.build(new ByteArrayInputStream(
						surefireConfiguration.getBytes()), "UTF-8");
			} catch (Exception e) {
				e.printStackTrace();
			}

			surefirePlugin.setConfiguration(dom);
			profile.getBuild().addPlugin(surefirePlugin);
		}

		Xpp3Dom dom = null;
		try {
			dom = Xpp3DomBuilder.build(
					new ByteArrayInputStream(config.getBytes()), "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}

		plugin.setConfiguration(dom);
		profile.setBuild(profile.getBuild());
		MavenCoreFacet facet = project.getFacet(MavenCoreFacet.class);
		Model pom = facet.getPOM();

		Profile existingProfile = findProfileById(profile.getId(), pom);
		if (existingProfile != null) {
			pom.removeProfile(existingProfile);
		}

		pom.addProfile(profile);
		facet.setPOM(pom);
	}
	
	private void setupAdditionalArquillianProfile(String packageName) {
		addJacocoProfile("jacoco-arq");
		MavenCoreFacet facet = project.getFacet(MavenCoreFacet.class);
		Profile arquillianProfile = findProfileById("jacoco-arq",
				facet.getPOM());
		setupPackageToProfile(packageName, arquillianProfile);
	}

	private void adjustArquillianConfig() {
		ResourceFacet resources = project.getFacet(ResourceFacet.class);
		FileResource<?> config = (FileResource<?>) resources
				.getTestResourceFolder().getChild("arquillian.xml");

		if (!config.exists() || config == null) {

			throw new RuntimeException(
					"Cannot read arquillian.xml! Make sure Arquillian plugin is installed.");

		} else {
			Node arquillianRootNode = XMLParser
					.parse(config.getResourceInputStream()).get("arquillian")
					.get(0);

			List<Node> nodes = arquillianRootNode.getChildren();
			// there is no special content in arquillian.xml
			if (nodes.size() == 0) {
				arquillianRootNode = XMLParser
						.parse("<arquillian xmlns=\"http://jboss.org/schema/arquillian\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
								+ "            xsi:schemaLocation=\"http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd\"> "
								+ "<container qualifier=\"jacoco\" default=\"false\"><configuration><property name=\"javaVmArguments\">${jacoco.agent}</property></configuration></container>"
								+ "</arquillian>");
				config.setContents(XMLParser.toXMLString(arquillianRootNode));
			} else {
				List<Node> containers = arquillianRootNode.get("container");
				Node jacocoContainer = null;
				for (Node container : containers) {
					if (container.getAttribute("qualifier").equals("jacoco")) {
						jacocoContainer = container;
					}
				}
				if (jacocoContainer != null) {
					arquillianRootNode.removeChild(jacocoContainer);
				}
				arquillianRootNode.createChild("container")
						.attribute("qualifier", "jacoco")
						.attribute("default", "false")
						.createChild("configuration").createChild("property")
						.attribute("name", "javaVmArguments")
						.text("${jacoco.agent}");
				config.setContents(XMLParser
						.toXMLInputStream(arquillianRootNode));

			}
		}
	}
	
	private Profile findProfileById(String profileId, Model pom) {
		for (Profile profile : pom.getProfiles()) {
			if (profileId.equalsIgnoreCase(profile.getId())) {
				return profile;
			}
		}
		return null;
	}

	private Plugin findPluginByArtifactId(String artifactId, BuildBase build) {
		for (Plugin plugin : build.getPlugins()) {
			if (artifactId.equalsIgnoreCase(plugin.getArtifactId())) {
				return plugin;
			}
		}
		return null;
	}


}
