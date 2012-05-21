import grails.converters.deep.JSON
import grails.util.GrailsUtil


// Load Dojo static class
GroovyClassLoader classLoader = new GroovyClassLoader();
Class Dojo = classLoader.parseClass(new File("${dojoPluginDir}/src/groovy/org/dojotoolkit/Dojo.groovy"));

def srcHref = "http://download.dojotoolkit.org/release-${Dojo.version}/dojo-release-${Dojo.version}-src.zip"
def downloadDir = "${grailsWorkDir}/download"
def tmpWorkingDir = "${basedir}/web-app/js/dojoTmp"
def dojoUtilDir = "${tmpWorkingDir}/util/"
def dojoReleaseDir = "${tmpWorkingDir}/release"
def dojoCssBuildFile = "${tmpWorkingDir}/css/custom-dojo.css"
def dojoProfile = "${tmpWorkingDir}/dojo.profile.js"
def dojoUiDir = "${dojoPluginDir}/web-app/js/dojo/${Dojo.version}/dojoui"
def config = new ConfigSlurper(GrailsUtil.environment).parse(new File("${basedir}/grails-app/conf/Config.groovy").toURL())



target(createOptimizedDojoBuild: "This is the main target for this script."){
  println "Creating an Optimized Dojo build..."
  depends(downloadDojoSource, createDojoCssBuild, createDojoProfile, buildDojo, copyDojoToStage ,cleanup)
}


/**
 * DownloadSource - This will install the source version of Dojo. (About 25MB)
 * It will copy the src to the application. From there it will build the
 * customized version.
 *
 * By copying the source to the application, we can include app specific
 * extensions to our custom release.
 */
target(downloadDojoSource: "This will download the source version of Dojo.") {
  println "Downloading Dojo ${Dojo.version} source files..."
  Ant.sequential {
    mkdir(dir: downloadDir)
    mkdir(dir: tmpWorkingDir)
    mkdir(dir: dojoReleaseDir)
    get(dest: "${downloadDir}/dojo-src-${Dojo.version}.zip", src: "${srcHref}", verbose: true, usetimestamp: true)
    unzip(dest: downloadDir, src: "${downloadDir}/dojo-src-${Dojo.version}.zip")
    move(todir: tmpWorkingDir) {
      fileset(dir: "${downloadDir}/dojo-release-${Dojo.version}-src", includes: "**/**")
    }
    copy(todir: "${tmpWorkingDir}/dojoui/") {
      fileset(dir: dojoUiDir, includes: "**/**")
    }
    copy(todir: "${tmpWorkingDir}/css/") {
      fileset(dir: "${dojoPluginDir}/web-app/js/dojo/${Dojo.version}/css", includes: "**/**")
    }
  }
}



/**
 * This will create a css file and add @import statements that the build process can use.
 */
target(createDojoCssBuild: "This will create a css build file.") {
  println "Creating Custom Dojo CSS build file..."
  def jsonString, cssFileList
  if(config.dojo.css){
    // Dojo 1.7 and up (Plugin 1.7.0.0++
    jsonString = config.dojo.css
    cssFileList = JSON.parse("{${jsonString}}").dependencies;
  }
  else{
    // Pre Dojo 1.7 (Plugin 1.6.1.3--)
    jsonString = config.dojo.profile
    cssFileList = JSON.parse("{${jsonString}}")?.dependencies?.css?.dependencies
  }

  if (cssFileList) {
    echo(file: dojoCssBuildFile, append: false, message: "/* This is generated by the dojo plugin build script */\n\n")
    cssFileList?.each {
      echo(file: dojoCssBuildFile, append: true, message: "@import '${it}';\n")
    }
  }
}



/**
 * This will create the dojo profile script file. Reading the Config.groovy statically since
 * it hasn't been compiled. Then it will create the dojo.profile.js file.
 */
target(createDojoProfile: "This will create the dojo profile js file") {
  println "Creating dojo profile js file..."
  echo(file: dojoProfile, append: false, message: "/* This is generated by the dojo plugin build script */\n\n")
  echo(file: dojoProfile, append: true, message: config.dojo.profile)
}



