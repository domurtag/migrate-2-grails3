import grails.build.logging.GrailsConsole
import grails.util.Metadata
import org.apache.commons.lang.StringUtils
import  org.apache.commons.io.FileUtils

includeTargets << grailsScript("_GrailsInit")
includeTargets << grailsScript("_GrailsArgParsing")

target(migrate: "Migrates a Grails 2.X app or plugin to Grails 3") {

    depends(parseArguments)

    def console = GrailsConsole.getInstance()

    String pathToTargetApp = argsMap.params[0]
    def pluginPackageName

    String baseDir = grailsSettings.baseDir

    if (grailsSettings.pluginProject) {
        pluginPackageName = getPluginPackageName(argsMap)
    }

    File targetDir = makeFile(baseDir, pathToTargetApp)

    if (!targetDir.directory) {
        console.error "Grails 3 project not found at $targetDir - quitting"
        return
    }

    def targetSrcDir = makeFile(targetDir, ['src', 'main'])

    Closure copier = { File src, File target -> FileUtils.copyDirectoryToDirectory(src, target)}

    // Copy groovy source
    copyDir(copier, grailsSettings.sourceDir, 'groovy', targetSrcDir)

    // Copy java source - in 3.0.0.RC1 the java dir isn't created by create-app, so we need to make it ourselves.
    // Check for it's existence in case this changes between now and the 3.X final release
    File targetJavaSrcDir = makeFile(targetDir, ['src', 'main', 'java'])
    createDirectoryIfNotExists(targetJavaSrcDir)

    copyDir(copier, grailsSettings.sourceDir, 'java', targetSrcDir)

    // copy grails-app
    copyDir(copier, baseDir, 'grails-app', targetDir, 'grails-app')

    // copy the unit and integration tests
    def sourceTestsBase = grailsSettings.testSourceDir
    copier = { File src, File target -> FileUtils.copyDirectory(src, target)}
    copyDir(copier, sourceTestsBase, 'unit', targetDir, ['src', 'test', 'groovy'])
    copyDir(copier, sourceTestsBase, 'integration', targetDir, ['src', 'integration-test', 'groovy'])

    copyDir(copier, sourceTestsBase, 'functional', targetDir, ['src', 'integration-test', 'groovy'])

    // copy web-app and scripts
    copyDir(copier, baseDir, 'web-app', targetDir, ['src', 'main', 'webapp'])
    copyDir(copier, baseDir, 'scripts', targetDir, ['src', 'main', 'scripts'])

    // copy various individual files
    copyFile(baseDir, ['grails-app', 'conf', 'UrlMappings.groovy'], targetDir, ['grails-app', 'controllers', 'UrlMappings.groovy'])
    copyFile(baseDir, ['grails-app', 'conf', 'BootStrap.groovy'], targetDir, ['grails-app', 'init', 'BootStrap.groovy'])
}

def createDirectoryIfNotExists(File dir) {
    if (!dir.directory) {
        assert dir.mkdir()
    }
}


/**
 * Copies a file
 * @param srcBase File/String indicating the base source dir
 * @param srcRelative String/List<String> indicating the relative path from srcBase to the source file
 * @param targetBase File/String indicating the base target dir
 * @param targetRelative String/List<String> indicating the relative path from targetBase to the target file. If targetBase
 * is the target file, this may be omitted
 */
void copyFile(srcBase, srcRelative, targetBase, targetRelative = StringUtils.EMPTY) {

    def src = makeFile(srcBase, srcRelative)
    def console = GrailsConsole.instance

    if (src.file) {
        def target = makeFile(targetBase, targetRelative)
        console.info "Copying $src to $target"
        FileUtils.copyFile(src, target)

    } else {
        console.warn "File $src not found in Grails 2 project - skipping"
    }
}

/**
 * Copies the contents of one directory to another
 * @param copier performs the copying
 * @param srcBase File/String indicating the base source dir
 * @param srcRelative String/List<String> indicating the relative path from srcBase to the source dir
 * @param targetBase File/String indicating the base target dir
 * @param targetRelative String/List<String> indicating the relative path from targetBase to the target dir. If targetBase
 * is the target dir, this may be omitted
 */
void copyDir(Closure copier, srcBase, srcRelative, targetBase, targetRelative = StringUtils.EMPTY) {
    def src = makeFile(srcBase, srcRelative)
    def console = GrailsConsole.instance

    if (src.directory) {
        def target = makeFile(targetBase, targetRelative)
        console.info "Copying contents of $src to $target"
        copier(src, target)
    } else {
        console.warn "Directory $src not found in Grails 2 project - skipping"
    }
}

/**
 * Returns the package name that will be used for the plugin descriptor class
 * @param argsMap
 * @return
 */
String getPluginPackageName(argsMap) {
    if (argsMap.params.size() > 1) {
        argsMap.params[1]

    } else {
        String appName = Metadata.current['app.name']

        // dashes aren't allowed in package names
        appName = StringUtils.remove(appName, '-')
        "grails.plugins.$appName"
    }
}

/**
 * Construct a canonical file from a base file or path and a relative path from it
 * @param base a File or path (String) to a file
 * @param relative a String path or List of path components that are joined to form the relative path
 * @return
 */
File makeFile(base, relative) {

    String pathSeparator = System.getProperty('file.separator')

    if (relative instanceof List) {
        relative = relative.join(pathSeparator)
    }
    String relativePath = [base.toString(), relative].join(pathSeparator)
    new File(relativePath).canonicalFile
}

setDefaultTarget(migrate)