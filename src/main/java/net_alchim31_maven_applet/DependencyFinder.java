package net_alchim31_maven_applet;

import java.util.Collection;

import org.apache.maven.artifact.Artifact;

public interface DependencyFinder {
    public Collection<Artifact> find(Artifact template) throws Exception;
}
