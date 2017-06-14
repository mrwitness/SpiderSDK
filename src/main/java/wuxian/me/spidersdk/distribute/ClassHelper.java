package wuxian.me.spidersdk.distribute;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import wuxian.me.spidercommon.util.FileUtil;
import wuxian.me.spidersdk.JobManagerConfig;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by wuxian on 12/5/2017.
 */
public class ClassHelper {

    private ClassHelper() {
    }

    public static Class getClassByName(String name) throws ClassNotFoundException {
        return Thread.currentThread().getContextClassLoader().loadClass(name);
    }

    public static Set<Class<?>> getClasses(String pack) throws IOException {
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        boolean recursive = true;
        String packageName = pack;
        String packageDirName = packageName.replace('.', '/');
        Enumeration<URL> dirs;
        dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
        while (dirs.hasMoreElements()) {
            URL url = dirs.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                findAndAddClassesInPackageByFile(packageName, filePath, recursive, classes);

            } else if ("jar".equals(protocol)) {

                JarFile jar;
                jar = ((JarURLConnection) url.openConnection()).getJarFile();
                Set<Class<?>> set = getJarFileClasses(jar, packageName);

                classes.addAll(set);
            }
        }

        return classes;
    }

    public static Set<Class<?>> getJarFileClasses(@NotNull JarFile jar) {
        return getJarFileClasses(jar, null);
    }

    public static Set<Class<?>> getJarFileClasses(JarFile jar, @Nullable String packageName, @Nullable CheckFilter filter) {
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        boolean recursive = true;
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.charAt(0) == '/') {
                name = name.substring(1);
            }

            if (packageName != null) {
                String packageDirName = packageName.replace('.', '/');
                if (name.startsWith(packageDirName)) {
                    int idx = name.lastIndexOf('/');
                    if (idx != -1) {
                        packageName = name.substring(0, idx)
                                .replace('/', '.');
                    }
                    if ((idx != -1) || recursive) {
                        if (name.endsWith(".class") && !entry.isDirectory()) {

                            String className = name.substring(packageName.length() + 1, name.length() - 6);
                            try {
                                classes.add(Class.forName(packageName + '.' + className));
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } else {
                if (filter != null && !filter.apply(name)) {
                    break;
                }
                int idx = name.lastIndexOf('/');
                if (idx != -1 && name.endsWith(".class") && !entry.isDirectory()) {
                    String packageName1 = name.substring(0, idx).replace('/', '.');
                    String className = name.substring(packageName1.length() + 1, name.length() - 6);
                    try {
                        classes.add(Class.forName(packageName1 + '.' + className));

                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return classes;
    }

    public static Set<Class<?>> getJarFileClasses(JarFile jar, @Nullable String packageName) {
        return getJarFileClasses(jar, packageName, null);
    }

    public static void findAndAddClassesInPackageByFile(String packageName, String packagePath,
                                                        final boolean recursive, Set<Class<?>> classes) {
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] dirfiles = dir.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return (recursive && file.isDirectory())
                        || (file.getName().endsWith(".class"));
            }
        });
        for (File file : dirfiles) {

            if (file.isDirectory()) {
                findAndAddClassesInPackageByFile(packageName + "." + file.getName(),
                        file.getAbsolutePath(), recursive, classes);
            } else {
                String className = file.getName().substring(0,
                        file.getName().length() - 6);
                try {
                    classes.add(Thread.currentThread().getContextClassLoader().
                            loadClass(packageName + '.' + className));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static Set<Class<?>> getSpiderFromJar(CheckFilter checkFilter) {
        Set<Class<?>> classSet = null;
        if (JobManagerConfig.jarMode) {
            if (FileUtil.currentFile != null) {
                try {
                    JarFile jar = new JarFile(FileUtil.currentFile);
                    classSet = ClassHelper.getJarFileClasses(jar, null, checkFilter);

                } catch (IOException e) {

                }
            } else {
                try {
                    //取当前jar做检查
                    File file = new File(FileUtil.class.getProtectionDomain().
                            getCodeSource().getLocation().toURI().getPath());
                    JarFile jar = new JarFile(file);
                    classSet = ClassHelper.getJarFileClasses(jar, null, checkFilter);
                } catch (Exception e) {

                }
            }
        } else {
            try {           //Fixme:library模式下 这段代码不起作用,应该改成业务的包名
                classSet = ClassHelper.getClasses("wuxian.me.spidersdk");
            } catch (IOException e) {
                classSet = null;
            }
        }
        return classSet;
    }

    public static boolean isPackageStringValid(String packageString) {
        if (packageString == null || packageString.length() == 0) {
            return false;
        }
        String reg = "([0-9A-Za-z]+[.])+[0-9A-Za-z]+";
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(packageString);
        return matcher.matches();
    }

    public static Set<Class<?>> getSpiderFromPackage(String spiderScan) {
        String[] packages = spiderScan.split(";");
        if (packages == null || packages.length == 0) {
            if (!isPackageStringValid(spiderScan)) {
                return null;
            }
            try {
                return ClassHelper.getClasses(spiderScan);
            } catch (Exception e) {
                return null;
            }
        } else {
            Set<Class<?>> classSet = new HashSet<Class<?>>();
            for (int i = 0; i < packages.length; i++) {
                if (!isPackageStringValid(packages[i])) {
                    continue;
                }
                try {
                    Set<Class<?>> set = ClassHelper.getClasses(packages[i]);
                    classSet.addAll(set);
                } catch (IOException e) {
                    continue;
                }
            }
            return classSet;
        }
    }

    public interface CheckFilter {
        boolean apply(String name);
    }
}
