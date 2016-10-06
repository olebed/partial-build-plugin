package com.lesfurets.maven.partial.mojos;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.lesfurets.maven.partial.core.*;
import com.lesfurets.maven.partial.utils.MavenToPlexusLogAdapter;
import com.lesfurets.maven.partial.utils.PluginUtils;

@Mojo(name = "writeChanged", defaultPhase = LifecyclePhase.VALIDATE,
                threadSafe = true, inheritByDefault = false, aggregator = true)
public class ChangedMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    @Parameter(defaultValue = "${session}")
    private MavenSession session;

    @Parameter(required = false, property = Property.PREFIX + "enabled", defaultValue = "true")
    public boolean enabled;

    @Parameter(required = false, property = Property.PREFIX + "key", defaultValue = "")
    public String key;

    @Parameter(required = false, property = Property.PREFIX + "referenceBranch", defaultValue =
                    "refs/remotes/origin/develop")
    public String referenceBranch;

    @Parameter(required = false, property = Property.PREFIX + "baseBranch", defaultValue = "HEAD")
    public String baseBranch;

    @Parameter(required = false, property = Property.PREFIX + "uncommited", defaultValue = "true")
    public boolean uncommited;

    @Parameter(required = false, property = Property.PREFIX + "makeUpstream", defaultValue = "false")
    public boolean makeUpstream;

    @Parameter(required = false, property = Property.PREFIX + "skipTestsForNotImpactedModules", defaultValue = "false")
    public boolean skipTestsForNotImpactedModules;

    @Parameter(required = false, property = Property.PREFIX + "buildAll", defaultValue = "false")
    public boolean buildAll;

    @Parameter(required = false, property = Property.PREFIX + "compareToMergeBase", defaultValue = "true")
    public boolean compareToMergeBase;

    @Parameter(required = false, property = Property.PREFIX + "fetchBaseBranch", defaultValue = "false")
    public boolean fetchBaseBranch;

    @Parameter(required = false, property = Property.PREFIX + "fetchReferenceBranch", defaultValue = "false")
    public boolean fetchReferenceBranch;

    @Parameter(required = false, property = Property.PREFIX + "outputFile", defaultValue = "${project" +
                    ".basedir}/changed.projects")
    public String outputFile;

    @Parameter(required = false, property = Property.PREFIX + "writeChanged", defaultValue = "false")
    public String writeChanged;

    @Parameter(required = false, property = Property.PREFIX + "ignoreChanged")
    public String ignoreChanged;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!project.isExecutionRoot()) {
            return;
        }

        Injector injector = Guice.createInjector(new GuiceModule(new MavenToPlexusLogAdapter(getLog()), session));
        UnchangedProjectsRemover projectsRemover = injector.getInstance(UnchangedProjectsRemover.class);
        ChangedProjects changedProjects = injector.getInstance(ChangedProjects.class);
        Configuration configuration = injector.getInstance(Configuration.class);

        getLog().info(configuration.toString());

        try {
            Set<MavenProject> changed = changedProjects.get();
            Set<MavenProject> ignoredProjects = configuration.getIgnoredProjects();
            changed.removeAll(ignoredProjects);
            projectsRemover.collectDependentProjects(changed);

            List<MavenProject> sortedChanged = session.getProjects().stream()
                            .filter(changed::contains)
                            .collect(Collectors.toList());

            PluginUtils.writeChangedProjectsToFile(sortedChanged, new File(outputFile));
            session.getProjects().forEach(m -> m.getProperties()
                            .setProperty(UnchangedProjectsRemover.CHANGED_PROJECTS, PluginUtils.joinProjectIds
                                            (sortedChanged, new StringJoiner(",")).toString()));
        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
        }
    }

}
