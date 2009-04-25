package net.alchim31.maven.basicwebstart;

import java.io.File;

/**
 * Structure to define the sgnature maven configuration.
 *
 * @author davidB
 */
public class SignConfig {
    /**
     * Should the keystore file should be generated.
     */
    public boolean generateKeystore = false;

    /**
     */
    public File keystore;

    /**
     */
    public String keyalg;

    /**
     */
    public String keysize;

    /**
     */
    public String sigalg;

    /**
     */
    public String sigfile;

    /**
     */
    public String storetype;

    /**
     */
    public String storepass;

    /**
     */
    public String keypass;

    /**
     */
    public String validity;

    /**
     */
    public String dnameCn;

    /**
     */
    public String dnameOu;

    /**
     */
    public String dnameL;

    /**
     */
    public String dnameSt;

    /**
     */
    public String dnameO;

    /**
     */
    public String dnameC;

    /**
     */
    public String alias;

    /**
     * Whether we want to auto-verify the signed jars.
     */
    public boolean verify;

    public String getDname() {
        StringBuffer buffer = new StringBuffer(128);

        appendToDnameBuffer(dnameCn, buffer, "CN");
        appendToDnameBuffer(dnameOu, buffer, "OU");
        appendToDnameBuffer(dnameL, buffer, "L");
        appendToDnameBuffer(dnameSt, buffer, "ST");
        appendToDnameBuffer(dnameO, buffer, "O");
        appendToDnameBuffer(dnameC, buffer, "C");

        return buffer.toString();
    }

    private void appendToDnameBuffer(final String property, StringBuffer buffer, final String prefix) {
        if (property != null) {
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(prefix).append("=");
            buffer.append(property);
        }
    }


}
