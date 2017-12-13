def seedPrefix = "-seed-"
def workspace = hudson.model.Executor.currentExecutor().getCurrentWorkspace()
println("workspace: ${workspace}")

println("${JENKINS_URL}")

listView("alle-jobber") {
    recurse(true)
    jobFilters {
        status {
            // Hack: dette eksluderer mapper, siden de ikke har status
            status(Status.values())
        }
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

listView("feilende-jobber") {
    recurse(true)
    jobFilters {
        status {
            status(Status.FAILED, Status.UNSTABLE)
        }
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

listView("seed-jobber") {
    recurse(true)
    jobs {
        regex("${seedPrefix}.*")
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

new hudson.FilePath(workspace, ".").list().each { mappe ->
    def mappeNavn = mappe.getName()
    println(mappeNavn)

    if(!mappe.isDirectory() || ".git" == mappeNavn){
        return
    }

    // se også DSL.groovy
    def fileNameNoExtension = mappeNavn
    def folderName = "${fileNameNoExtension}"

    def jobName = "${folderName}/${seedPrefix}${fileNameNoExtension}-"
    println("${mappeNavn} -> ${jobName}")

    folder(folderName)
    job(jobName) {

        parameters {
            stringParam("SCRIPT_FOLDER", folderName)
        }

        scm {
            git {
                remote {
                    url(GIT_URL)
                }
                branch(GIT_BRANCH)
            }
        }

        triggers {
            scm('') // Dette gjør at webhook fungerer...
            cron('H/5 * * * *') // kjør relativt ofte slik at branch-spesifikke jobber genereres
        }

        logRotator {
            numToKeep(10)
        }

        steps {
            dsl {
                external("${mappeNavn}/seed.groovy")
                removeAction('DELETE')
                removeViewAction('DELETE')
            }
        }

        this.queue(jobName)
    }
}