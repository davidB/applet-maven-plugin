package sandbox;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.maven.mercury.artifact.ArtifactMetadata;
import org.apache.maven.mercury.artifact.ArtifactScopeEnum;
import org.apache.maven.mercury.artifact.MetadataTreeNode;
import org.apache.maven.mercury.builder.api.DependencyProcessor;
import org.apache.maven.mercury.repository.api.LocalRepository;
import org.apache.maven.mercury.repository.api.RemoteRepository;
import org.apache.maven.mercury.repository.local.m2.LocalRepositoryM2;
import org.apache.maven.mercury.repository.remote.m2.RemoteRepositoryM2;
import org.apache.maven.mercury.repository.virtual.VirtualRepositoryReader;
import org.apache.maven.mercury.transport.api.Server;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;

// see http://www.sonatype.com/people/2008/10/mercury-externalized-dependencies/
// see http://docs.codehaus.org/display/MAVEN/HowTo+use+Mercury+for+accessing+repositories
public class MercurySample {
    public static void main(String[] args) {
        try {
            File             _testBase;
            LocalRepository  _localRepo;
            Server           _server;
            RemoteRepository _remoteRepo;
            VirtualRepositoryReader _vr;

            // null dependency processor as I don't use readDependencies()
            DependencyProcessor dp = DependencyProcessor.NULL_PROCESSOR;

            _testBase = new File( "/home/dwayne/.m2/repository" );

            _localRepo = new LocalRepositoryM2( _testBase, dp );

            _server = new Server( "remoteRepo", new URL("http://repo1.maven.org/maven2") );

            _remoteRepo = new RemoteRepositoryM2( _server, dp );

            _vr = new VirtualRepositoryReader(_localRepo, _remoteRepo);
            ArtifactMetadata am = _vr.readDependencies(new ArtifactMetadata( "org.apache.maven:maven-core:2.0.9" ));
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
