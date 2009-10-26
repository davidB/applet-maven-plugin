package net.alchim31.maven.applet;

import java.io.File;

import proguard.ClassPath;
import proguard.ClassPathEntry;
import proguard.Configuration;
import proguard.ConfigurationParser;
import proguard.ProGuard;

class ProguardHelper {
    public static File run(File conf, File outJar) throws Exception {
        // create ProGuard configuration
        final Configuration configuration = new Configuration();
        final ConfigurationParser parser = new ConfigurationParser(conf);
        //getLog().info("parsing configuration...");
        try {
            parser.parse(configuration);
        } finally {
            parser.close();
        }
        // override output
        ClassPath jars = configuration.programJars;
        for(int i = jars.size() - 1; i > -1; i--) {
            ClassPathEntry entry = jars.get(i);
            if (entry.isOutput()) {
                jars.remove(i);
            }
        }
        
        //nothing to do if injars and conffile are less recent that outjar (useless conf is always younger)
//        boolean shouldRun = outJar.canRead();
//        shouldRun = shouldRun || outJar.lastModified() < conf.lastModified();
//        if (!shouldRun) {
//            for(int i = jars.size() - 1; i > -1; i--) {
//                ClassPathEntry entry = jars.get(i);
//                shouldRun = shouldRun || outJar.lastModified() < entry.getFile().lastModified();
//            }
//        }
//
//        if (shouldRun) {
            jars.add(new ClassPathEntry(outJar, true));
            new ProGuard(configuration).execute();
//        }
        return outJar;
    }
}
