package net_alchim31_maven_applet;

import java.io.File;
import java.util.HashSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;

public class JarMerger {
    public static String GROUP_ID = "merge";
    private Artifact _result;
    private HashSet<Artifact> _jars = new HashSet<Artifact>();
    private Log _logger;
    private JarUtil _ju;
    private boolean _resultAsDir;

    public JarMerger(Artifact result, JarUtil ju, Log logger, boolean resultAsDir) {
        _result = result;
        _logger = logger;
        _result.setFile(null);
        _ju = ju;
        _resultAsDir = resultAsDir;
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
            File mergedJar = File.createTempFile("jarmerger-", ".jar", _ju.getTempDir());

            File mergedJarDir = new File(mergedJar.getAbsolutePath() + ".dir");
            mergedJarDir.mkdirs();
            _logger.debug("create mergedDir : " + mergedJarDir);
            for (Artifact artifact : _jars) {
                _ju.unjar(artifact.getFile(), mergedJarDir);
            }
            new File(mergedJarDir, "META-INF/MANIFEST.MF").delete();
            new File(mergedJarDir, "META-INF/INDEX.LIST").delete();
            JarUtil.unsign(mergedJarDir);
            //TODO add a file that list every artifact merged ??
            _result.setGroupId(GROUP_ID);
            if (_resultAsDir) {
                _result.setFile(mergedJarDir);
            } else {
                _ju.jar(mergedJarDir, mergedJar, true);
                _result.setFile(mergedJar);
            }
        }
        return _result;
    }
}
