/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.alchim31.maven.basicwebstart;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import java.util.jar.JarFile;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;
import java.util.zip.GZIPOutputStream;
import org.codehaus.plexus.util.IOUtil;

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
            _tmpRootDir = new File(System.getProperty("java.io.tmp"), "jarutil.tmp");
            Runtime.getRuntime().addShutdownHook(new Thread(){
                @Override
                public void run() {
                    super.run();
                    try {
                        FileUtils.deleteDirectory(_tmpRootDir);
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
        jarArchiver.setCompress( true );
        jarArchiver.setUpdateMode( false );
        jarArchiver.addDirectory( explodedJarDir );
        jarArchiver.setDestFile( jarFile );
        jarArchiver.createArchive();
    }


    public static File pack(File jar) throws Exception {
        Packer packer = Pack200.newPacker();

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
        JarFile in = null;
        OutputStream out = null;
        try {
            in = new JarFile(jar);
            out = new GZIPOutputStream(new FileOutputStream(back));
            packer.pack(in, out);
            return back;
        } finally {
            //IOUtil.close(in);
            IOUtil.close(out);
        }
    }

}
