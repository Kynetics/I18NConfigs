package com.kynetics.i18ncfg;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by andrex on 10/11/16.
 */
public class ConfigBuilder {

    public static final String ROOT_DIR_CONFIG_KEY = "rootDir";

    public static final String PROFILE_PATH_CONFIG_KEY = "profilePath";

    private ConfigBuilder(){}

    public static ConfigBuilder create() {
        return new ConfigBuilder();
    }

    public ConfigBuilder withRootDir(File dir) {
        requireArgument(dir.isDirectory(), "RootDir must be a directory");
        this.rootDir = dir;
        checkState();
        return this;
    }

    public ConfigBuilder withProfileDir(File dir) {
        requireArgument(dir.isDirectory(), "ProfileDir must be a directory");
        this.profileDir = dir;
        checkState();
        return this;
    }

    public ConfigBuilder withConfigFile(File file) {
        requireArgument(file.isFile(), "ConfigFile must be a file");
        this.configFile = file;
        checkState();
        return this;
    }

    public ConfigBuilder withLocale(Locale locale) {
        this.locale = locale;
        return this;
    }

    public Config build() {
        return buildConfig(buildRootConfig(), buildFileSequencesMap());
    }

    static boolean hasParent(File child, File parent) {
        return child != null && (child.equals(parent) || hasParent(child.getParentFile(), parent));
    }

    static List<File> fileHierarchy(File child, File parent) {
        try {
            final List<File> l = new LinkedList<>();
            for(File f = child; !f.getCanonicalFile().equals(parent.getCanonicalFile()); f=f.getParentFile()){
                l.add(f);
            }
            l.add(parent);
            return l;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static void requireArgument(boolean test, String msg) {
        if(!test) throw new IllegalArgumentException(msg);
    }

    private static void requireState(boolean test, String msg) {
        if(!test) throw new IllegalStateException(msg);
    }

    private void checkDirs() {
        requireState(rootDir == null || profileDir == null || hasParent(profileDir, rootDir), "ProfileDir must be a RootDir subdirectory");
    }

    private void checkDirsVersusFile() {
        requireState(configFile == null || (rootDir == null && profileDir == null), "ConfigFile must be an alternative to RootDir and ProfileDir");
    }

    private void checkState() {
        this.checkDirs();
        this.checkDirsVersusFile();
    }

    private FileSequences getFileSequences(String fileName, Map<String, FileSequences> fileSequencesMap) {
        if(!fileSequencesMap.containsKey(fileName)) {
            fileSequencesMap.put(fileName,new FileSequences());
        }
        return fileSequencesMap.get(fileName);
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

    private static String stripFiletypeSuffix(String fileName) {
        final int dotIndex = fileName.lastIndexOf('.');
        if(dotIndex > -1) return fileName.substring(0, dotIndex);
        else return fileName;
    }

    private static String stripSuffix(String fileName, String suffix) {
        return fileName.substring(0,fileName.length()-suffix.length());
    }

    private static boolean isLocalizedFile(String fileName) {
        return fileName.matches(".*_[a-z]{2}(_[A-Z]{2})?");
    }

    private Map<String, FileSequences> buildFileSequencesMap() {
        final Map<String, FileSequences> map = new HashMap<>();
        final Locale defaultLocale = Locale.getDefault();
        final boolean hasLocale = this.locale != null && !this.locale.equals(defaultLocale);
        for(File currentDir : fileHierarchy(this.profileDir, this.rootDir)) {
            List<File> files = Arrays.asList(currentDir.listFiles(fileFilter));
            Collections.sort(files);
            for(File currentFile : files) {
                final String fileName = stripFiletypeSuffix(currentFile.getName());
                if(hasLocale) {
                    if(!this.locale.getCountry().isEmpty()){
                        final String suffix = String.format("_%s_%s", locale.getLanguage(), locale.getCountry());
                        if(fileName.endsWith(suffix)){
                            getFileSequences(stripSuffix(fileName, suffix), map).currentLocaleSequence_2.add(currentFile);
                        }
                    }
                    final String suffix = String.format("_%s", locale.getLanguage());
                    if(fileName.endsWith(suffix)){
                        getFileSequences(stripSuffix(fileName, suffix), map).currentLocaleSequence_1.add(currentFile);
                    }
                }
                if(!defaultLocale.getCountry().isEmpty()){
                    final String suffix = String.format("_%s_%s", defaultLocale.getLanguage(), defaultLocale.getCountry());
                    if(fileName.endsWith(suffix)){
                        getFileSequences(stripSuffix(fileName, suffix), map).defaultLocaleSequence_2.add(currentFile);
                    }
                }
                final String suffix = String.format("_%s", defaultLocale.getLanguage());
                if(fileName.endsWith(suffix)){
                    getFileSequences(stripSuffix(fileName, suffix), map).defaultLocaleSequence_1.add(currentFile);
                }
                if(!isLocalizedFile(fileName)) getFileSequences(fileName, map).defaultSequence.add(currentFile);
            }
        }
        return map;
    }

    private Config buildRootConfig() {
        if(this.configFile == null) {
            return ConfigFactory.empty();
        } else {
            final Config rootConfig = ConfigFactory.parseFile(this.configFile).resolve();
            final String rootDirUrlStr = rootConfig.getString(ROOT_DIR_CONFIG_KEY);
            final String profilePath = rootConfig.getString(PROFILE_PATH_CONFIG_KEY);
            final URI rootDirUri;
            try {
                rootDirUri = new URI(rootDirUrlStr);
            } catch (URISyntaxException use) {
                throw new IllegalStateException(use);
            }
            if(rootDirUri.isAbsolute()) {
                this.rootDir = new File(rootDirUri);
            } else {
                this.rootDir = new File(this.configFile.getParentFile(), rootDirUri.getPath());
            }
            this.profileDir = new File(this.rootDir, profilePath);
            this.checkDirs();
            return rootConfig;
        }
    }

    private final Config buildConfig(Config rootConfig, Map<String, FileSequences> fileSequencesMap) {
        final ConfigResolveOptions resolvingOptions =  ConfigResolveOptions.defaults().setAllowUnresolved(true);
        for(String key : fileSequencesMap.keySet()) {
            final FileSequences fileSequences = fileSequencesMap.get(key);
            Config cfg = ConfigFactory.empty();
            for(File f : fileSequences.currentLocaleSequence_2) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            for(File f : fileSequences.currentLocaleSequence_1) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            for(File f : fileSequences.defaultLocaleSequence_2) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            for(File f : fileSequences.defaultLocaleSequence_1) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            for(File f : fileSequences.defaultSequence) {
                cfg = cfg.withFallback(ConfigFactory.parseFile(f));
            }
            rootConfig = rootConfig.withValue(key, cfg.root());
        }
        rootConfig = rootConfig.resolve();
        return rootConfig;
    }

    private File rootDir = null;

    private File profileDir = null;

    private File configFile = null;

    private Locale locale = null;

}
