package net.alchim31.maven.basicwebstart;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.resolver.filter.TypeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.apache.maven.shared.dependency.tree.traversal.CollectingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;

import org.codehaus.plexus.logging.Logger;

/**
 * generate jnlp (from template), rename, sign, pack jar
 *
 * @goal jwsdir
 * @phase package
 * @author david.bernard
 */
public class JwsDirMojo extends AbstractMojo implements org.codehaus.plexus.logging.LogEnabled{

    /**
     * Location of the file.
     * @parameter expression="${project.basedir}/src/main/jws"
     * @required
     */
    private File inputDirectory;

    /**
     * Location of the file.
     * @parameter expression="${project.build.directory}/jws"
     * @required
     */
    private File outputDirectory;

    /**
     * Template values, a set of properties available in the template processing
     *
     * @parameter
     */
    private Properties templateValues;

    /**
     * Configuration of the signature to used.
     *
     * @parameter
     */
    private SignConfig sign;

    /**
     * Should generate a .pack.gz version of the jar.
     * The value could be used in template call via "${packEnabled}", use it
     * to avoid to keep sync your pom.xml and you .jnlp.vm
     *
     * @see http://java.sun.com/javase/6/docs/technotes/guides/jweb/tools/pack200.html#pack200
     * @parameter expression="${jnlp.packEnabled}" default-value="true"
     */
    private boolean packEnabled;

    /**
     * Optionnal additionnal options to use when calling pack200 (eg:
     * --modification-time=latest --deflate-hint="true" --strip-debug).
     *
     * @see http://java.sun.com/j2se/1.5.0/docs/guide/deployment/deployment-guide/pack200.html#pack200_compression
     * @see http://java.sun.com/javase/6/docs/technotes/guides/jweb/tools/pack200.html#pack200
     * @parameter expression="${jnlp.packOptions}"
     */
    private String[] packOptions;

    /**
     * Should generate a jar with version number that follow java-plugin convention ?
     * The value could be used in template call via "${versionEnabled}", use it
     * to avoid to keep sync your pom.xml and you .jnlp.vm
     *
     * @see http://java.sun.com/javase/6/docs/technotes/guides/jweb/tools/pack200.html#versionDownload
     * parameter expression="${jnlp.versionEnabled}" default-value="true"
     */
    private static boolean versionEnabled = false;     //TODO support versionEnable = false

    /**
     * Enable verbose
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private boolean verbose;

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     * @required
     * @readonly
     */
    protected ArtifactMetadataSource artifactMetadataSource;


    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @parameter expression="${component.org.apache.maven.artifact.resolver.ArtifactResolver}"
     * @required
     * @readonly
     */
    protected ArtifactResolver resolver;

    /**
     * Location of the local repository.
     *
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     */
    protected ArtifactRepository localRepo;
    private LinkedList<?> remoteRepos = new LinkedList<Object>();
    
    /**
     * To look up Archiver/UnArchiver implementations
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.manager.ArchiverManager}"
     * @required
     */
    protected ArchiverManager archiverManager;

    /**
     * The artifact collector to use.
     * 
     * @component
     * @required
     * @readonly
     */
    private ArtifactCollector artifactCollector;

    /**
     * The dependency tree builder to use.
     * 
     * @component
     * @required
     * @readonly
     */
    private DependencyTreeBuilder dependencyTreeBuilder;
    
    public void execute() throws MojoExecutionException {
        try {
            getLog().info("start on : " + inputDirectory);
            if ((inputDirectory == null) || !inputDirectory.exists()) {
                getLog().debug("directory not found : " + inputDirectory);
                return;
            }
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            initJars(); //reset jarList before template fill it

            VelocityContext context = initTemplateContext();
            for(File file : inputDirectory.listFiles()) {
                if (file.getName().endsWith(".vm") && file.canRead() && !file.getName().startsWith(".#") && !file.getName().startsWith("#")) {
                    getLog().info("process template : " + file.getName());
                    // convert template to regular file in the outputdiretory and remove the '.vm' extension
                    generateTemplate(context, file, new File(outputDirectory, file.getName().substring(0, file.getName().length() - 3)));
                } else {
                    // copy file
                    FileUtils.copyFile(file, new File(outputDirectory, file.getName()));
                }
            }

            getLog().info("processJars");
            processJars(outputDirectory);
        } catch (Exception e ) {
            throw new MojoExecutionException("Error generation jws dir", e );
        }
    }

