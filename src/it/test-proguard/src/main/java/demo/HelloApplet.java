package demo;

import java.awt.*;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

public class HelloApplet extends java.applet.Applet {
    public void init (){
        try {
            add(new Label("Hello World"));
            InputStream is = null;
            try {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream("hello.txt");
                add(new Label(IOUtils.toString(is)));
            } finally {
                IOUtils.closeQuietly(is);
            }
        } catch (Exception exc) {
            exc.printStackTrace();
        }
     }
}