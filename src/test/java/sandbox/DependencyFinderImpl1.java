package sandbox;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import net.alchim31.maven.basicwebstart.DependencyFinder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.resolver.filter.TypeArtifactFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.codehaus.plexus.logging.Logger;

public class DependencyFinderImpl1 implements DependencyFinder {
    private MavenProject _project;
    private Logger plexusLogger;
    private ArtifactFactory artifactFactory;
    private DependencyTreeBuilder dependencyTreeBuilder;
    private ArtifactCollector artifactCollector;
    private ArtifactRepository localRepo;
    private ArtifactMetadataSource artifactMetadataSource;
    protected ArtifactResolver resolver;
    private LinkedList<?> remoteRepos = new LinkedList<Object>();

    public DependencyFinderImpl1(ArtifactFactory artifactFactory, MavenProject project) {
        super();
        this._project = project;
    }

    /** Visits a node (and all dependencies) to see if it contains duplicate scala versions */
    @SuppressWarnings("unchecked")
    public Collection<Artifact> find(Artifact template) throws Exception {
        System.out.println("logger " + plexusLogger);
        DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener( plexusLogger );
        Map managedVersions = _project.getManagedVersionMap();
        Set<Artifact> dependencyArtifacts = _project.getDependencyArtifacts();
        if ( dependencyArtifacts == null ) {
            dependencyArtifacts = _project.createArtifacts( artifactFactory, null, null );
        }
         Artifact rootArtifact = _project.getArtifact();
         /*
         for (Artifact a : dependencyArtifacts) {
             if (matchTemplate(a, template)) {
                 rootArtifact = a;
                 break;
             }
         }
         */
         // TODO: note that filter does not get applied due to MNG-3236
         AndArtifactFilter filter = new AndArtifactFilter();
         filter.add(new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME));
         filter.add(new TypeArtifactFilter("jar"));
         artifactCollector.collect( dependencyArtifacts, rootArtifact, managedVersions, localRepo,
                                     _project.getRemoteArtifactRepositories(), artifactMetadataSource, filter,
                                     Collections.singletonList( listener ) );

         MyArtifactCollector2 v = new MyArtifactCollector2();
         DependencyNode rootNode = listener.getRootNode();
         rootNode.accept(v);
         Collection<Artifact> back = v.artifacts;
         System.out.println("result :" +back.size());
         for (Artifact a : back ) {
             System.out.println("\t" + a);
         }
         return back;
    }

    //copy and adapted from http://maven.apache.org/shared/maven-dependency-tree/xref/org/apache/maven/shared/dependency/tree/DefaultDependencyTreeBuilder.html
    //TODO use real glob or regexp
    private boolean matchTemplate(Artifact a, Artifact template) {
        boolean back = true;
        back = back && ( "*".equals(template.getGroupId()) || template.getGroupId().equals(a.getGroupId()) );
        back = back && ( "*".equals(template.getArtifactId()) || template.getArtifactId().equals(a.getArtifactId()) );
        back = back && ( "*".equals(template.getVersion()) || template.getVersion().equals(a.getVersion()) );
        back = back && ( "*".equals(template.getClassifier()) || template.getClassifier().equals(a.getClassifier()) );
        return back;
    }

    class MyArtifactCollector  implements DependencyNodeVisitor {
        private Artifact _branchRootTemplate;
        private Artifact _branchRoot; //_branchRoot not null => collecting

        public MyArtifactCollector(Artifact branchRootTemplate) {
            _branchRootTemplate = branchRootTemplate;
        }

        public HashSet<Artifact> artifacts = new HashSet<Artifact>();

        //TODO use real glob or regexp
        private boolean matchBranchRootTemplate(Artifact a) {
            boolean back = true;
            back = back && ( "*".equals(_branchRootTemplate.getGroupId()) || _branchRootTemplate.getGroupId().equals(a.getGroupId()) );
            back = back && ( "*".equals(_branchRootTemplate.getArtifactId()) || _branchRootTemplate.getArtifactId().equals(a.getArtifactId()) );
            back = back && ( "*".equals(_branchRootTemplate.getVersion()) || _branchRootTemplate.getVersion().equals(a.getVersion()) );
            back = back && ( "*".equals(_branchRootTemplate.getClassifier()) || _branchRootTemplate.getClassifier().equals(a.getClassifier()) );
            return back;
        }

        public boolean endVisit(DependencyNode n) {
            if ((_branchRoot != null) && _branchRoot.equals(n)) {
                _branchRoot = null;
            }
            // several branch could be selected => scan all the tree
            return true;
        }

        public boolean visit(DependencyNode n) {
            Artifact a = n.getArtifact();
            if (!Artifact.SCOPE_TEST.equalsIgnoreCase(a.getScope()) && !Artifact.SCOPE_PROVIDED.equalsIgnoreCase(a.getScope()) ) {
                if ((_branchRoot == null) && matchBranchRootTemplate(a)){
                    _branchRoot = a;
                }
                System.out.println("try to add :" + (_branchRoot != null) + " : " + a);
                if ( _branchRoot != null ) {
                    try {
                        resolver.resolve(a, remoteRepos, localRepo);
                        if (a.getFile() != null) {
                            plexusLogger.debug("add file for : " + a + " in dependency set of " + _branchRootTemplate);
                            artifacts.add(a);
                        }
                    } catch (Exception e) {
                        plexusLogger.warn("failed to visit node " + n, e);
                    }
                }
            }
            return true;
        }
    }

    class MyArtifactCollector2  implements DependencyNodeVisitor {

        public HashSet<Artifact> artifacts = new HashSet<Artifact>();

        public boolean endVisit(DependencyNode n) {
            return true;
        }

        public boolean visit(DependencyNode n) {
            Artifact a = n.getArtifact();
            if (!Artifact.SCOPE_TEST.equalsIgnoreCase(a.getScope()) && !Artifact.SCOPE_PROVIDED.equalsIgnoreCase(a.getScope()) ) {
                try {
                    resolver.resolve(a, remoteRepos, localRepo);
                    if (a.getFile() != null) {
                        plexusLogger.debug("add file for : " + a );
                        artifacts.add(a);
                    }
                } catch (Exception e) {
                    plexusLogger.warn("failed to visit" + n, e);
                }
            }
            return true;
        }
    }

    private DependencyNode _rootNode = null;
    private DependencyNode getRootNode() throws Exception {
        if (_rootNode == null) {
            System.out.println("ctx : "+ _project + " - "+ localRepo + " - "+ artifactFactory + " - "+ artifactMetadataSource + " - "+ artifactCollector);
            AndArtifactFilter filter = new AndArtifactFilter();
            filter.add(new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME));
            filter.add(new TypeArtifactFilter("jar"));
            _rootNode = dependencyTreeBuilder.buildDependencyTree( _project, localRepo, artifactFactory, artifactMetadataSource, filter, null/*artifactCollector*/ );
        }
        return _rootNode;
    }

}