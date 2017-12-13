import groovy.json.JsonSlurper

def prosjektMappe = this.SCRIPT_FOLDER

pipelineJob("${prosjektMappe}/-release-") {
    concurrentBuild(false)

    logRotator {
        numToKeep(20)
    }

    definition {
        cps {
            script(readFileFromWorkspace("${prosjektMappe}/release-pipeline.groovy"))
            sandbox() // ellers må noen manuelt godkjenne scriptet!
        }
    }
}


def jsonSlurper = new JsonSlurper()
def tjenesterRepositories = jsonSlurper.parse(new URL("http://stash.devillo.no/rest/api/1.0/projects/common/repos?limit=500"))
tjenesterRepositories.values.forEach { repo ->

    def applikasjonsNavn = repo.slug
    if (applikasjonsNavn == "release") {
        return
    }

    def appMappe = "${prosjektMappe}/${applikasjonsNavn}"
    folder(appMappe)

    def branches = jsonSlurper.parse(new URL("http://stash.devillo.no/rest/api/1.0/projects/common/repos/${applikasjonsNavn}/branches?limit=500"))
    branches.values.forEach { branch ->

        def branchId = branch.displayId
        def normalizedBranch = branchId.replaceAll("/", "-")
        pipelineJob("${appMappe}/${normalizedBranch}") {
            concurrentBuild(false)

            logRotator {
                numToKeep(20)
            }

            def pipelineKonstanter = [
                    gitUrl: "ssh://git@stash.devillo.no:7999/common/${applikasjonsNavn}.git",
                    branch: branchId,
            ].collect({ property, verdi -> "${property}=\"${verdi}\"" }).join("\n")

            def prefix = applikasjonsNavn == "release" ? "release" : "bygge";
            def pipelineScript = readFileFromWorkspace("${prosjektMappe}/${prefix}-pipeline.groovy")

            definition {
                cps {
                    script("${pipelineKonstanter}\n\n${pipelineScript}")
                    sandbox() // ellers må noen manuelt godkjenne scriptet!
                }
            }
        }
    }
}