package org.evosuite.tom;

import org.evosuite.utils.LoggingUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class VersionsClassLoader extends ClassLoader{
    private ClassLoader classLoader;
    private Map<String, Class> classes;
    private HashMap<String, ByteArrayOutputStream> entriesStreamMap;

    public VersionsClassLoader(String path) {
        // TODO Auto-generated constructor stub
        classLoader = VersionsClassLoader.class.getClassLoader();
        classes = new HashMap<>();
        entriesStreamMap = new HashMap<String, ByteArrayOutputStream>();
        init(path);
    }

    private void init(String path){
        File jarsDir = new File(path);
        for(File jar: jarsDir.listFiles()){
            if(jar.getName().endsWith(".jar")&&!jar.getName().endsWith("tests.jar")){
                try {
                    InputStream in = new BufferedInputStream(new FileInputStream(jar));
                    JarInputStream jarInput = new JarInputStream(in);
                    JarEntry entry = jarInput.getNextJarEntry();
                    while (entry != null) {
                        copyInputStream(jarInput, entry.getName());
                        entry = jarInput.getNextJarEntry();
                    }
                }catch (Exception e) {
                }
            }
        }
    }

    private void copyInputStream(InputStream in, String entryName) throws IOException {
        if(!entriesStreamMap.containsKey(entryName)) {
            ByteArrayOutputStream _copy = new ByteArrayOutputStream();
            int read = 0;
            int chunk = 0;
            byte[] data = new byte[256];
            while(-1 != (chunk = in.read(data)))
            {
                read += data.length;
                _copy.write(data, 0, chunk);
            }
            entriesStreamMap.put(entryName, _copy);
        }
    }

    private InputStream getCopy(String entryName) {
        System.out.println(entryName);
        ByteArrayOutputStream _copy = entriesStreamMap.get(entryName);
        if(_copy!=null)
            return (InputStream)new ByteArrayInputStream(_copy.toByteArray());
        else
            return null;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // TODO Auto-generated method stub
            Class<?> loadedClass = classes.get(name);
            if (loadedClass != null) {
                return loadedClass;
            }

            InputStream in = getCopy(name.replace(".",File.separator)+".class");
            if(in==null){
                Class<?> newClass = classLoader.loadClass(name);
                classes.put(name, newClass);
                return newClass;
            } else {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int len = 0;
                try {
                    while ((len = in.read()) != -1) {
                        bos.write(len);
                    }
                    byte[] data = bos.toByteArray();
                    in.close();
                    bos.close();
                    Class<?> newClass = defineClass(name, data, 0, data.length);
                    classes.put(name, newClass);
                    return newClass;
                } catch (IOException e) {
                    e.printStackTrace();
                    Class<?> newClass = classLoader.loadClass(name);
                    classes.put(name, newClass);
                    return newClass;
                }

            }
        }
    }
}
