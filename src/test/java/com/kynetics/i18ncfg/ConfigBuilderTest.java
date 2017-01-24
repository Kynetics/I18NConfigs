/**
 * Copyright (c) 2016 Kynetics, LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.kynetics.i18ncfg;

import com.typesafe.config.Config;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class ConfigBuilderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testHasParent() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File child_1 = tmp.newFolder("parent", "child_1");
        final File child_1_1 = tmp.newFolder("parent", "child_1", "child_1_1");
        final File child_1_1_1 = tmp.newFolder("parent", "child_1", "child_1_1", "child_1_1_1");
        final File child_2 = tmp.newFolder("parent", "child_2");
        Assert.assertTrue(ConfigBuilder.hasParent(child_1_1_1, child_1));
        Assert.assertTrue(ConfigBuilder.hasParent(child_1_1, child_1));
        Assert.assertTrue(ConfigBuilder.hasParent(child_1, child_1));
        Assert.assertFalse(ConfigBuilder.hasParent(child_1_1_1, child_2));
        Assert.assertFalse(ConfigBuilder.hasParent(child_1_1, child_2));
        Assert.assertFalse(ConfigBuilder.hasParent(child_1, child_2));
    }

    @Test
    public void testBuilderState() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File child_1 = tmp.newFolder("parent", "child_1");
        final File child_1_1 = tmp.newFolder("parent", "child_1", "child_1_1");
        final File child_1_1_1 = tmp.newFolder("parent", "child_1", "child_1_1", "child_1_1_1");
        final File child_2 = tmp.newFolder("parent", "child_2");
        final File config = new File(child_1, "config.conf");
        config.createNewFile();
        try {
            ConfigBuilder cb = ConfigBuilder.create().withRootDir(child_2);
            try {
                cb.withProfileDir(child_1_1);
            } catch (IllegalStateException ise) {
                Assert.assertEquals("ProfileDir must be a RootDir subdirectory", ise.getMessage());
            }
        } catch (IllegalStateException e) {
            Assert.fail("unexpected Exception: "+e.getMessage());
        }
        try {
            ConfigBuilder cb = ConfigBuilder.create().withProfileDir(child_1_1);
            try {
                cb.withRootDir(child_2);
            } catch (IllegalStateException ise) {
                Assert.assertEquals("ProfileDir must be a RootDir subdirectory", ise.getMessage());
            }
        } catch (IllegalStateException e) {
            Assert.fail("unexpected Exception: "+e.getMessage());
        }
        try {
            ConfigBuilder cb = ConfigBuilder.create().withRootDir(child_1);
            try {
                cb.withConfigFile(config);
            } catch (IllegalStateException ise) {
                Assert.assertEquals("ConfigFile must be an alternative to RootDir and ProfileDir", ise.getMessage());
            }
        } catch (IllegalStateException e) {
            Assert.fail("unexpected Exception: "+e.getMessage());
        }
        try {
            ConfigBuilder cb = ConfigBuilder.create().withProfileDir(child_1_1);
            try {
                cb.withConfigFile(config);
            } catch (IllegalStateException ise) {
                Assert.assertEquals("ConfigFile must be an alternative to RootDir and ProfileDir", ise.getMessage());
            }
        } catch (IllegalStateException e) {
            Assert.fail("unexpected Exception: "+e.getMessage());
        }
        try {
            ConfigBuilder cb = ConfigBuilder.create().withConfigFile(config);
            try {
                cb.withRootDir(child_1);
            } catch (IllegalStateException ise) {
                Assert.assertEquals("ConfigFile must be an alternative to RootDir and ProfileDir", ise.getMessage());
            }
        } catch (IllegalStateException e) {
            Assert.fail("unexpected Exception: "+e.getMessage());
        }
        try {
            ConfigBuilder cb = ConfigBuilder.create().withConfigFile(config);
            try {
                cb.withProfileDir(child_1_1);
            } catch (IllegalStateException ise) {
                Assert.assertEquals("ConfigFile must be an alternative to RootDir and ProfileDir", ise.getMessage());
            }
        } catch (IllegalStateException e) {
            Assert.fail("unexpected Exception: "+e.getMessage());
        }
        ConfigBuilder cb = ConfigBuilder.create().withConfigFile(config);
        ConfigBuilder cb1 = ConfigBuilder.create().withRootDir(child_1).withProfileDir(child_1_1);
        ConfigBuilder cb2 = ConfigBuilder.create().withProfileDir(child_1_1).withRootDir(child_1);
    }

    @Test
    public void testFileHierarchy() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File child_1 = tmp.newFolder("parent", "child_1");
        final File child_1_1 = tmp.newFolder("parent", "child_1", "child_1_1");
        Assert.assertEquals(Arrays.asList(child_1_1, child_1, parent), ConfigBuilder.fileHierarchy(child_1_1, parent));
        Assert.assertEquals(Arrays.asList(child_1, parent), ConfigBuilder.fileHierarchy(child_1, parent));
        Assert.assertEquals(Arrays.asList(parent), ConfigBuilder.fileHierarchy(parent, parent));
        Assert.assertEquals(Arrays.asList(parent), ConfigBuilder.fileHierarchy(new File(parent,"."), parent));
    }

    @Test
    public void testBuilding_with_only_a_root_file() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File rootConf = newTextFileIn(parent, "root.conf",
                "property1: property1 in root.conf file");
        final Config cfg = ConfigBuilder.create().withRootDir(parent).withProfileDir(parent).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property1 in root.conf file", cfg.getString("root.property1"));
        Assert.assertEquals(1, cfg.entrySet().size());
    }

    @Test
    public void testBuilding_with_a_root_file_overwritten() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File child_1 = tmp.newFolder("parent", "child_1");
        final File rootConf = newTextFileIn(parent, "root.conf",
                "property1: property1 in root.conf file\n"+
                "property2: property2 in root.conf file");
        final File childConf = newTextFileIn(child_1, "root.conf",
                "property1: property1 in root.conf file in child_1 directory");
        final Config cfg = ConfigBuilder.create().withRootDir(parent).withProfileDir(child_1).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property2 in root.conf file", cfg.getString("root.property2"));
        Assert.assertEquals("property1 in root.conf file in child_1 directory", cfg.getString("root.property1"));
        Assert.assertEquals(2, cfg.entrySet().size());
    }

    @Test
    public void testBuilding_merge_file_types() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File rootConf = newTextFileIn(parent, "root.conf",
                "property1: property1 in root.conf file");
        final File rootProp = newTextFileIn(parent, "root.properties",
                "property2 = property2 in root.properties file");
        final File rootJson = newTextFileIn(parent, "root.json",
                "{ \"property3\": \"property3 in root.json file\" }");
        final Config cfg = ConfigBuilder.create().withRootDir(parent).withProfileDir(parent).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property1 in root.conf file", cfg.getString("root.property1"));
        Assert.assertEquals("property2 in root.properties file", cfg.getString("root.property2"));
        Assert.assertEquals("property3 in root.json file", cfg.getString("root.property3"));
        Assert.assertEquals(3, cfg.entrySet().size());
    }

    @Test
    public void testBuilding_merge_file_types_order() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File rootConf = newTextFileIn(parent, "root.conf",
                "property1: property1 in root.conf file");
        final File rootJson = newTextFileIn(parent, "root.json",
                "{ \"property1\": \"property1 in root.json file\" , \"property2\": \"property2 in root.json file\" }");
        final File rootProp = newTextFileIn(parent, "root.properties",
                "property1 = property1 in root.properties file\n"+
                "property2 = property2 in root.properties file\n"+
                "property3 = property3 in root.properties file");
        final Config cfg = ConfigBuilder.create().withRootDir(parent).withProfileDir(parent).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property1 in root.conf file", cfg.getString("root.property1"));
        Assert.assertEquals("property2 in root.json file", cfg.getString("root.property2"));
        Assert.assertEquals("property3 in root.properties file", cfg.getString("root.property3"));
        Assert.assertEquals(3, cfg.entrySet().size());
    }

    @Test
    public void testBuilding_override_by_locale() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File rootConf = newTextFileIn(parent, "root.conf",
                "property1: property1 in root.conf file\n"+
                "property2: property2 in root.conf file\n"+
                "property3: property3 in root.conf file");
        final File rootConf_it = newTextFileIn(parent, "root_it.conf",
                "property2: property2 in root_it.conf file\n"+
                "property3: property3 in root_it.conf file");
        final File rootConf_it_IT = newTextFileIn(parent, "root_it_IT.conf",
                "property3: property3 in root_it_IT.conf file");
        final Config cfg = ConfigBuilder.create().withRootDir(parent).withProfileDir(parent).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property1 in root.conf file", cfg.getString("root.property1"));
        Assert.assertEquals("property2 in root_it.conf file", cfg.getString("root.property2"));
        Assert.assertEquals("property3 in root_it_IT.conf file", cfg.getString("root.property3"));
        Assert.assertEquals(3, cfg.entrySet().size());
    }

    @Test
    public void testBuilding_override_locale_dominate_on_profile() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File child_1 = tmp.newFolder("parent", "child_1");
        final File rootConf_it = newTextFileIn(parent, "root_it.conf",
                "property1: property1 in root_it.conf file");
        final File rootConf = newTextFileIn(child_1, "root.conf",
                "property1: property1 in root.conf file in child_1 dir\n"+
                "property2: property2 in root.conf file in child_1 dir");
        final Config cfg = ConfigBuilder.create().withRootDir(parent).withProfileDir(child_1).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property1 in root_it.conf file", cfg.getString("root.property1"));
        Assert.assertEquals("property2 in root.conf file in child_1 dir", cfg.getString("root.property2"));
        Assert.assertEquals(2, cfg.entrySet().size());
    }

    @Test
    public void testBuilding_with_config_absolute_path() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File child_1 = tmp.newFolder("parent", "child_1");
        final File config = newTextFileIn(parent, "config.conf",
                ConfigBuilder.ROOT_DIR_CONFIG_KEY+": \"file://"+parent.getAbsolutePath()+"\"\n"+
                ConfigBuilder.PROFILE_PATH_CONFIG_KEY+": child_1");
        final File rootConf = newTextFileIn(parent, "root.conf",
                "property1: property1 in root.conf");
        final File rootConf_1 = newTextFileIn(child_1, "root.conf",
                "property2: property2 in root.conf file in child_1 dir");
        final Config cfg = ConfigBuilder.create().withConfigFile(config).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property1 in root.conf", cfg.getString("root.property1"));
        Assert.assertEquals("property2 in root.conf file in child_1 dir", cfg.getString("root.property2"));
        Assert.assertEquals(6, cfg.entrySet().size());
    }

    @Test
    public void testBuilding_with_config_relative_path() throws IOException {
        final File parent = tmp.newFolder("parent");
        final File child_1 = tmp.newFolder("parent", "child_1");
        final File config = newTextFileIn(parent, "config.conf",
                ConfigBuilder.ROOT_DIR_CONFIG_KEY+": .\n"+
                ConfigBuilder.PROFILE_PATH_CONFIG_KEY+": child_1");
        final File rootConf = newTextFileIn(parent, "root.conf",
                "property1: property1 in root.conf");
        final File rootConf_1 = newTextFileIn(child_1, "root.conf",
                "property2: property2 in root.conf file in child_1 dir");
        final Config cfg = ConfigBuilder.create().withConfigFile(config).build();
        Assert.assertNotNull(cfg.getConfig("root"));
        Assert.assertEquals("property1 in root.conf", cfg.getString("root.property1"));
        Assert.assertEquals("property2 in root.conf file in child_1 dir", cfg.getString("root.property2"));
        Assert.assertEquals(6, cfg.entrySet().size());
    }

    private File newTextFileIn(File folder, String fileName, String text) throws IOException {
        final File file = new File(folder, fileName);
        try(FileWriter w = new FileWriter(file)) {
            w.write(text);
        }
        return file;
    }

}