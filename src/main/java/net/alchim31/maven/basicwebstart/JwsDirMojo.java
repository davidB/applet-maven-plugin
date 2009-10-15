package net.alchim31.maven.basicwebstart;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.codehaus.plexus.util.FileUtils;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

/**
 * generate jnlp (from template), rename, sign, pack jar
 *
 * @goal jwsdir
 * @phase package
 * @author david.bernard
 */
public class JwsDirMojo extends AbstractMojo { //implements org.codehaus.plexus.logging.LogEnabled{

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
     * Should unpack and verify signature after packing ?
     * @parameter expression="${jnlp.pack.verify.signature}" default-value="false"
     */
    private boolean packVerifySignature;

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
     * Size of the Thread Pool use to process jar (unsign, sign, packe,...) (default is nb of processor)
     *
     * @parameter expression="${jws.nbprocessor}" 
     */
    private int nbProcessor = Runtime.getRuntime().availableProcessors();
    
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

    /**
     * Artifact factory, needed to download source jars.
     *
     * @component
     * @required
     * @readonly
     */
    protected MavenProjectBuilder mavenProjectBuilder;
    
//    /**
//     * The artifact collector to use.
//     *
//     * @component
//     * @required
//     * @readonly
//     */
//    private ArtifactCollector artifactCollector;
//
//    /**
//     * The dependency tree builder to use.
//     *
//     * @component
//     * @required
//     * @readonly
//     */
//    private DependencyTreeBuilder dependencyTreeBuilder;

    private DependencyFinder _depFinder = null;

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

            getLog().info("step 0 : initialisation");
            initJars(); //reset jarList before template fill it
            _depFinder = new DependencyFinderImpl0(project);

