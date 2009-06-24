/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.alchim31.maven.basicwebstart;

import java.io.File;
import java.io.FileNotFoundException;
import org.apache.maven.plugin.jar.JarSignMojo;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.mojo.keytool.GenkeyMojo;
import org.codehaus.plexus.util.FileUtils;

/**
 *
 * @author dwayne
 */
public class JarSigner {
    ////////////////////////////////////////////////////////////////////////////
    // Class
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a fully configured version of a Mojo ready to sign jars. You will
     * need to attach set the MavenProject is you don't sign in place.
     *
     * @return
     */
    private static JarSignMojo newJarSignerMojo(SignConfig cfg, File workDirectory, Log log, boolean verbose) throws Exception {
        createKeyStore(cfg, workDirectory, log, verbose);

        JarSignMojo signJar = new JarSignMojo();

        signJar.setAlias(cfg.alias);
        signJar.setKeypass(cfg.keypass);
        signJar.setKeystore(cfg.keystore.getAbsolutePath());
        signJar.setSkipAttachSignedArtifact(true);
        signJar.setSigFile(cfg.sigfile);
        signJar.setStorepass(cfg.storepass);
        signJar.setType(cfg.storetype);
        signJar.setVerify(cfg.verify);
        signJar.setWorkingDir(workDirectory);
        signJar.setVerbose(verbose);
        signJar.setLog(log);
        return signJar;
    }

    /**
     * generate Keystore if cfg.generateKeystore == true
     */
    private static void createKeyStore(SignConfig cfg, File workDirectory, Log log, boolean verbose) throws Exception {
        if (cfg.keystore == null || cfg.generateKeystore) {
            if (cfg.keystore == null) {
                cfg.keystore = new File(workDirectory, "tmp.jks");
            }
            if (cfg.keystore.exists()) {
                FileUtils.forceDelete(cfg.keystore);
            }
            GenkeyMojo genKeystore = new GenkeyMojo();
            genKeystore.setAlias(cfg.alias);
            genKeystore.setDname(cfg.getDname());
            genKeystore.setKeyalg(cfg.keyalg);
            genKeystore.setKeypass(cfg.keypass);
            genKeystore.setKeysize(cfg.keysize);
            genKeystore.setKeystore(cfg.keystore.getAbsolutePath());
            genKeystore.setSigalg(cfg.sigalg);
            genKeystore.setStorepass(cfg.storepass);
            genKeystore.setStoretype(cfg.storetype);
            genKeystore.setValidity(cfg.validity);
            genKeystore.setVerbose(verbose);
            genKeystore.setWorkingDir(workDirectory);
            genKeystore.setLog(log);
            genKeystore.execute();

            cfg.generateKeystore = false;
        }
        if ((cfg.keystore != null) && !cfg.keystore.exists()){
            throw new FileNotFoundException("keystore file '" + cfg.keystore + "'");
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Object
    ////////////////////////////////////////////////////////////////////////////

    private JarSignMojo _mojo = null;

    public JarSigner(SignConfig cfg, File workDirectory, Log log, boolean verbose) throws Exception {
        if (cfg != null) {
            _mojo = newJarSignerMojo(cfg, workDirectory, log, verbose);
        }
    }

    /**
     * if SignConfig used to create JarSigner was null,
     * then only copy jarIn to jarOut,
     * else sign the jarIn and produce jarOut.
     * No thread-safe.
     *
     * @param jarIn jar to sign
     * @param jarOut signed jar
     * @throws java.lang.Exception
     */
    public void sign(File jarIn, File jarOut) throws Exception {
        if (_mojo != null) {
            _mojo.setJarPath(jarIn);
            if (!jarIn.getCanonicalPath().equals(jarOut.getCanonicalPath())) {
                _mojo.setSignedJar(jarOut);
            }
            _mojo.execute();
        } else if (!jarIn.getCanonicalPath().equals(jarOut.getCanonicalPath())) {
            FileUtils.copyFile(jarIn, jarOut);
        }
    }
}