    private VelocityContext initTemplateContext() throws Exception {
        Velocity.init();
        VelocityContext context = new VelocityContext();
        context = addProperties(context, System.getProperties());
        context = addProperties(context, templateValues);
        context.put("jws", this);
        context.put("project", project);
        context.put("packEnabled", packEnabled);
        context.put("versionEnabled", versionEnabled);
        return context;
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
                            getLog().debug("add file for : " + a + " in dependency set of " + _branchRootTemplate);
                            artifacts.add(a);
                        }
                    } catch (Exception e) {
                        getLog().warn(e);
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
                        getLog().debug("add file for : " + a );
                        artifacts.add(a);
                    }
                } catch (Exception e) {
                    getLog().warn(e);
                }
            }
            return true;
        }
    }

    private DependencyNode _rootNode = null;
    private DependencyNode getRootNode() throws Exception {
        if (_rootNode == null) {
            System.out.println("ctx : "+ project + " - "+ localRepo + " - "+ artifactFactory + " - "+ artifactMetadataSource + " - "+ artifactCollector);
            AndArtifactFilter filter = new AndArtifactFilter();
            filter.add(new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME));
            filter.add(new TypeArtifactFilter("jar"));
            _rootNode = dependencyTreeBuilder.buildDependencyTree( project, localRepo, artifactFactory, artifactMetadataSource, filter, null/*artifactCollector*/ );
        }
        return _rootNode;
    }

    public Collection<Artifact> findDependencies() throws Exception {
        return findDependencies(project.getArtifact());
    }

    public Collection<Artifact> findDependencies(String groupId, String artifactId, String version, String classifier) throws Exception {
        Artifact artifactTemplate = artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, "jar", classifier);
        return findDependencies(artifactTemplate);
    }

    /** Visits a node (and all dependencies) to see if it contains duplicate scala versions */
    public Collection<Artifact> findDependencies(Artifact template) throws Exception {
        return new DependenciesTools().findDependencies2(template);
        /*
        DependencyNode rootNode = getRootNode();
        getLog().debug("findDependencies of " + artifactTemplate);
        final MyArtifactCollector visitor = new MyArtifactCollector(artifactTemplate);
        rootNode.accept( visitor );
        return visitor.artifacts;
        */
    }
    
    class DependenciesTools {
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

        @SuppressWarnings("unchecked")
        private Collection<Artifact> findDependencies2(Artifact template) throws Exception {
            System.out.println("logger " + plexusLogger);
            DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener( plexusLogger );
            Map managedVersions = project.getManagedVersionMap();
            Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();
            if ( dependencyArtifacts == null ) {
                dependencyArtifacts = project.createArtifacts( artifactFactory, null, null );
            }
             Artifact rootArtifact = project.getArtifact();
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
                                         project.getRemoteArtifactRepositories(), artifactMetadataSource, filter,
                                         Collections.singletonList( listener ) );
         
             MyArtifactCollector2 v = new MyArtifactCollector2();
             DependencyNode rootNode = listener.getRootNode();
             rootNode.accept(v);
             Collection<Artifact> back =v.artifacts;
             System.out.println("result :" +back.size());
             for (Artifact a : back ) {
                 System.out.println("\t" + a);
             }
             return back;
        }
    }
    
    @SuppressWarnings("unchecked")
    private VelocityContext addProperties(VelocityContext context, Properties p) throws Exception {
        if (p != null) {
            Enumeration e = p.propertyNames();
            while (e.hasMoreElements()) {
                String key = (String)e.nextElement();
                String value = p.getProperty(key);
                context.put(key, value);
            }
        }
        return context;
    }

    //during the convertion some the template should call the addJar(dependency) method to request the copy (+ sign + pack +...) of the jar in the outputDirectory
    private void generateTemplate(VelocityContext context, File in, File out) throws Exception {
        //template = Velocity.getTemplate("mytemplate.vm");
        FileReader input = new FileReader(in);
        try {
            FileWriter output = new FileWriter(out);
            try {
                if (!Velocity.evaluate(context, output, in.getName(), input)) {
                    getLog().warn("failed to process template :" + in);
                }
//            Template template = Velocity.getTemplate(path);
//            template.merge(context, output);
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private static HashSet<Artifact> _jars = null;
//    public static String addJar(String jar) throws Exception {
//        _jars.add(jar);
//        String back = "href=\"" + jar + "\"";
//        return back;
//    }

    public static String addJar(Artifact artifact) throws Exception {
        _jars.add(artifact);
        StringBuilder back = new StringBuilder();
        back.append("href=\"")
            .append(findFilename(artifact, false))
            .append('"')
            ;
        if (!isSnapshot(artifact)) {
            back.append(" version=\"").append(artifact.getVersion()).append('"');
        }
        //back.append(" size=\"").append(artifact.getFile().length()).append('"');
        return back.toString();
    }

    private void initJars() throws Exception {
        _jars = new HashSet<Artifact>();
    }

    // see http://java.sun.com/j2se/1.5.0/docs/guide/deployment/deployment-guide/pack200.html
    // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5078608
    //    Step 1:  Repack the file to normalize the jar, repacking calls the packer and unpacks the file in one step.
    //
    //    % pack200 --repack HelloWorld.jar
    //
    //    Step 2: Sign the jar after we normalize using repack.
    //
    //    % jarsigner -keystore myKeystore HelloWorld.jar ksrini
    //
    //    Verify the just signed jar to ensure the signing worked.
    //
    //    % jarsigner -verify HelloWorld.jar
    //    jar verified.
    //
    //    Ensure the jar still works.
    //
    //    % Java -jar HelloWorld.jar
    //    HelloWorld
    //
    //    Step 3: Now we pack the file
    //
    //    % pack200 HelloWorld.jar.pack.gz HelloWorld.jar
    //
    //    Step 4: Unpack the file
    //
    //    % unpack200 HelloWorld.jar.pack.gz HelloT1.jar
    //
    //    Step 5:  Verify the jar
    //
    //    % jarsigner -verify HelloT1.jar
    //    jar verified.
    private void processJars(File outputDir) throws Exception {
        JarSigner signer = new JarSigner(sign, JarUtil.createTempDir(), getLog(), verbose);
        for(Artifact artifact : _jars) {
            if (artifact == null) {
                continue;
            }
            File in = artifact.getFile();
            File dest = new File(outputDir, findFilename(artifact, true));
            getLog().debug("unsign :" + in + " to " + dest);
            JarUtil.unsign(in, dest, archiverManager, true);
            
            if (packEnabled) {
                getLog().debug("repack : " + dest);
                JarUtil.repack(dest, packOptions, getLog());
            }
            
            getLog().debug("sign  : " + dest);
            signer.sign(dest, dest);
            
            //signer.verify(out);
            if (packEnabled) {
                getLog().debug("pack :" + dest);
                File tmp3 = new File(outputDir, "tmp3.jar");
                try {
                    JarUtil.pack(dest, packOptions, getLog());

                    getLog().debug("verify packed");
                    JarUtil.unpack(dest, tmp3, getLog());
                    JarUtil.verifySignature(tmp3, getLog());
                } finally {
                    tmp3.delete();
                }
            }
            getLog().info("end generation of " + dest);
        }
    }

    private static boolean isSnapshot(Artifact artifact) {
        return artifact.getVersion().toUpperCase().endsWith("-SNAPSHOT");
    }

    private static String findFilename(Artifact artifact, boolean withVersion) {
        StringBuilder back = new StringBuilder();
        back.append(artifact.getArtifactId());
        if (versionEnabled) {
            if (artifact.hasClassifier()) {
                back.append('-').append(artifact.getClassifier());
            }
            if (isSnapshot(artifact)) {
                back.append('-').append(artifact.getVersion());
            }
            if (withVersion && !isSnapshot(artifact)) {
                back.append("__V").append(artifact.getVersion());
            }
        } else {
            back.append('-').append(artifact.getVersion());
            if (artifact.hasClassifier()) {
                back.append('-').append(artifact.getClassifier());
            }
        }
        back.append('.').append(artifact.getType());
        return back.toString();
    }

    Logger plexusLogger = null;
    public void enableLogging(Logger logger) {
        plexusLogger = logger;
    }
    
}