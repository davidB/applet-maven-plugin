/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.alchim31.maven.basicwebstart;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 *
 * @author dwayne
 */
public class JarUtil {
    private final static List<String> EXT_ARRAY = Arrays.asList(new String[]{ "DSA", "RSA", "SF" });
    private final static FileFilter _removeSignatureFileFilter = new FileFilter() {
        public boolean accept(File file) {
            String extension = FileUtils.getExtension(file.getAbsolutePath());
            return (EXT_ARRAY.contains(extension));
        }
    };

    private static File _tmpRootDir = null;

    // TODO check that tempDir is removed on shutdown
    public static File createTempDir() throws Exception {
        if (_tmpRootDir == null) {
            _tmpRootDir = new File(System.getProperty("java.io.tmpdir"), "jarutil.tmp");
            Runtime.getRuntime().addShutdownHook(new Thread(){
                @Override
                public void run() {
                    super.run();
                    try {
                        if (_tmpRootDir.exists()) {
                            FileUtils.deleteDirectory(_tmpRootDir);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        File dir = new File(_tmpRootDir, String.valueOf(System.currentTimeMillis()));
        dir.mkdirs();
        return dir;
    }

    public static File unjar(File jarFile, ArchiverManager archiverManager) throws Exception {
        File tempDirParent = createTempDir();

        String archiveExt = FileUtils.getExtension( jarFile.getAbsolutePath() ).toLowerCase();

        // create temp dir
        File tempDir = new File( tempDirParent, jarFile.getName() );

        if (!tempDir.mkdir()) {
            throw new IOException( "Error creating temporary directory: " + tempDir );
        }
        // FIXME we probably want to be more security conservative here.
        // it's very easy to guess where the directory will be and possible
        // to access/change its contents before the file is rejared..

        // extract jar into temporary directory
        UnArchiver unArchiver = archiverManager.getUnArchiver( archiveExt );
        unArchiver.setSourceFile( jarFile );
        unArchiver.setDestDirectory( tempDir );
        unArchiver.extract();
        return tempDir;
    }
    
    public static void unsign(File jarIn, File jarOut, ArchiverManager archiverManager, boolean compress) throws Exception {
        File explodedJarDir = unjar(jarIn, archiverManager);
        unsign(explodedJarDir);
        jar(explodedJarDir, jarOut, archiverManager, compress);
    }
    
    public static void unsign(File explodedJarDir) throws Exception {
        // create and check META-INF directory
        File metaInf = new File( explodedJarDir, "META-INF" );
        if ( !metaInf.isDirectory() ) {
//            verboseLog( "META-INT dir not found : nothing to do for file: " + jarFile.getAbsolutePath() );
            return;
        }

        // filter signature files and remove them
        File[] filesToRemove = metaInf.listFiles(_removeSignatureFileFilter);
        if ( filesToRemove.length == 0 ) {
//            verboseLog( "no files match " + toString(EXT_ARRAY) + " : nothing to do for file: " + jarFile.getAbsolutePath() );
            return;
        }
        for ( int i = 0; i < filesToRemove.length; i++ ) {
            if ( !filesToRemove[i].delete() ) {
                throw new IOException( "Error removing signature file: " + filesToRemove[i] );
            }
//            verboseLog("remove file :" + filesToRemove[i]);
        }
    }

    public static void jar(File explodedJarDir, File jarFile, ArchiverManager archiverManager, boolean compress) throws Exception {
        JarArchiver jarArchiver = (JarArchiver) archiverManager.getArchiver( "jar" );
        jarArchiver.setCompress( compress );
        jarArchiver.setUpdateMode( false );
        jarArchiver.addDirectory( explodedJarDir );
        jarArchiver.setDestFile( jarFile );
        File manifestFile = new File(explodedJarDir, "META-INF/MANIFEST.MF");
        if (manifestFile.exists()) {
            jarArchiver.setManifest(manifestFile);
        }
        //jnlp (1.6.0_u14) doesn't like empty jar (with nothing except META-INF)
        File[] children = explodedJarDir.listFiles();
        if ( children.length == 0 || (children.length == 1 && "META-INF".equals(children[0].getName().toUpperCase())) ) {
            FileUtils.fileWrite(new File(explodedJarDir, "__no_empty.txt").getAbsolutePath(), "fake : no empty file for jnlp");
        }
        jarArchiver.setIndex(true);
        jarArchiver.createArchive();
    }


    public static File pack(File jar, String[] options, final Log log) throws Exception {
//        Packer packer = Pack200.newPacker();

//    // Initialize the state by setting the desired properties
//    Map p = packer.properties();
//    // take more time choosing codings for better compression
//    p.put(Packer.EFFORT, "7");  // default is "5"
//    // use largest-possible archive segments (>10% better compression).
//    p.put(Packer.SEGMENT_LIMIT, "-1");
//    // reorder files for better compression.
//    p.put(Packer.KEEP_FILE_ORDER, Packer.FALSE);
//    // smear modification times to a single value.
//    p.put(Packer.MODIFICATION_TIME, Packer.LATEST);
//    // ignore all JAR deflation requests,
//    // transmitting a single request to use "store" mode.
//    p.put(Packer.DEFLATE_HINT, Packer.FALSE);
//    // discard debug attributes
//    p.put(Packer.CODE_ATTRIBUTE_PFX+"LineNumberTable", Packer.STRIP);
//    // throw an error if an attribute is unrecognized
//    p.put(Packer.UNKNOWN_ATTRIBUTE, Packer.ERROR);
//    // pass one class file uncompressed:
//    p.put(Packer.PASS_FILE_PFX+0, "mutants/Rogue.class");
        File back = new File(jar.getAbsolutePath() + ".pack.gz");
//        JarFile in = null;
//        OutputStream out = null;
//        try {
//            in = new JarFile(jar);
//            out = new GZIPOutputStream(new FileOutputStream(back));
//            packer.pack(in, out);
//            return back;
//        } finally {
//            //IOUtil.close(in);
//            IOUtil.close(out);
//        }
        Commandline commandLine = new Commandline();
        commandLine.setExecutable(findJavaExec("pack200"));
        if (options != null && options.length > 0) {
            commandLine.addArguments(options);
        }
        commandLine.addArguments(new String[]{back.getAbsolutePath(), jar.getAbsolutePath()});
        exec(commandLine, log);
        return back;
    }

    public static void repack(File jar, String[] options, final Log log) throws Exception {
        Commandline commandLine = new Commandline();
        commandLine.setExecutable(findJavaExec("pack200"));
        commandLine.addArguments(options);
        commandLine.addArguments(new String[]{"--repack", jar.getAbsolutePath()});
        exec(commandLine, log);
    }

    public static void unpack(File pack, File jar, Log log) throws Exception {
        Commandline commandLine = new Commandline();
        commandLine.setExecutable(findJavaExec("unpack200"));
        commandLine.addArguments(new String[]{pack.getAbsolutePath(), jar.getAbsolutePath()});
        exec(commandLine, log);
    }

    //TODO throws an exception or return a boolean is the verification failed
    public static void verifySignature(File jar, Log log) throws Exception {
        Commandline commandLine = new Commandline();
        commandLine.setExecutable( findJavaExec("jarsigner") );
        commandLine.addArguments(new String[]{"-verify", jar.getAbsolutePath()});
        exec(commandLine, log);
    }
    
    private static String findJavaExec(String name) throws Exception {
        File jhome = new File(System.getProperty("java.home"));
        File f = findJavaExec(jhome, name);
        if (!f.exists() && jhome.getName().contains("jre")) {
            f = new File(jhome.getParentFile(), "bin/" + name);
        }
        return f.getCanonicalPath();
    }
    
    private static File findJavaExec(File jhome, String name) throws Exception {
        File f = new File(jhome, "bin/" + name);
        if (!f.exists()) {
            f = new File(jhome, "bin/" + name + ".exe");
        }
        return f;
    }

    private static void exec(Commandline commandLine, final Log log) throws Exception {
        StreamConsumer stdout = new StreamConsumer() {
            public void consumeLine( String line ) {
                log.info( line );
            }
        };
        StreamConsumer sterr = new StreamConsumer() {
            public void consumeLine( String line ) {
                log.info( line );
            }
        };
        File exec = new File(commandLine.getExecutable());
        if (!exec.exists()) {
            String msg = "exec not found : " + exec;
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.debug(commandLine.toString());
        int pid = CommandLineUtils.executeCommandLine(commandLine, stdout, sterr);
        while(CommandLineUtils.isAlive(pid)) {
            Thread.sleep(1000);
        }
    }
}
