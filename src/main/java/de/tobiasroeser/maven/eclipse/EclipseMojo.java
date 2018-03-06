package de.tobiasroeser.maven.eclipse;

import static de.tototec.utils.functional.FList.concat;
import static de.tototec.utils.functional.FList.foreach;
import static de.tototec.utils.functional.FList.map;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import de.tototec.utils.functional.Optional;
import de.tototec.utils.functional.Procedure1;

/**
 * Generated Eclipse project files from the current Maven project.
 */
@Mojo(name = "eclipse", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class EclipseMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject mavenProject;

	@Parameter(required = true, property = "eclipse.outputDirectory", defaultValue = "${project.build.outputDirectory}")
	private String outputDirectory;

	@Parameter(required = true, property = "eclipse.testOutputDirectory", defaultValue = "${project.build.outputDirectory}")
	private String testOutputDirectory;

	/**
	 * Use the alternative build output directory.
	 */
	@Parameter(required = false, property = "eclipse.altTarget", alias = "altTarget")
	private String alternativeOutput;

	/**
	 * Should the generated source paths be optional.
	 */
	@Parameter(required = false, property = "eclipse.sourcesOptional")
	private boolean sourcesOptional = true;

	@Parameter(required = false, property = "eclipse.dryrun")
	private boolean dryrun = false;

	@Parameter(required = false, property = "eclipse.skip")
	private boolean skip = false;

	@Parameter(required = false, property = "eclipse.defaultBuilders")
	private boolean defaultBuilders = true;

	@Parameter(required = false, property = "eclipse.defaultNatures")
	private boolean defaultNatures = true;

	@Parameter(required = false, property = "eclipse.extraBuilders")
	private List<String> extraBuilders = new LinkedList<String>();

	@Parameter(required = false, property = "eclipse.extraNatures")
	private List<String> extraNatures = new LinkedList<String>();

	@Parameter(required = false, property = "eclipse.extraSources")
	private List<String> extraSources = new LinkedList<String>();

	@Parameter(required = false, property = "eclipse.extraResources")
	private List<String> extraResources = new LinkedList<String>();

	@Parameter(required = false, property = "eclipse.extraTestSources")
	private List<String> extraTestSources = new LinkedList<String>();

	@Parameter(required = false, property = "eclipse.extraTestResources")
	private List<String> extraTestResources = new LinkedList<String>();

	public EclipseMojo() {
	}

	protected ProjectConfig readProjectConfig() {

		final List<String> builders = !defaultBuilders
				? Collections.<String>emptyList()
				: Arrays.asList(
						"org.eclipse.jdt.core.javabuilder",
						"org.eclipse.m2e.core.maven2Builder");

		final List<String> natures = !defaultNatures
				? Collections.emptyList()
				: Arrays.asList(
						"org.eclipse.jdt.core.javanature",
						"org.eclipse.m2e.core.maven2Nature");

		// initial config
		final ProjectConfig projectConfig = new ProjectConfig()
				.withName(Optional.lift(mavenProject.getName())
						.orElse(Optional.lift(mavenProject.getArtifactId()))
						.getOrElse("undefined"))
				.withComment(Optional.lift(mavenProject.getDescription()).getOrElse(""))
				.withSources(mavenProject.getCompileSourceRoots())
				.withTestSources(mavenProject.getTestCompileSourceRoots())
				.withResources(map(mavenProject.getBuild().getResources(), r -> r.getDirectory()))
				.withTestResources(map(mavenProject.getBuild().getTestResources(), r -> r.getDirectory()))
				.withBuilder(builders)
				.withNatures(natures);

		// enhance with config values
		return projectConfig
				.withSources(concat(projectConfig.getSources(), extraSources))
				.withResources(concat(projectConfig.getResources(), extraResources))
				.withTestSources(concat(projectConfig.getTestSources(), extraTestSources))
				.withTestResources(concat(projectConfig.getTestResources(), extraTestResources))
				.withBuilder(concat(projectConfig.getBuilders(), extraBuilders))
				.withNatures(concat(projectConfig.getNatures(), extraNatures));
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			getLog().info("Skipping eclipse");
		}

		final ProjectConfig projectConfig = readProjectConfig();
		getLog().debug("Analyzer: " + projectConfig);

		final File basedir = mavenProject.getBasedir();
		final Tasks tasks = new Tasks(basedir);

		final File projectFile = new File(basedir.getPath(), ".project");
		generateFile(projectFile, dryrun, printStream -> {
			tasks.generateProjectFile(printStream, projectConfig);
		});

		final File classpathFile = new File(basedir.getPath(), ".classpath");
		generateFile(classpathFile, dryrun, printStream -> {
			tasks.generateClasspathFileContent(
					printStream, projectConfig,
					Optional.lift(alternativeOutput),
					outputDirectory, testOutputDirectory,
					sourcesOptional);
		});
	}

	protected void generateFile(final File file, final boolean dryrun, final Procedure1<PrintStream> generator)
			throws MojoExecutionException {

		final Charset charset = StandardCharsets.UTF_8;
		final OutputStream out;
		final LinkedList<Runnable> onEnd = new LinkedList<>();

		if (dryrun) {
			final ByteArrayOutputStream bout = new ByteArrayOutputStream();
			onEnd.add(() -> {
				final String fileContent = new String(bout.toByteArray(), charset);
				getLog().info("(dryrun) I would generate: " + file +
						" with content:\n" + fileContent);
			});
			out = bout;
		} else {
			if (file.exists()) {
				getLog().info("Overwriting existing file: " + file);
			}
			try {
				out = new FileOutputStream(file);
			} catch (final FileNotFoundException e) {
				throw new MojoExecutionException("Could not write file: " + file, e);
			}
		}

		try (final PrintStream printStream = new PrintStream(out, false, charset.name());) {
			generator.apply(printStream);
			printStream.flush();
		} catch (final UnsupportedEncodingException e) {
			throw new MojoExecutionException("Could not create print stream", e);
		}

		foreach(onEnd, r -> r.run());
	}

}