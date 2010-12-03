includeTargets << grailsScript("Init")
includeTargets << new File("${basedir}/scripts/_DojoTasks.groovy")

target(main: "Will create the optimized dojo version and copy to the application.") {
    depends(buildDojo,copyDojoToApp,cleanup)
}

setDefaultTarget(main)