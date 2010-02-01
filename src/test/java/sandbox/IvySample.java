package sandbox;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.mercury.artifact.ArtifactMetadata;
import org.apache.maven.mercury.artifact.ArtifactQueryList;
import org.apache.maven.mercury.artifact.ArtifactScopeEnum;
import org.apache.maven.mercury.builder.api.DependencyProcessor;
import org.apache.maven.mercury.metadata.DependencyBuilder;
import org.apache.maven.mercury.metadata.DependencyBuilderFactory;
import org.apache.maven.mercury.repository.api.LocalRepository;
import org.apache.maven.mercury.repository.api.RemoteRepository;
import org.apache.maven.mercury.repository.api.Repository;
import org.apache.maven.mercury.repository.local.m2.LocalRepositoryM2;
import org.apache.maven.mercury.repository.remote.m2.RemoteRepositoryM2;
import org.apache.maven.mercury.repository.virtual.VirtualRepositoryReader;
import org.apache.maven.mercury.transport.api.Server;

// see http://www.sonatype.com/people/2008/10/mercury-externalized-dependencies/
// see http://docs.codehaus.org/display/MAVEN/HowTo+use+Mercury+for+accessing+repositories
public class IvySample {
    public static void main(String[] args) {
        try {
            File             _testBase;
            LocalRepository  _localRepo;
            Server           _server;
            RemoteRepository _remoteRepo;
            VirtualRepositoryReader _vr;

            // null dependency processor as I don't use readDependencies()
            //PlexusMercury pm = new DefaultPlexusMercury();
            //DependencyProcessor dp =  new MavenDependencyProcessor();//pm.findDependencyProcessor(); //DependencyProcessor.NULL_PROCESSOR;
            DependencyProcessor dp = DependencyProcessor.NULL_PROCESSOR;

            _testBase = new File( "/home/dwayne/.m2/repository" );

            _localRepo = new LocalRepositoryM2( _testBase, dp );

            _server = new Server( "remoteRepo", new URL("http://repo1.maven.org/maven2") );

            _remoteRepo = new RemoteRepositoryM2( _server, dp );
            ArtifactMetadata metadata = new ArtifactMetadata( "com.mimesis-republic.blackmamba:blackmamba-core:0.7.5-SNAPSHOT" );


            ArrayList<Repository> repos = new ArrayList<Repository>();
            repos.add(_localRepo);
            ArtifactScopeEnum   scope = ArtifactScopeEnum.runtime;

            DependencyBuilder depBuilder = DependencyBuilderFactory.create( DependencyBuilderFactory.JAVA_DEPENDENCY_MODEL, repos );
            List<ArtifactMetadata> res = depBuilder.resolveConflicts( scope, new ArtifactQueryList( metadata ), null, null );
            for (ArtifactMetadata am2 : res) {
                System.out.println(am2);
            }

            _vr = new VirtualRepositoryReader(_localRepo, _remoteRepo);
            ArtifactMetadata am = _vr.readDependencies(metadata);
            for (ArtifactMetadata am2 : am.getDependencies()) {
                System.out.println(am2);
            }
//            DependencyTreeBuilder dtb = new DependencyTreeBuilder( null, null, null, reps, processor );
//            MetadataTreeNode root = dtb.buildTree(  );
//            //root.
//            List cp = dtb.resolveConflicts( ArtifactScopeEnum.compile );
        } catch(Exception exc) {
            exc.printStackTrace();
        }
    }
}
