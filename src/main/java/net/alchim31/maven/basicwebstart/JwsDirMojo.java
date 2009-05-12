package net.alchim31.maven.basicwebstart;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.DefaultArchiverManager;
import org.codehaus.plexus.util.FileUtils;

/**
 * generate jnlp (from template), rename, sign, pack jar
 *
 * @goal jwsdir
 * @phase package
 * @author david.bernard
 */
public class JwsDirMojo extends AbstractMojo {

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
     * Used to look up Artifacts in the remote repository.
     *
     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
     * @required
     * @readonly
     */
    protected ArtifactFactory factory;

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
     * To look up Archiver/UnArchiver implementations
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.manager.ArchiverManager}"
     * @required
     */
    protected ArchiverManager archiverManager;

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

            getLog().info("processTemplates");
            VelocityContext context = initTemplateContext();
            for(File file : inputDirectory.listFiles()) {
                if (file.getName().endsWith(".vm")) {
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
        context.put("dependencies", findAllDependencies());
        context.put("project", project);
        context.put("packEnabled", packEnabled);
        context.put("versionEnabled", versionEnabled);
        return context;
    }

    @SuppressWarnings("unchecked")
    private List<Artifact> findAllDependencies() throws Exception {
        LinkedList<Artifact> back = new LinkedList<Artifact>();
        LinkedList<?> remoteRepos = new LinkedList<Object>();
        List<Dependency> deps  = (List<Dependency>) project.getRuntimeDependencies();
        if (deps != null) {
            for (Dependency dep : deps) {
                Artifact artifact = factory.createArtifactWithClassifier(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), dep.getType(), dep.getClassifier());
                resolver.resolve(artifact, remoteRepos, localRepo);
                back.add(artifact);
            }
        }
        back.add(project.getArtifact());
        return back;
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
        File tmp1 = new File(outputDir, "tmp-1.jar");
        File tmp2 = new File(outputDir, "tmp-2.jar");
        File tmp3 = new File(outputDir, "tmp-3.jar");
        for(Artifact artifact : _jars) {
            if (artifact == null) {
                continue;
            }
            File in = artifact.getFile();
            File dest = new File(outputDir, findFilename(artifact, true));

            getLog().debug("unsign :" + in + " to " + tmp1);
            JarUtil.unsign(in, tmp1, archiverManager, false);
            in = tmp1;
            
            if (packEnabled) {
                getLog().debug("repack :" + in + " to " + tmp2);
                JarUtil.repack(tmp2, packOptions, getLog());
                in = tmp2;
            }
            
            getLog().debug("sign  : " + in + " to " + dest);
            signer.sign(in, dest);
            
            //signer.verify(out);
            
            if (packEnabled) {
                getLog().debug("pack :" + dest);
                JarUtil.pack(dest, packOptions, getLog());

                getLog().debug("verify packed");
                JarUtil.unpack(dest, tmp3, getLog());
                JarUtil.verifySignature(tmp3, getLog());
            }
            getLog().info("end generation of " + dest);
        }
        tmp1.delete();
        tmp2.delete();
        tmp3.delete();
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
}