package net_alchim31_maven_applet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import org.codehaus.plexus.util.IOUtil;

import SevenZip.LzmaAlone;

class CompressionHelper {

    public static File lzma(File src, File dest, String[] options) throws Exception {
        ArrayList<String> args = new ArrayList<String>();
        args.add("e"); //encode/compress file
        if (options != null && options.length > 0) {
            args.addAll(Arrays.asList(options));
        }
        args.add(src.getCanonicalPath());
        args.add(dest.getCanonicalPath());
        LzmaAlone.main(args.toArray(new String[args.size()]));
        return dest;
    }

    public static File gzip(File src, File dest) throws Exception {
        InputStream istream = null;
        OutputStream ostream = null;
        try {
            istream = new FileInputStream(src);
            ostream = new GZIPOutputStream(new FileOutputStream(dest));
            IOUtil.copy(istream, ostream);
            ostream.flush();
        } finally {
            IOUtil.close(ostream);
            IOUtil.close(istream);
        }
        return dest;
    }
}
