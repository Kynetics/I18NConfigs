package com.kynetics.i18ncfg;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;

import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by andrex on 03/11/16.
 */
public class ConfigMapBuilder {

    public static final String ROOT_DIR_CONFIG_KEY = "rootDir";
    public static final String PROFILE_PATH_CONFIG_KEY = "profilePath";

    public ConfigMapBuilder(File rootDir, File profileDir, Locale currentLocale) {
        this.init(rootDir, profileDir, currentLocale);
        this.rootConfig = ConfigFactory.empty();
    }

    public ConfigMapBuilder(File rootDir, File profileDir) {
        this(rootDir, profileDir, null);
    }

    public ConfigMapBuilder(File config) {
        require(config.isFile(), "config file must be a file");
        this.rootConfig = ConfigFactory.parseFile(config).resolve();

        final String rootDirUrlStr = rootConfig.getString(ROOT_DIR_CONFIG_KEY);
        final String profilePath = rootConfig.getString(PROFILE_PATH_CONFIG_KEY);
        try {
            final File rootDir = new File(new URI(rootDirUrlStr));
            final File profileDir = new File(rootDir, profilePath);
            this.init(rootDir, profileDir, null);
        } catch (URISyntaxException use) {
            throw new IllegalStateException(use);
        }
    }

    private void init(File rootDir, File profileDir, Locale currentLocale) {
        require(rootDir.isDirectory(), "rootDir must be a directory");
        require(profileDir.isDirectory(), "profileDir must be a directory");
        final List<File> path = new LinkedList<>();
        require(hasParent(profileDir, rootDir, path), "profileDir must be a rootDir subdirectory");
        if(defaultLocale.equals(currentLocale)) {
            this.currentLocale = null;
        } else {
            this.currentLocale = currentLocale;
        }
        this.hasCurrentLocale = currentLocale != null;
        this.path = Collections.unmodifiableList(path);
    }

    private void initFileSequencesMap() {
        for(File currentDir : path) {
            final List<File> files = Arrays.asList(currentDir.listFiles(fileFilter));
            for(File currentFile : files) {
                final String fileName = stripFiletypeSuffix(currentFile.getName());
                if(this.hasCurrentLocale) {
                    if(!this.currentLocale.getCountry().isEmpty()){
                        final String suffix = String.format("_%s_%s", currentLocale.getLanguage(), currentLocale.getCountry());
                        if(fileName.endsWith(suffix)){
                            getFileSequences(stripSuffix(fileName, suffix)).currentLocaleSequence_2.add(currentFile);
                        }
                    }
                    final String suffix = String.format("_%s", currentLocale.getLanguage());
                    if(fileName.endsWith(suffix)){
                        getFileSequences(stripSuffix(fileName, suffix)).currentLocaleSequence_1.add(currentFile);
                    }
                }
                if(!this.defaultLocale.getCountry().isEmpty()){
                    final String suffix = String.format("_%s_%s", defaultLocale.getLanguage(), defaultLocale.getCountry());
                    if(fileName.endsWith(suffix)){
                        getFileSequences(stripSuffix(fileName, suffix)).defaultLocaleSequence_2.add(currentFile);
                    }
                }
                final String suffix = String.format("_%s", defaultLocale.getLanguage());
                if(fileName.endsWith(suffix)){
                    getFileSequences(stripSuffix(fileName, suffix)).defaultLocaleSequence_1.add(currentFile);
                }
                if(!fileName.matches(".*_[a-z]{2}(_[A-Z]{2})?")) getFileSequences(fileName).defaultSequence.add(currentFile);
            }
        }
    }

    public final Config build() {
        final ConfigResolveOptions resolvingOptions =  ConfigResolveOptions.defaults().setAllowUnresolved(true);
        this.initFileSequencesMap();
        for(String key : fileSequencesMap.keySet()) {
            final FileSequences fileSequences = fileSequencesMap.get(key);
            Config cfg = ConfigFactory.empty();
            for(File f : fileSequences.currentLocaleSequence_2) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            rootConfig = rootConfig.withValue(key, cfg.root());
            cfg = ConfigFactory.empty();
            for(File f : fileSequences.currentLocaleSequence_1) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            rootConfig = rootConfig.withValue(key, cfg.root());
            cfg = ConfigFactory.empty();
            for(File f : fileSequences.defaultLocaleSequence_2) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            rootConfig = rootConfig.withValue(key, cfg.root());
            cfg = ConfigFactory.empty();
            for(File f : fileSequences.defaultLocaleSequence_1) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            rootConfig = rootConfig.withValue(key, cfg.root());
            cfg = ConfigFactory.empty();
            for(File f : fileSequences.defaultSequence) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            rootConfig = rootConfig.withValue(key, cfg.root());
        }
        rootConfig = rootConfig.resolve();
        return rootConfig;
    }


    private static String stripFiletypeSuffix(String fileName) {
        final int dotIndex = fileName.lastIndexOf('.');
        if(dotIndex > -1) return fileName.substring(0, dotIndex);
        else return fileName;
    }

    private static String stripSuffix(String fileName, String suffix) {
        return fileName.substring(0,fileName.length()-suffix.length());
    }

    private static boolean hasParent(File childDir, File parentDir, List<File> path) {
        path.add(childDir);
        return childDir != null && (childDir.equals(parentDir) || hasParent(childDir.getParentFile(), parentDir, path));
    }

    private Locale currentLocale;

    private final Locale defaultLocale = Locale.getDefault();

    private boolean hasCurrentLocale;

    private List<File> path;

    private final Map<String, FileSequences> fileSequencesMap = new HashMap<>();

    private Config rootConfig;

    private FileSequences getFileSequences(String fileName) {
        if(!fileSequencesMap.containsKey(fileName)) {
            fileSequencesMap.put(fileName,new FileSequences());
        }
        return fileSequencesMap.get(fileName);
    }

    private static void require(boolean test, String msg) {
        if(!test) throw new IllegalArgumentException(msg);
    }



    private static class FileSequences {
        private final List<File> defaultSequence = new LinkedList<>();
        private final List<File> defaultLocaleSequence_1 = new LinkedList<>();
        private final List<File> defaultLocaleSequence_2 = new LinkedList<>();
        private final List<File> currentLocaleSequence_1 = new LinkedList<>();
        private final List<File> currentLocaleSequence_2 = new LinkedList<>();

        @Override
        public String toString() {
            return String.format("FileSequence : [%n" +
                            "defaultSequence: %s%n" +
                            "defaultLocaleSequence_1: %s%ndefaultLocaleSequence_2: %s%n" +
                            "currentLocaleSequence_1: %s%ncurrentLocaleSequence_2: %s%n]",
                    defaultSequence,
                    defaultLocaleSequence_1, defaultLocaleSequence_2,
                    currentLocaleSequence_1, currentLocaleSequence_2);
        }
    }

    private static final FileFilter fileFilter = new FileFilter() {

        private final List<String> supportedFiletypes = Arrays.asList(".properties", ".json", ".conf");

        @Override
        public boolean accept(File file) {
            if(!file.isFile()) return false;
            final String fname = file.getName();
            for(String suffix : supportedFiletypes) {
                if(fname.endsWith(suffix)) return true;
            }
            return false;
        }
    };
}
