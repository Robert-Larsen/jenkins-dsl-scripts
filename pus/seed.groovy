def prosjektMappe = this.SCRIPT_FOLDER
def configJson = readFileFromWorkspace("${prosjektMappe}/config.json")
def config = new groovy.json.JsonSlurper().parseText(configJson)


config.forEach({ applikasjonsNavn, applikasjonsConfig ->

    pipelineJob("${prosjektMappe}/${applikasjonsNavn}") {

        concurrentBuild(false)

        parameters {
            stringParam("branch", "master")
        }

        logRotator {
            numToKeep(20)
        }

        def pipelineKonstanter = [
                gitUrl          : applikasjonsConfig.gitUrl,
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
})


