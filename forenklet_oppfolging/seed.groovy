def prosjektMappe = this.SCRIPT_FOLDER
def configJson = readFileFromWorkspace("${prosjektMappe}/config.json")
def config = new groovy.json.JsonSlurper().parseText(configJson)

def miljoer = ["t6", "q6", "q0"]
def promoteringConfig = [
        q6: "t6",
        q0: "q6",
]

def toEnvironmentString(properties) {
    return properties
            .collect({ property, verdi -> verdi ? "${property}=\"${verdi}\"" : "${property}=null" })
            .join("\n")
}

def maskPasswords(inputString) {
    regex = /(?i)(.*passord=(.*))/
    matchResult = inputString =~ regex
    if(matchResult.matches()) {
        password = m[0][2]
        return inputString.replace(password, '*******')
    } else {
        return inputString
    }
}

def safePrintln(someString) {
    def safeToPrint = maskPasswords(someString)
    println(safeToPrint)
}

config.forEach({ applikasjonsNavn, applikasjonsConfig ->

    println(applikasjonsNavn)
    safePrintln(applikasjonsConfig)
    def applikasjonsMappe = "${prosjektMappe}/${applikasjonsNavn}"
    folder(applikasjonsMappe)

    def triggerMappe = "${prosjektMappe}/-triggere-"
    folder(triggerMappe)

    def gitUrl = applikasjonsConfig.gitUrl
    def type = applikasjonsConfig.type
    def downstreamConfig = applikasjonsConfig.downstream
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
                numToKeep(5)
            }

            def pipelineScript = readFileFromWorkspace("${prosjektMappe}/bygge-pipeline.groovy")
            def pipelineKonstanter = toEnvironmentString([
                    gitUrl          : gitUrl,
                    branch          : branchName,
                    applikasjonsNavn: applikasjonsNavn,
                    downstream      : downstreamConfig ? "${prosjektMappe}/${downstreamConfig}/master" : null,
                    sone            : applikasjonsConfig.sone, // nais-applikasjoner må ha dette
            ])

            definition {
                cps {
                    script("${pipelineKonstanter}\n\n${pipelineScript}")
                    sandbox() // ellers må noen manuelt godkjenne scriptet!
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

    promoteringConfig.forEach({ tilMiljo, fraMiljo ->
        pipelineJob("${applikasjonsMappe}/-promotering-${tilMiljo}-") {
            description("Promotering fra ${fraMiljo} til ${tilMiljo}")
            concurrentBuild(false)

            logRotator {
                numToKeep(20)
            }

            def pipelineScript = readFileFromWorkspace("${prosjektMappe}/promotering-pipeline.groovy")
            def pipelineKonstanter = toEnvironmentString([
                    tilMiljo        : tilMiljo,
                    fraMiljo        : fraMiljo,
                    applikasjonsNavn: applikasjonsNavn,
                    sone            : applikasjonsConfig.sone, // nais-applikasjoner må ha dette
            ])

            definition {
                cps {
                    script("${pipelineKonstanter}\n\n${pipelineScript}")
                    sandbox() // ellers må noen manuelt godkjenne scriptet!
                }
            }
        }

    })

    miljoer.forEach({ miljo ->
        pipelineJob("${applikasjonsMappe}/-miljotest-${miljo}-") {
            concurrentBuild(false)

            logRotator {
                numToKeep(20)
            }

            def pipelineScript = readFileFromWorkspace("${prosjektMappe}/miljotest-pipeline.groovy")
            def pipelineKonstanter = toEnvironmentString([
                    miljo: miljo,
                    applikasjonsNavn: applikasjonsNavn,
                    type: type,
					gitUrl: gitUrl,
					branch: branchName
            ])

            definition {
                cps {
                    script("${pipelineKonstanter}\n\n${pipelineScript}")
                    sandbox() // ellers må noen manuelt godkjenne scriptet!
                }
            }
        }
    })

    if(gitUrl.contains("github")){
        pipelineJob("${applikasjonsMappe}/-backup-") {
            concurrentBuild(false)

            logRotator {
                numToKeep(20)
            }

            triggers {
                cron("H/2 * * * *")
            }

            def pipelineScript = readFileFromWorkspace("${prosjektMappe}/backup-pipeline.groovy")
            def pipelineKonstanter = toEnvironmentString([
                    applikasjonsNavn: applikasjonsNavn,
                    gitUrl          : gitUrl
            ])

            definition {
                cps {
                    script("${pipelineKonstanter}\n\n${pipelineScript}")
                    sandbox() // ellers må noen manuelt godkjenne scriptet!
                }
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
                sone            : applikasjonsConfig.sone, // nais-applikasjoner må ha dette
        ])

        definition {
            cps {
                script("${pipelineKonstanter}\n\n${pipelineScript}")
                sandbox() // ellers må noen manuelt godkjenne scriptet!
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

