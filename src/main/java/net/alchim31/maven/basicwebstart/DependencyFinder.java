package net.alchim31.maven.basicwebstart;

import java.util.Collection;

import org.apache.maven.artifact.Artifact;

public interface DependencyFinder {
    public Collection<Artifact> find(Artifact template) throws Exception;
}
