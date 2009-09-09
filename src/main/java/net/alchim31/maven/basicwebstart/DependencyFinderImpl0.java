package net.alchim31.maven.basicwebstart;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/**
 * Default implementation that always return the Runtime Dependencies of the project.
 *
 * @author david.bernard
 */
public class DependencyFinderImpl0 implements DependencyFinder {
    private MavenProject _project;

    public DependencyFinderImpl0(MavenProject project) {
        super();
        this._project = project;
    }

    @SuppressWarnings("unchecked")
    public Collection<Artifact> find(Artifact template) throws Exception {
        ArrayList<Artifact> back = new ArrayList<Artifact>();
        back.add(_project.getArtifact());
        back.addAll(_project.getRuntimeArtifacts());
        return back;
        //return new DependenciesTools().findDependencies2(template);
        /*
        DependencyNode rootNode = getRootNode();
        getLog().debug("findDependencies of " + artifactTemplate);
        final MyArtifactCollector visitor = new MyArtifactCollector(artifactTemplate);
        rootNode.accept( visitor );
        return visitor.artifacts;
        */
    }
}