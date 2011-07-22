/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.rackpackager;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyObjectAdapter;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.jruby.javasupport.JavaEmbedUtils.javaToRuby;

/**
 * @goal rack-package
 */
public class RackPackager
        extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The output directory of the assembled distribution file.
     *
     * @parameter default-value="${project.build.directory}"
     * @required
     */
    private File outputDirectory;

    public void execute()
            throws MojoExecutionException, MojoFailureException
    {
        URL gem2Jar = Resources.getResource("proofpoint/gemfile2jar.rb");

        checkNotNull(gem2Jar, "Couldn't find gemfile2jar.rb.  Please ensure the RackPackager plugin is properly built.");

        URL bundlerLib = Resources.getResource("bundler/lib");

        checkNotNull(bundlerLib, "Couldn't find a gem repo that contains bundler.  Please ensure the RackPackager plugin is properly built.");

        Ruby runtime = JavaEmbedUtils.initialize(ImmutableList.of(bundlerLib.getPath()), createRuntimeConfig());

        File gemfile;
        try {
            gemfile = new File(project.getBasedir().getCanonicalPath() + "/Gemfile");

            if (!gemfile.exists())
            {
                throw new MojoExecutionException("No Gemfile was found in the root of your project.  Please ensure a Gemfile exists, is readable, and is in the root of your project structure.");
            }
        }
        catch (IOException e) {
            throw new MojoExecutionException("Error finding Gemfile.  Please ensure a Gemfile exists, is readable, and is in the root of your project structure.");
        }

        try {
            InputStream gem2JarStream = gem2Jar.openStream();
            runtime.loadFile("gemfile2jar", gem2JarStream, false);

            gem2JarStream.close();

            RubyObjectAdapter adapter = JavaEmbedUtils.newObjectAdapter();

            IRubyObject gemfile2jar = runtime.evalScriptlet("Proofpoint::GemToJarPackager::Gemfile2JDir.new");

            File gemrepoDirectory = Files.createTempDir();

            IRubyObject response = adapter.callMethod(gemfile2jar, "run",
                    new IRubyObject[]{
                            javaToRuby(runtime, gemrepoDirectory.getCanonicalPath()),
                            javaToRuby(runtime, gemfile.getCanonicalPath())
                    });

            generateJarFile(gemrepoDirectory);

            if(!response.isTrue())
            {
                throw new MojoExecutionException("Gem repo jar was not properly constructed.  Please check the above output for errors.  " +
                        "Try running bundle install manually to verify the contents of the Gemfile.");
            }
        }
        catch (IOException e) {
            throw new MojoExecutionException("Error running gemfile2jar ruby module.  Please ensure the RackPackager plugin is properly built.");
        }
        finally {
            if (runtime != null)
            {
                JavaEmbedUtils.terminate(runtime);
            }
        }
    }

    private void generateJarFile(File gemrepoDirectory)
            throws IOException
    {
        File gemrepoJarFile = new File(String.format("%s/%s-%s-gemrepo.jar", outputDirectory.getCanonicalPath(), project.getName(), project.getVersion()));

        JarOutputStream gemrepoJarOutputStream = new JarOutputStream(new FileOutputStream(gemrepoJarFile), new Manifest());
        gemrepoJarOutputStream.close();

        addFilesToJarRecursivelyIgnoringSymlinks(gemrepoJarFile, gemrepoDirectory, "");
    }

    private void addFilesToJarRecursivelyIgnoringSymlinks(File jarFile, File directory, String path)
    {
        for(File child : directory.listFiles())
        {
            if (!isSymbolicLink(child)) {
                if(child.isDirectory()) {
                    addFilesToJarRecursivelyIgnoringSymlinks(jarFile, child, path + child.getName() + "/");
                } else
                {
                    JarEntry jarEntry = new

                }
        }
    }

    private boolean isSymbolicLink(File file)
    {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                    // or the canonical parent path is not the same as the files parent path
                    !canonicalFile.getParent().equals(file.getParentFile().getCanonicalPath());
        }
        catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    private RubyInstanceConfig createRuntimeConfig()
    {
        RubyInstanceConfig config = new RubyInstanceConfig();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        config.setClassCache(JavaEmbedUtils.createClassCache(classLoader));

        URL resource = RubyInstanceConfig.class.getResource("/META-INF/jruby.home");
        if (resource != null && resource.getProtocol().equals("jar")) {
            try { // http://weblogs.java.net/blog/2007/04/25/how-convert-javaneturl-javaiofile
                config.setJRubyHome(resource.toURI().getSchemeSpecificPart());
            }
            catch (URISyntaxException e) {
                config.setJRubyHome(resource.getPath());
            }
        }

        return config;
    }

    private void checkNotNull(Object argument, String message)
            throws MojoExecutionException
    {
        if (argument == null)
        {
            throw new MojoExecutionException(message);
        }
    }

    public void setProject(MavenProject project)
    {
        this.project = project;
    }

    public void setOutputDirectory(File outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }
}
