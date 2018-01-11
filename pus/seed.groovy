def prosjektMappe = this.SCRIPT_FOLDER
def configJson = readFileFromWorkspace("${prosjektMappe}/config.json")
def config = new groovy.json.JsonSlurper().parseText(configJson)

def triggerMappe = "${prosjektMappe}/-triggere-"
folder(triggerMappe)

config.forEach({ applikasjonsNavn, applikasjonsConfig ->
    def pipelineNavn = "${prosjektMappe}/${applikasjonsNavn}"
    def gitUrl = applikasjonsConfig.gitUrl

    pipelineJob(pipelineNavn) {

        concurrentBuild(false)

        parameters {
            stringParam("branch", "master")
        }

        logRotator {
            numToKeep(20)
        }

        def pipelineKonstanter = [
                gitUrl          : gitUrl,
                applikasjonsNavn: applikasjonsNavn
        ].collect({ property, verdi -> "${property}=\"${verdi}\"" }).join("\n")

        def pipelineScript = readFileFromWorkspace("${prosjektMappe}/pipeline.groovy")

        definition {
            cps {
                script("${pipelineKonstanter}\n\n${pipelineScript}")
                sandbox() // ellers må noen manuelt godkjenne scriptet!
            }
        }
    }

    job("${triggerMappe}/${applikasjonsNavn}") {

        scm {
            git {
                branch("master")
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
})