/**
 * Build Dojo - This will do the same as call the shell script to create the optimized
 *              version of dojo. This will use the Rhino version to create the build.
 *
 * Example of original build. (Assumes that it's being called from the buildscripts directory) :
 * java -Xms256m -Xmx256m -cp ./../shrinksafe/js.jar:./../closureCompiler/compiler.jar
 * :./../shrinksafe/shrinksafe.jar org.mozilla.javascript.tools.shell.Main
 * ./../../dojo/dojo.js baseUrl=./../../dojo load=build  action=release profile=../../../../../grails-app/conf/dojo.profile.js
 *
 * Furthur notes about Dojo 1.7 custom builds:
 * http://livedocs.dojotoolkit.org/releasenotes/1.7#considerations-for-custom-builds
 *
 */
target(buildDojo: "This will run shrinksafe to create an optimized version of dojo") {
  println "Runnning Google Closure Compiler to create an optimized dojo..."
  def build_classpath = Ant.path {
    pathelement(location: "${dojoUtilDir}/shrinksafe/js.jar")
    pathelement(location: "${dojoUtilDir}/shrinksafe/shrinksafe.jar")
    pathelement(location: "${dojoUtilDir}/closureCompiler/compiler.jar")
  }
  java(classname: "org.mozilla.javascript.tools.shell.Main", fork: true,
          dir: "${dojoUtilDir}/buildscripts", classpath: build_classpath) {

    arg(value: "${tmpWorkingDir}/dojo/dojo.js")
    arg(value: "releaseDir=${dojoReleaseDir}")
    arg(value: "action=release")
    arg(value: "cssOptimize=comments")
    arg(value: "mini=true")
    arg(value: "layerOptimize=closure")
    arg(value: "optimize=closure")
    arg(value: "stripConsole=all")
    arg(value: "selectorEngine=acme")
    arg(value: "load=build")
    arg(value: "profile=${dojoProfile}")
    arg(value: "baseUrl=${tmpWorkingDir}/dojo")
  }

  delete(includeemptydirs: true) {
    fileset(dir: dojoReleaseDir, includes: "**/tests/**/")
    fileset(dir: dojoReleaseDir, includes: "**/demos/**/")
    fileset(dir: dojoReleaseDir, includes: "**/themeTester*")
    fileset(dir: dojoReleaseDir, includes: "**/*.psd")
    fileset(dir: dojoReleaseDir, includes: "**/*.fla")
    fileset(dir: dojoReleaseDir, includes: "**/*.svg")
    fileset(dir: dojoReleaseDir, includes: "**/*.as")
    fileset(dir: dojoReleaseDir, includes: "**/*.swf")
    fileset(dir: dojoReleaseDir, includes: "**/*.uncompressed.js")
    fileset(dir: dojoReleaseDir, includes: "**/package.json")
    fileset(dir: dojoReleaseDir, includes: "**/build-report.txt")
    fileset(dir: dojoReleaseDir, includes: "**/DojoGrailsSpinner.js")
  }


  // Hack - spinner wont work if processed by build process. So copy over original.
  // DojoGrailsSpinner.js needs to be written in AMD format so we can remove this hack. (RM 3-31-2012)
  // Done (PS 5-19-2012)
  //copy(file:"${dojoUiDir}/DojoGrailsSpinner.js", tofile: "${dojoReleaseDir}/dojoui/DojoGrailsSpinner.js")
}



/**
 * Will copy the customized dojo release to the staging directory during war
 * creation. This is called from _Events.groovy.
 */
target(copyDojoToStage: "This will copy the optimized dojo release to stage.") {
  println "Copying optimized dojo release to the staging area..."
  def destinationDir = "${stagingDir}/js/dojo/${Dojo.pluginVersion}"
  copy(todir: "${destinationDir}-custom") {
    fileset(dir: dojoReleaseDir, includes: "**/**")
  }
}



/**
 * Will copy the customized dojo release to the application. This is called
 * from InstallDojo.groovy.
 */
target(copyDojoToApp: "Copies the optimized dojo release to application.") {
  println "Copying custom dojo build to the application..."
  def destinationDir = "${basedir}/web-app/js/dojo/${Dojo.pluginVersion}"
  copy(todir: "${destinationDir}-custom") {
    fileset(dir: dojoReleaseDir, includes: "**/**")
  }
}



/**
 * This will delete the tmp Directory.
 */
target(cleanup: "This will copy the optimized dojo release to application.") {
  println "Cleaning up custom dojo build tmp files..."
  delete(dir: tmpWorkingDir)
  delete(file: dojoProfile)
}