            getLog().info("step 1 : register proguard out jar (if exists)");
            for(File file : inputDirectory.listFiles()) {
                if (file.getName().startsWith(".#") || file.getName().startsWith("#")) {
                    continue;
                }
                String classifier = null;
                if (file.getName().endsWith(".proguard.conf") && file.canRead()) {
                    classifier = file.getName().substring(0, file.getName().length() - ".proguard.conf".length());
                }
                if (file.getName().endsWith(".proguard.conf.vm") && file.canRead()) {
                    classifier = file.getName().substring(0, file.getName().length() - ".proguard.conf.vm".length());
                }
                if (classifier != null) {
                    Artifact pArtifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), VersionRange.createFromVersion(project.getVersion()), Artifact.SCOPE_RUNTIME, "jar", classifier, project.getArtifact().getArtifactHandler(), true);
                    pArtifact.setFile(new File(outputDirectory, project.getArtifactId() + "-" + project.getVersion() + "-" + classifier + ".jar"));
                    _generatedJars.add(pArtifact);
                    getLog().debug(" - - register" + pArtifact);
                }
            }

            getLog().info("step 2 : copy files and process templates");
            VelocityContext context = initTemplateContext();
            for(File file : inputDirectory.listFiles()) {
                if (file.getName().endsWith(".vm") && file.canRead() && !file.getName().startsWith(".#") && !file.getName().startsWith("#")) {
                    getLog().info(" - - process template : " + file.getName());
                    // convert template to regular file in the outputdiretory and remove the '.vm' extension
                    File out = new File(outputDirectory, file.getName().substring(0, file.getName().length() - 3));
                    generateTemplate(context, file, out);
                } else {
                    // copy file
                    FileUtils.copyFile(file, new File(outputDirectory, file.getName()));
                }
            }

            getLog().info("step 3 : run proguard on *.proguard.conf");
            for (Artifact a : findGenerated()) {
                File confFile =  new File(outputDirectory, a.getClassifier() + ".proguard.conf");
                if (confFile.canRead()) {
                    getLog().info(" - - read configuration file " + confFile + " to generate " + a);
                    ProguardHelper.run(confFile, a.getFile());
                } else {
                    getLog().warn(" - - can't read configuration file " + confFile + " to generate " + a);
                }
            }

            getLog().info("step 4 : generate .jar (and .pack.gz)");
            processJars(outputDirectory);
        } catch (Exception e ) {
            throw new MojoExecutionException("Error generation jws dir", e );
        }
    }

    private static HashSet<Artifact> _generatedJars = new HashSet<Artifact>();
    private static HashMap<String, JarMerger> _mergedJars = new HashMap<String, JarMerger>();

    public Collection<Artifact> findGenerated() throws Exception {
        //System.out.println("size of _generatedJars when requested : " + _generatedJars.size());
        return _generatedJars;
    }

    public Collection<Artifact> findGenerated(String classifier) throws Exception {
        HashSet<Artifact> back = new HashSet<Artifact>();
        for(Artifact a : _generatedJars) {
            if (classifier.equalsIgnoreCase(a.getClassifier())) {
                back.add(a);
            }
        }
        //System.out.println("size of _generatedJars for "+ classifier + " when requested : " + back.size());
        return back;
    }

    private VelocityContext initTemplateContext() throws Exception {
        Velocity.init();
        VelocityContext context = new VelocityContext();
        context = addProperties(context, System.getProperties());
        context = addProperties(context, project.getProperties());
        context = addProperties(context, templateValues);
        context.put("outputDir", this.outputDirectory);
        context.put("jws", this);
        context.put("project", project);
        context.put("packEnabled", packEnabled);
        context.put("versionEnabled", versionEnabled);
        return context;
    }

    //TODO check if classifier is not already created (cache and warn)
    public JarMerger newJarMerger(String classifier) throws Exception {
        JarMerger back = _mergedJars.get(classifier);
        if (back == null) {
            Artifact result = artifactFactory.createArtifactWithClassifier(project.getGroupId(), project.getArtifactId(), project.getVersion(), "jar", classifier);
            back = new JarMerger(result, getLog());
            _mergedJars.put(classifier, back);
        } else {
            getLog().warn("reuse already define jarMerger for classifier '"+ classifier +"'");
        }
        return back;
    }

    @SuppressWarnings("unchecked")
    public Collection<Artifact> findArtifact(String groupId, String artifactId, String version, String classifier, boolean withDependencies) throws Exception {
        Set<Artifact> back = new HashSet<Artifact>();
        LinkedList<?> remoteRepos = new LinkedList<Object>();
        Artifact artifact = artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, "jar", classifier);
        resolver.resolve(artifact, remoteRepos, localRepo);
        back.add(artifact);
        if (withDependencies) {
            //TODO refactor to reuse findDependencies
            Artifact pomArtifact = artifactFactory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), "", "pom");
            MavenProject pomProject = mavenProjectBuilder.buildFromRepository(pomArtifact, remoteRepos, localRepo);
            //protected Set<Artifact> resolveDependencyArtifacts(MavenProject theProject) throws Exception {
            AndArtifactFilter filter = new AndArtifactFilter();
            filter.add(new ScopeArtifactFilter(Artifact.SCOPE_TEST));
            filter.add(new ArtifactFilter(){
                public boolean include(Artifact artifact) {
                    return !artifact.isOptional();
                }
            });
            //TODO follow the dependenciesManagement and override rules
            Set<Artifact> deps = pomProject.createArtifacts(artifactFactory, Artifact.SCOPE_RUNTIME, filter);
            for (Artifact dep : deps) {
                resolver.resolve(dep, remoteRepos, localRepo);
            }
            back.addAll(deps);
        }
        return back;
    }

    public Collection<Artifact> findDependencies() throws Exception {
        return findDependencies(project.getArtifact());
    }

    public Collection<Artifact> findDependencies(String groupId, String artifactId, String version, String classifier) throws Exception {
        Artifact artifactTemplate = artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, "jar", classifier);
        return findDependencies(artifactTemplate);
    }

    public Collection<Artifact> findDependencies(Artifact template) throws Exception {
        return _depFinder.find(template);
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
                    getLog().warn(" - - failed to process template :" + in);
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

    // keep for backward compatibility
    public static CharSequence addJar(Artifact artifact) throws Exception {
        return addJar(artifact, true);
    }

    public static CharSequence addJar(Artifact artifact, boolean outputAsJnlpAttributes) throws Exception {
        _jars.add(artifact);
        if (outputAsJnlpAttributes) {
            StringBuilder back = new StringBuilder();
            back.append("href=\"")
                .append(findFilename(artifact, false))
                .append('"')
                ;
            if (!isSnapshot(artifact)) {
                back.append(" version=\"").append(artifact.getVersion()).append('"');
            }
            //back.append(" size=\"").append(artifact.getFile().length()).append('"');
            return back;
        }
        return findFilename(artifact, false);
    }
    
    private void initJars() throws Exception {
        _jars = new HashSet<Artifact>();
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
    private void processJars(final File outputDir) throws Exception {
        final JarSigner signer = new JarSigner(sign, JarUtil.createTempDir(), getLog(), verbose);
        List<Artifact> jars = Lists.newArrayList(Collections2.filter(_jars, new Predicate<Artifact>(){
            public boolean apply(Artifact arg0) {
                return arg0 != null && arg0.getFile() != null && arg0.getFile().exists();
            }
        }));
        Collections.sort(jars, new Comparator<Artifact>(){
            public int compare(Artifact o1, Artifact o2) {
                return o1.getArtifactId().compareToIgnoreCase(o2.getArtifactId());
            }
        });
        getLog().info(" - - thread pool size : " + nbProcessor);
        final ExecutorService exec = Executors.newFixedThreadPool(nbProcessor);
        Iterable<Future<ProcessJarResult>> rjars = exec.invokeAll(Collections2.transform(jars, new Function<Artifact, Callable<ProcessJarResult>>(){
            public Callable<ProcessJarResult> apply(final Artifact arg0) {
                return new Callable<ProcessJarResult>() {
                    public ProcessJarResult call() throws Exception {
                        return processJar(arg0, outputDir, getLog(), signer);                    
                    }
                };
            }
        }));
        
        getLog().debug(" - - waiting end of jar processing...");
        long totalSizeJar = 0;
        long totalSizePacked = 0;
        long nbFile = 0;
        for(Future<ProcessJarResult> rjar : rjars) {
            ProcessJarResult result = rjar.get();
            nbFile++;
            if (result.jar != null) {
                totalSizeJar += result.jar.length();
            }
            if (result.packed != null) {
                totalSizePacked += result.packed.length();
            }
        }
        getLog().info(" - - total size of .jar         : " + totalSizeJar / 1024 + " KB");
        if ((totalSizeJar > 0) &&  (totalSizePacked > 0)) {
            getLog().info(" - - total size of .jar.pack.gz : " + totalSizePacked / 1024 + " KB ~ " + (totalSizePacked*100/totalSizeJar) + "%");
        }
        getLog().info(" - - total number of jar        : " +  nbFile);
    }
    
    public static class ProcessJarResult {
        public File packed;
        public File jar;
    }

    public ProcessJarResult processJar(Artifact artifact, File outputDir, Log logger, JarSigner signer) throws Exception {
        ProcessJarResult back = new ProcessJarResult();
        File in = artifact.getFile();
        File dest = new File(outputDir, findFilename(artifact, true));
        logger.debug(" - - unsign :" + in + " to " + dest);
        if (JarMerger.GROUP_ID.equals(artifact.getGroupId())) {
            in.renameTo(dest);
            artifact.setFile(dest);
        } else {
            JarUtil.rejar(in, dest, true, true, logger);
        }

//        getLog().debug(" - - create INDEX.LIST");
//        JarUtil.createIndex(dest, getLog());
        

        if (packEnabled) {
            logger.debug(" - - repack : " + dest);
            JarUtil.repack(dest, packOptions, logger);
        }

        getLog().debug(" - - sign  : " + dest);
        signer.sign(dest, dest);
        back.jar = dest;
        
        //signer.verify(out);
        if (packEnabled) {
            logger.debug(" - - pack :" + dest);
            File tmp3 = new File(outputDir, dest.getName()+"-tmp3.jar");
            try {
                File packed = JarUtil.pack(dest, packOptions, logger);
                if (packVerifySignature) {
                    getLog().debug(" - - verify packed");
                    JarUtil.unpack(dest, tmp3, logger);
                    JarUtil.verifySignature(tmp3, logger);
                }
                back.packed = packed;
//                    long sizeBefore = dest.length();
//                    JarUtil.rejar(dest, dest, archiverManager, true, false);
//                    System.out.println("size before rejar : "+ sizeBefore + " - "+ dest.length() + " = " + (sizeBefore - dest.length()));
            } finally {
                tmp3.delete();
            }
        }
        logger.debug("end generation of " + dest);
        return back;
    }
}
