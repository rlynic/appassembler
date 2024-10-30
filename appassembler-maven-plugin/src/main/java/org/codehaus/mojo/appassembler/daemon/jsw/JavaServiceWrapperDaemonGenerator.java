package org.codehaus.mojo.appassembler.daemon.jsw;

/*
 * The MIT License
 *
 * Copyright (c) 2006-2012, The Codehaus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.appassembler.daemon.DaemonGenerationRequest;
import org.codehaus.mojo.appassembler.daemon.DaemonGenerator;
import org.codehaus.mojo.appassembler.daemon.DaemonGeneratorException;
import org.codehaus.mojo.appassembler.model.Daemon;
import org.codehaus.mojo.appassembler.model.GeneratorConfiguration;
import org.codehaus.mojo.appassembler.util.DependencyFactory;
import org.codehaus.mojo.appassembler.util.FormattedProperties;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.InterpolationFilterReader;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 * @plexus.component role-hint="jsw"
 */
public class JavaServiceWrapperDaemonGenerator
    extends AbstractLogEnabled
    implements DaemonGenerator
{
    private static final Map<String, String> JSW_PLATFORMS_MAP = new HashMap<String, String>()
    {
        {
            put( "aix-ppc-32-lib", "libwrapper-aix-ppc-32.a" );
            put( "aix-ppc-32-exec", "wrapper-aix-ppc-32" );
            put( "aix-ppc-64-lib", "libwrapper-aix-ppc-64.a" );
            put( "aix-ppc-64-exec", "wrapper-aix-ppc-64" );
            put( "hpux-parisc-64-lib", "libwrapper-hpux-parisc-64.sl" );
            put( "hpux-parisc-64-exec", "wrapper-hpux-parisc-64" );
            put( "linux-x86-32-lib", "libwrapper-linux-x86-32.so" );
            put( "linux-x86-32-exec", "wrapper-linux-x86-32" );
            put( "linux-x86-64-lib", "libwrapper-linux-x86-64.so" );
            put( "linux-x86-64-exec", "wrapper-linux-x86-64" );
            put( "linux-aarch64-64-lib", "libwrapper-linux-aarch64-64.so" );
            put( "linux-aarch64-64-exec", "wrapper-linux-aarch64-64" );
            put( "linux-ppc-64-lib", "libwrapper-linux-ppc-64.so" );
            put( "linux-ppc-64-exec", "wrapper-linux-ppc-64" );
            put( "macosx-ppc-32-lib", "libwrapper-macosx-ppc-32.jnilib" );
            put( "macosx-ppc-32-exec", "wrapper-macosx-ppc-32" );
            put( "macosx-x86-universal-32-lib", "libwrapper-macosx-universal-32.jnilib" );
            put( "macosx-x86-universal-32-exec", "wrapper-macosx-universal-32" );
            put( "macosx-universal-32-lib", "libwrapper-macosx-universal-32.jnilib" );
            put( "macosx-universal-32-exec", "wrapper-macosx-universal-32" );
            put( "macosx-universal-64-lib", "libwrapper-macosx-universal-64.jnilib" );
            put( "macosx-universal-64-exec", "wrapper-macosx-universal-64" );
            put( "solaris-sparc-32-lib", "libwrapper-solaris-sparc-32.so" );
            put( "solaris-sparc-32-exec", "wrapper-solaris-sparc-32" );
            put( "solaris-sparc-64-lib", "libwrapper-solaris-sparc-64.so" );
            put( "solaris-sparc-64-exec", "wrapper-solaris-sparc-64" );
            put( "solaris-x86-32-lib", "libwrapper-solaris-x86-32.so" );
            put( "solaris-x86-32-exec", "wrapper-solaris-x86-32" );
            put( "solaris-x86-64-lib", "libwrapper-solaris-x86-64.so" );
            put( "solaris-x86-64-exec", "wrapper-solaris-x86-64" );
            put( "windows-x86-32-lib", "wrapper-windows-x86-32.dll" );
            put( "windows-x86-32-exec", "wrapper-windows-x86-32.exe" );
            put( "windows-x86-64-lib", "wrapper-windows-x86-64.dll" );
            put( "windows-x86-64-exec", "wrapper-windows-x86-64.exe" );
        }
    };

    /**
     * The directory where the JSW executable files are fetched from.
     */
    private static final String JSW_BIN_DIR = "bin";

    /**
     * The directory where the JSW lib files are fetched from.
     */
    private static final String JSW_LIB_DIR = "lib";

    // -----------------------------------------------------------------------
    // DaemonGenerator Implementation
    // -----------------------------------------------------------------------

    public void generate( DaemonGenerationRequest request )
        throws DaemonGeneratorException
    {
        Daemon daemon = request.getDaemon();

        File outputDirectory = new File( request.getOutputDirectory(), daemon.getId() );

        Properties configuration = createConfiguration( daemon );

        // Don't want these in the wrapper.conf file
        String appBaseEnvVar = configuration.getProperty( "app.base.envvar", "APP_BASE" );
        configuration.remove( "app.base.envvar" );
        String runAsUserEnvVar = configuration.getProperty( "run.as.user.envvar", "" );
        if ( !runAsUserEnvVar.equals( "" ) )
        {
            runAsUserEnvVar = "RUN_AS_USER=" + runAsUserEnvVar;
            configuration.remove( "run.as.user.envvar" );
        }
        String pidFile = configuration.getProperty( "wrapper.pidfile", "$BASEDIR/logs" );

        // Don't want these in the wrapper.conf file
        String chkconfigRun = configuration.getProperty( "chkconfig.run", "2345" ); // 2345 is the original run level,
                                                                                    // donot change this default value
        configuration.remove( "chkconfig.level" );
        String chkconfigStart = configuration.getProperty( "chkconfig.start", "20" );
        configuration.remove( "chkconfig.start" );
        String chkconfigStop = configuration.getProperty( "chkconfig.stop", "80" );
        configuration.remove( "chkconfig.stop" );

        Properties context = createContext( request, daemon );
        context.setProperty( "app.base.envvar", appBaseEnvVar );
        context.setProperty( "wrapper.pidfile", pidFile );
        context.setProperty( "run.as.user.envvar", runAsUserEnvVar );
        context.setProperty( "wrapper.conf.directory", daemon.getConfigurationDirectory() );
        context.setProperty( "wrapper.conf.fileName", this.getWrapperConfigFileName( daemon ) );
        context.setProperty( "chkconfig.run", chkconfigRun );
        context.setProperty( "chkconfig.start", chkconfigStart );
        context.setProperty( "chkconfig.stop", chkconfigStop );
        context.setProperty( "wrapper.exe.prefix", "wrapper" );
        if ( daemon.isUseDaemonIdAsWrapperExePrefixName() )
        {
            context.setProperty( "wrapper.exe.prefix", daemon.getId() );
        }

        writeWrapperConfFile( request, daemon, outputDirectory, context, configuration );

        writeScriptFiles( request, daemon, outputDirectory, context );

        List<String> jswPlatformIncludes = getJswPlatformIncludes( daemon );

        writeLibraryFiles( outputDirectory, daemon, jswPlatformIncludes );

        writeExecutableFiles( outputDirectory, daemon, jswPlatformIncludes );
    }

    private void writeWrapperConfFile( DaemonGenerationRequest request, Daemon daemon, File outputDirectory,
                                       Properties context, Properties configuration )
                                           throws DaemonGeneratorException
    {
        InputStream in = this.getClass().getResourceAsStream( "conf/wrapper.conf.in" );

        if ( in == null )
        {
            throw new DaemonGeneratorException( "Could not load template." );
        }

        FormattedProperties confFile = new FormattedProperties();

        try
        {
            confFile.read( in );
        }
        catch ( IOException e )
        {
            throw new DaemonGeneratorException( "Error reading template: " + e.getMessage(), e );
        }
        finally
        {
            IOUtil.close( in );
        }

        // TODO: configurable?
        confFile.setPropertyAfter( "wrapper.working.dir", "..", "wrapper.java.command" );
        confFile.setProperty( "wrapper.java.library.path.1", daemon.getRepositoryName() );
        confFile.setPropertyAfter( "set.default.REPO_DIR", daemon.getRepositoryName(), "wrapper.java.mainclass" );

        confFile.setPropertyAfter( "set." + context.getProperty( "app.base.envvar" ), ".", "wrapper.java.mainclass" );

        if ( daemon.getWrapperLogFile() == null )
        {
            // Write the default value to the wrapper.logfile.
            confFile.setProperty( "wrapper.logfile", "../logs/wrapper.log" );
        }
        else
        {
            confFile.setProperty( "wrapper.logfile", daemon.getWrapperLogFile() );
        }

        if ( daemon.getJvmSettings() != null && !StringUtils.isEmpty( daemon.getJvmSettings().getInitialMemorySize() ) )
        {
            confFile.setProperty( "wrapper.java.initmemory", daemon.getJvmSettings().getInitialMemorySize() );
        }

        if ( daemon.getJvmSettings() != null && !StringUtils.isEmpty( daemon.getJvmSettings().getMaxMemorySize() ) )
        {
            confFile.setProperty( "wrapper.java.maxmemory", daemon.getJvmSettings().getMaxMemorySize() );
        }

        confFile.setProperty( "wrapper.java.mainclass", daemon.getWrapperMainClass() );
        if ( !StringUtils.isEmpty( daemon.getMainClass() ) )
        {
            confFile.setProperty( "wrapper.app.parameter.1", daemon.getMainClass() );
        }

        createClasspath( daemon, request, confFile, configuration );
        createAdditional( daemon, confFile );
        createParameters( daemon, confFile );

        for ( Map.Entry entry : configuration.entrySet() )
        {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if ( value.length() > 0 )
            {
                confFile.setProperty( key, value );
            }
            else
            {
                confFile.removeProperty( key );
            }
        }

        ByteArrayOutputStream string = new ByteArrayOutputStream();
        confFile.save( string );

        Reader reader = new InputStreamReader( new ByteArrayInputStream( string.toByteArray() ) );

        File wrapperConf =
            new File( outputDirectory, daemon.getConfigurationDirectory() + "/" + getWrapperConfigFileName( daemon ) );
        writeFilteredFile( request, daemon, reader, wrapperConf, context );

        if ( daemon.getPreWrapperConf() != null )
        {
            insertPreWrapperConf( wrapperConf, new File( daemon.getPreWrapperConf() ) );
        }

    }

    private void insertPreWrapperConf( File wrapperConf, File preWrapperConf )
        throws DaemonGeneratorException
    {
        try
        {
            StringBuilder buffer = new StringBuilder();
            buffer.append( FileUtils.fileRead( preWrapperConf ) );
            buffer.append( "\n" );
            buffer.append( FileUtils.fileRead( wrapperConf ) );
            FileUtils.fileWrite( wrapperConf, buffer.toString() );
        }
        catch ( IOException e )
        {
            throw new DaemonGeneratorException( "Unable to merge pre wrapper config file." );
        }
    }

    private String getWrapperConfigFileName( Daemon daemon )
    {
        String wrapperConfigFileName = "wrapper.conf";
        if ( daemon.isUseDaemonIdAsWrapperConfName() )
        {
            wrapperConfigFileName = "wrapper-" + daemon.getId() + ".conf";
        }

        return wrapperConfigFileName;
    }

    private Properties createConfiguration( Daemon daemon )
    {
        Properties configuration = new Properties();

        for ( GeneratorConfiguration generatorConfiguration : daemon.getGeneratorConfigurations() )
        {
            if ( generatorConfiguration.getGenerator().equals( "jsw" ) )
            {
                configuration.putAll( generatorConfiguration.getConfiguration() );
            }
        }
        return configuration;
    }

    private static void writeFilteredFile( DaemonGenerationRequest request, Daemon daemon, Reader reader,
                                           File outputFile, Map context )
                                               throws DaemonGeneratorException
    {
        InterpolationFilterReader interpolationFilterReader =
            new InterpolationFilterReader( reader, context, "@", "@" );

        writeFile( outputFile, interpolationFilterReader );
    }

    private static Properties createContext( DaemonGenerationRequest request, Daemon daemon )
    {
        Properties context = new Properties();

        if ( StringUtils.isNotEmpty( daemon.getLongName() ) )
        {
            context.setProperty( "app.long.name", daemon.getLongName() );
        }
        else
        {
            context.setProperty( "app.long.name", request.getMavenProject().getName() );
        }

        if ( StringUtils.isNotEmpty( daemon.getName() ) )
        {
            context.setProperty( "app.name", daemon.getName() );
        }
        else
        {
            context.setProperty( "app.name", daemon.getId() );
        }

        String description = request.getMavenProject().getDescription();
        if ( description == null )
        {
            description = request.getMavenProject().getName();
        }
        context.setProperty( "app.description", description );

        String envSetupFileName = daemon.getEnvironmentSetupFileName();

        String envSetup = "";
        if ( envSetupFileName != null )
        {
            String envScriptPath = "\"%BASEDIR%\\bin\\" + envSetupFileName + ".bat\"";
            envSetup = "if exist " + envScriptPath + " call " + envScriptPath;
            context.put( "env.setup.windows", envSetup );

            envScriptPath = "\"$BASEDIR\"/bin/" + envSetupFileName;
            envSetup = "[ -f " + envScriptPath + " ] && . " + envScriptPath;
            context.put( "env.setup.unix", envSetup );
        }
        else
        {
            context.put( "env.setup.windows", envSetup );
            context.put( "env.setup.unix", envSetup );
        }

        return context;
    }

    private static void writeFile( File outputFile, Reader reader )
        throws DaemonGeneratorException
    {
        FileWriter out = null;

        try
        {
            outputFile.getParentFile().mkdirs();
            out = new FileWriter( outputFile );

            IOUtil.copy( reader, out );
        }
        catch ( IOException e )
        {
            throw new DaemonGeneratorException( "Error writing output file: " + outputFile.getAbsolutePath(), e );
        }
        finally
        {
            IOUtil.close( reader );
            IOUtil.close( out );
        }
    }

    private static void writeFile( File outputFile, InputStream inputStream )
        throws DaemonGeneratorException
    {
        FileOutputStream out = null;

        try
        {
            outputFile.getParentFile().mkdirs();
            out = new FileOutputStream( outputFile );

            IOUtil.copy( inputStream, out );
        }
        catch ( IOException e )
        {
            throw new DaemonGeneratorException( "Error writing output file: " + outputFile.getAbsolutePath(), e );
        }
        finally
        {
            IOUtil.close( inputStream );
            IOUtil.close( out );
        }
    }

    private void createClasspath( Daemon daemon, DaemonGenerationRequest request, FormattedProperties confFile,
                                  Properties configuration )
    {
        final String wrapperClassPathPrefix = "wrapper.java.classpath.";

        int counter = 1;
        // @todo dennisl: Shouldn't we be using %REPO_DIR% here?
        confFile.setProperty( wrapperClassPathPrefix + counter++, daemon.getRepositoryName() + "/wrapper.jar" );

        String configurationDirFirst = configuration.getProperty( "configuration.directory.in.classpath.first" );
        if ( configurationDirFirst != null )
        {
            confFile.setProperty( wrapperClassPathPrefix + counter++, configurationDirFirst );
        }

        MavenProject project = request.getMavenProject();
        ArtifactRepositoryLayout layout = request.getRepositoryLayout();

        // Load all jars in %ENDORSED_DIR% if specified
        if ( daemon.getEndorsedDir() != null )
        {
            confFile.setProperty( wrapperClassPathPrefix + counter++, daemon.getEndorsedDir() + "/*" );
        }

        if ( daemon.isUseWildcardClassPath() )
        {
            confFile.setProperty( wrapperClassPathPrefix + counter++, "%REPO_DIR%/*" );
        }
        else
        {
            confFile.setProperty( wrapperClassPathPrefix + counter++, "%REPO_DIR%/"
                + DependencyFactory.create( project.getArtifact(), layout, true,
                                            request.getOutputFileNameMapping() ).getRelativePath() );

            Iterator j = project.getRuntimeArtifacts().iterator();
            while ( j.hasNext() )
            {
                Artifact artifact = (Artifact) j.next();

                confFile.setProperty( wrapperClassPathPrefix + counter, "%REPO_DIR%/"
                    + DependencyFactory.create( artifact, layout, daemon.isUseTimestampInSnapshotFileName(),
                                                request.getOutputFileNameMapping() ).getRelativePath() );
                counter++;
            }
        }

        String configurationDirLast = configuration.getProperty( "configuration.directory.in.classpath.last" );
        if ( configurationDirLast != null )
        {
            confFile.setProperty( wrapperClassPathPrefix + counter++, configurationDirLast );
        }
    }

    private static void createAdditional( Daemon daemon, FormattedProperties confFile )
    {
        if ( daemon.getJvmSettings() != null )
        {
            int count = 1;
            for ( Iterator i = daemon.getJvmSettings().getSystemProperties().iterator(); i.hasNext(); count++ )
            {
                String systemProperty = (String) i.next();
                confFile.setProperty( "wrapper.java.additional." + count, "-D" + systemProperty );
            }
            for ( Iterator i = daemon.getJvmSettings().getExtraArguments().iterator(); i.hasNext(); count++ )
            {
                String extraArgument = (String) i.next();
                confFile.setProperty( "wrapper.java.additional." + count, extraArgument );
            }
        }
    }

    private static void createParameters( Daemon daemon, FormattedProperties confFile )
    {
        int count = StringUtils.isEmpty( daemon.getMainClass() ) ? 1 : 2;

        for ( Iterator<String> i = daemon.getCommandLineArguments().iterator(); i.hasNext(); count++ )
        {
            String argument = i.next();
            if ( StringUtils.isNotEmpty( argument ) )
            {
                confFile.setProperty( "wrapper.app.parameter." + count, argument );
            }
        }

        if ( count == 1 )
        { // Remove default parameter if not set
            confFile.removeProperty( "wrapper.app.parameter.1" );
        }
    }

    private void writeScriptFiles( DaemonGenerationRequest request, Daemon daemon, File outputDirectory,
                                   Properties context )
                                       throws DaemonGeneratorException
    {
        InputStream shellScriptInputStream = null;
        InputStream batchFileInputStream = null;
        try
        {
            // TODO: selectively depending on selected platforms instead of always doing both
            shellScriptInputStream = getUnixTemplate( daemon );

            if ( shellScriptInputStream == null )
            {
                throw new DaemonGeneratorException( "Could not load template." );
            }

            Reader reader = new InputStreamReader( shellScriptInputStream );

            String executableName = daemon.getId();
            if (StringUtils.isNotEmpty( request.getBinScriptName())) {
                executableName = request.getBinScriptName();
            }
            
            writeFilteredFile( request, daemon, reader, new File( outputDirectory, "bin/" + executableName ), context );

            batchFileInputStream = this.getWindowsTemplate( daemon );

            if ( batchFileInputStream == null )
            {
                throw new DaemonGeneratorException( "Could not load template." );
            }

            reader = new InputStreamReader( batchFileInputStream );

            writeFilteredFile( request, daemon, reader, new File( outputDirectory, "bin/" + executableName + ".bat" ),
                               context );
        }
        finally
        {
            IOUtil.close( shellScriptInputStream );
            IOUtil.close( batchFileInputStream );
        }
    }

    private InputStream getUnixTemplate( Daemon daemon )
        throws DaemonGeneratorException
    {
        return getTemplate( daemon.getUnixScriptTemplate(), "bin/sh.script.in" );
    }

    private InputStream getWindowsTemplate( Daemon daemon )
        throws DaemonGeneratorException
    {
        return getTemplate( daemon.getWindowsScriptTemplate(), "bin/AppCommand.bat.in" );
    }

    private InputStream getTemplate( String customTemplate, String internalTemplate )
        throws DaemonGeneratorException
    {

        InputStream is = null;

        try
        {
            if ( customTemplate != null )
            {
                File customTemplateFile = new File( customTemplate );
                if ( customTemplateFile.exists() )
                {
                    is = new FileInputStream( customTemplateFile );
                }
                else
                {
                    is = getClass().getClassLoader().getResourceAsStream( customTemplate );
                    if ( is == null )
                    {
                        throw new DaemonGeneratorException( "Unable to load external template resource: "
                            + customTemplate );
                    }

                }
            }
            else
            {
                is = this.getClass().getResourceAsStream( internalTemplate );
                if ( is == null )
                {
                    throw new DaemonGeneratorException( "Unable to load internal template resource: "
                        + internalTemplate );
                }
            }

        }
        catch ( FileNotFoundException e )
        {
            throw new DaemonGeneratorException( "Unable to load external template file", e );
        }

        return is;
    }

    private void writeLibraryFiles( File outputDirectory, Daemon daemon, List<String> jswPlatformIncludes )
        throws DaemonGeneratorException
    {
        if ( daemon.getExternalDeltaPackDirectory() != null )
        {
            copyExternalFile( new File( daemon.getExternalDeltaPackDirectory(), JSW_LIB_DIR + "/wrapper.jar" ),
                              new File( outputDirectory, daemon.getRepositoryName() + "/wrapper.jar" ) );
        }
        else
        {
            copyResourceFile( new File( outputDirectory, daemon.getRepositoryName() ), JSW_LIB_DIR, "wrapper.jar",
                              "wrapper.jar" );
        }

        for ( String platform : jswPlatformIncludes )
        {
            String libFile = JSW_PLATFORMS_MAP.get( platform + "-lib" );
            if ( libFile != null )
            {
                if ( daemon.getExternalDeltaPackDirectory() != null )
                {
                    copyExternalFile( new File( daemon.getExternalDeltaPackDirectory(), JSW_LIB_DIR + "/" + libFile ),
                                      new File( outputDirectory, daemon.getRepositoryName() + "/" + libFile ) );
                }
                else
                {
                    copyResourceFile( new File( outputDirectory, daemon.getRepositoryName() ), JSW_LIB_DIR, libFile,
                                      libFile );
                }
            }
            else
            {
                getLogger().warn( "Lib file for " + platform + " not found in map." );
            }
        }
    }

    private void writeExecutableFiles( File outputDirectory, Daemon daemon, List<String> jswPlatformIncludes )
        throws DaemonGeneratorException
    {
        for ( String platform : jswPlatformIncludes )
        {
            String execFile = JSW_PLATFORMS_MAP.get( platform + "-exec" );
            if ( execFile != null )
            {
                String outputExecFile = execFile;
                if ( daemon.isUseDaemonIdAsWrapperExePrefixName() )
                {
                    outputExecFile = StringUtils.replaceOnce( execFile, "wrapper", daemon.getId() );
                }

                if ( daemon.getExternalDeltaPackDirectory() != null )
                {
                    copyExternalFile( new File( daemon.getExternalDeltaPackDirectory(), JSW_BIN_DIR + "/" + execFile ),
                                      new File( outputDirectory, JSW_BIN_DIR + "/" + outputExecFile ) );
                }
                else
                {
                    copyResourceFile( new File( outputDirectory, JSW_BIN_DIR ), JSW_BIN_DIR, execFile, outputExecFile );
                }
            }
            else
            {
                getLogger().warn( "Exec file for " + platform + " not found in map." );
            }
        }
    }

    private void copyExternalFile( File from, File to )
        throws DaemonGeneratorException
    {
        try
        {
            from.getParentFile().mkdirs();
            FileUtils.copyFile( from, to );
        }
        catch ( IOException e )
        {
            throw new DaemonGeneratorException( "Could not copy external file: " + from, e );
        }

    }

    private void copyResourceFile( File outputDirectory, String inputDirectory, String fileName, String outFileName )
        throws DaemonGeneratorException
    {
        InputStream batchFileInputStream = this.getClass().getResourceAsStream( inputDirectory + "/" + fileName );

        if ( batchFileInputStream == null )
        {
            throw new DaemonGeneratorException( "Could not load library file: " + fileName );
        }

        writeFile( new File( outputDirectory, outFileName ), batchFileInputStream );
    }

    private List<String> getJswPlatformIncludes( Daemon daemon )
    {
        List<String> jswPlatformIncludes = null;
        for ( GeneratorConfiguration generatorConfiguration : daemon.getGeneratorConfigurations() )
        {
            if ( generatorConfiguration.getGenerator().equals( "jsw" ) )
            {
                jswPlatformIncludes = generatorConfiguration.getIncludes();
            }
        }

        // set default if none is specified
        if ( jswPlatformIncludes == null || jswPlatformIncludes.isEmpty() )
        {
            jswPlatformIncludes = new ArrayList<String>();
            jswPlatformIncludes.add( "aix-ppc-32" );
            jswPlatformIncludes.add( "aix-ppc-64" );
            jswPlatformIncludes.add( "linux-x86-32" );
            jswPlatformIncludes.add( "macosx-x86-universal-32" );
            jswPlatformIncludes.add( "solaris-x86-32" );
            jswPlatformIncludes.add( "windows-x86-32" );
        }

        return jswPlatformIncludes;
    }
}
