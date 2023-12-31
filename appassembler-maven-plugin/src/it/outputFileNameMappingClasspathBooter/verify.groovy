/**
 *
 * The MIT License
 *
 * Copyright 2006-2013 The Codehaus.
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

import java.io.*
import java.util.*

import groovy.util.XmlSlurper


t = new IntegrationBase();

def repoFolder = new File( basedir, "target/generated-resources/appassembler/booter-windows/repo");
def scriptFolder = new File( basedir, "target/generated-resources/appassembler/booter-windows/");

def xmlFile = new File( scriptFolder, "etc/app.xml" );

/**
* This will filter out the project version out of the
* pom.xml file, cause currently no opportunity exists to
* get this information via Maven Invoker Plugin into
* the Groovy script code.
* @return Version information.
*/
def getProjectVersion() {
   def pom = new XmlSlurper().parse(new File(basedir, 'pom.xml'))

   def allDependencies = pom.dependencies;

   def dependencies = allDependencies.dependency

   def appassemblerModule = dependencies.find {
       item -> item.groupId.equals("org.codehaus.mojo.appassembler") && item.artifactId.equals("appassembler-model");
   }

   return appassemblerModule.version;
}

def projectVersion = getProjectVersion();

def filesInRepository = [
 "junit/junit/3.8.1/junit.jar",
 "net/java/dev/stax-utils/stax-utils/20070216/stax-utils.jar",
 "org/codehaus/mojo/appassembler/appassembler-booter/" + projectVersion + "/appassembler-booter.jar",
 "org/codehaus/mojo/appassembler/appassembler-model/" + projectVersion + "/appassembler-model.jar",
 "org/codehaus/mojo/appassembler-maven-plugin/it/mappasm-71-5/1.0-SNAPSHOT/mappasm-71-5.jar",
 "org/codehaus/plexus/plexus-utils/1.1/plexus-utils.jar",
 "stax/stax/1.1.2-dev/stax.jar",
 "stax/stax-api/1.0.1/stax-api.jar"
]

println "---> Checking files in repository."

filesInRepository.each {
  fileInRepository ->
    print "Checking file " + fileInRepository + " in repository..."
    def fileToCheck = new File( repoFolder, fileInRepository);
    if (!fileToCheck.canRead()) {
      throw new FileNotFoundException("Could not find " + fileInRepository + " in generated repository.");
    }
    println " done."
}

println "---> Checking files in classpath."

def classpathElements = []
def configuration = new XmlSlurper().parse(xmlFile)
def classpath = configuration.classpath
def allDependencies = classpath.dependencies
def dependencies = allDependencies.dependency
dependencies.each {
    dependency ->
        def relativePath = dependency.relativePath
        classpathElements.add( relativePath.text() );
}

// TODO: For debugging purposes only, remove before release
println "     classpathElements"
classpathElements.each {
    classpathElement ->
        println "     - classpathElement: '" + classpathElement + "'"
}

filesInRepository.each {
    classpathElement -> print "Checking for '" + classpathElement + "' in classpath..."
    if (!classpathElements.contains(classpathElement)) {
        println ""
        println "The classpath contains the following: " + classpathElements
        throw new FileNotFoundException("We couldn't find '" + classpathElement + "' in classpath (booter).");
    }
    println " done."
}

return true;
