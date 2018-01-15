def prosjektMappe = this.SCRIPT_FOLDER
def configJson = readFileFromWorkspace("${prosjektMappe}/config.json")
def config = new groovy.json.JsonSlurper().parseText(configJson)

def toEnvironmentString(properties) {
    return properties
            .collect({ property, verdi -> verdi ? "${property}=\"${verdi}\"" : "${property}=null" })
            .join("\n")
}

config.forEach({ applikasjonsNavn, applikasjonsConfig ->

    println(applikasjonsNavn)
    def applikasjonsMappe = "${prosjektMappe}/${applikasjonsNavn}"
    folder(applikasjonsMappe)

    def triggerMappe = "${prosjektMappe}/-triggere-"
    folder(triggerMappe)

    def gitUrl = applikasjonsConfig.gitUrl
    def lsRemoteProcess = "git ls-remote --heads ${gitUrl}".execute()
    def lsRemote = lsRemoteProcess.in.text
    println(lsRemote)

    lsRemote.split("\n").each { ref ->
        if (ref.isEmpty()) {
            return
        }
        def refsPrefix = "refs/heads"
        def branchName = ref.substring(ref.indexOf(refsPrefix) + refsPrefix.length() + 1)
        println("${ref} -> ${branchName}")
        def normalizedBranch = branchName.replaceAll("/", "-")

        def pipelineNavn = "${applikasjonsMappe}/${normalizedBranch}"
        pipelineJob(pipelineNavn) {
            concurrentBuild(false)

            logRotator {
                numToKeep(20)
            }

            def pipelineScript = readFileFromWorkspace("${prosjektMappe}/bygge-pipeline.groovy")
            def pipelineKonstanter = toEnvironmentString([
                    gitUrl          : gitUrl,
                    branch          : branchName,
                    applikasjonsNavn: applikasjonsNavn,
                    sone            : applikasjonsConfig.sone, // nais-applikasjoner m� ha dette
            ])

            definition {
                cps {
                    script("${pipelineKonstanter}\n\n${pipelineScript}")
                    sandbox() // ellers m� noen manuelt godkjenne scriptet!
                }
            }
        }

        job("${triggerMappe}/${applikasjonsNavn}-${normalizedBranch}") {

            scm {
                git {
                    branch(branchName)
                    remote {
                        url(gitUrl)
                    }
                }
            }
            triggers {
                scm("H/2 * * * *")
            }

            publishers {
                downstream(pipelineNavn)
            }

        }
    }

    pipelineJob("${applikasjonsMappe}/-promotering-") {
        concurrentBuild(false)

        logRotator {
            numToKeep(20)
        }

        def pipelineScript = readFileFromWorkspace("${prosjektMappe}/promotering-pipeline.groovy")
        def pipelineKonstanter = toEnvironmentString([
                applikasjonsNavn: applikasjonsNavn,
                sone            : applikasjonsConfig.sone, // nais-applikasjoner m� ha dette
        ])

        definition {
            cps {
                script("${pipelineKonstanter}\n\n${pipelineScript}")
                sandbox() // ellers m� noen manuelt godkjenne scriptet!
            }
        }
    }


    pipelineJob("${applikasjonsMappe}/-release-") {
        concurrentBuild(false)

        logRotator {
            numToKeep(20)
        }

        def pipelineScript = readFileFromWorkspace("${prosjektMappe}/release-pipeline.groovy")
        def pipelineKonstanter = toEnvironmentString([
                applikasjonsNavn: applikasjonsNavn,
                sone            : applikasjonsConfig.sone, // nais-applikasjoner m� ha dette
        ])

        definition {
            cps {
                script("${pipelineKonstanter}\n\n${pipelineScript}")
                sandbox() // ellers m� noen manuelt godkjenne scriptet!
            }
        }
    }

    println()
    println()
})


listView("${prosjektMappe}/feilende-jobber") {
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

listView("${prosjektMappe}/master-jobber") {
    jobs {
        regex("[^-].*/master")
    }
    recurse(true)
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
