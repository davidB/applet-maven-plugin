package net.alchim31.maven.basicwebstart;

import java.io.File;
import java.util.HashSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;

public class JarMerger {
    public static String GROUP_ID = "merge";
    private Artifact _result;
    private HashSet<Artifact> _jars = new HashSet<Artifact>();
    private Log _logger;
    
    public JarMerger(Artifact result, Log logger) {
        _result = result;
        _logger = logger;
        _result.setFile(null);
    }

    public void addJar(Artifact artifact) throws Exception {
        if (_result.getFile() == null) {
            _logger.debug("add artifact " + artifact + " into merger for "+ _result);
            _jars.add(artifact);
        } else {
            _logger.debug("already created => NO add artifact " + artifact + " into merger for "+ _result);
        }
    }

    public Artifact getMergedJar() throws Exception {
        if (_result.getFile() == null) {
            File mergedJar = File.createTempFile("jarmerger-", ".jar", JarUtil.createTempDir());

            File mergedJarDir = new File(mergedJar.getAbsolutePath() + ".dir");
            mergedJarDir.mkdirs();
            _logger.debug("create mergedDir : " + mergedJarDir);
            for (Artifact artifact : _jars) {
                JarUtil.unjar(artifact.getFile(), mergedJarDir);
            }
            new File(mergedJarDir, "META-INF/MANIFEST.MF").delete();
            new File(mergedJarDir, "META-INF/INDEX.LIST").delete();
            JarUtil.unsign(mergedJarDir);
            //TODO add a file that list every artifact merged ??
            JarUtil.jar(mergedJarDir, mergedJar, true, _logger);
            _result.setGroupId(GROUP_ID);
            _result.setFile(mergedJar);
        }
        return _result;
    }
}
