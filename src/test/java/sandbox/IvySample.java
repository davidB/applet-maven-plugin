package sandbox;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;

// see http://www.sonatype.com/people/2008/10/mercury-externalized-dependencies/
// see http://docs.codehaus.org/display/MAVEN/HowTo+use+Mercury+for+accessing+repositories
public class IvySample {
    public static void main(String[] args) {
        try {
            //IvySettings settings = new IvySettings();
            Ivy ivy = Ivy.newInstance();
            //ivy.configure(new URL(&quot;ivysettings.xml&quot;));
            ResolveReport r = ivy.resolve(ModuleRevisionId.newInstance("com.mimesis-republic.blackmamba", "blackmamba-core", "0.7.5-SNAPSHOT"),new ResolveOptions(), false);
        } catch(Exception exc) {
            exc.printStackTrace();
        }
    }
}
