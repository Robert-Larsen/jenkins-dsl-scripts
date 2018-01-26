pusDockerImagePrefiks = "docker.adeo.no:5000/pus/"
environmentFile = "__build-environment__"
plattform = sone != null ? "nais" : "skya"

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"

    def versjon
    stage("hent versjon fra vera - ${fraMiljo}") {
        def url = "https://vera.adeo.no/api/v1/deploylog?onlyLatest=true&application=${applikasjonsNavn}&environment=${fraMiljo}"
        def deployLog = new groovy.json.JsonSlurper().parse(new URL(url))
        versjon = deployLog[0].version
    }

    stage("deploy til ${tilMiljo}") {

        writeFile([
                file: environmentFile,
                text:
"""
BUILD_URL=${BUILD_URL}
domenebrukernavn=${domenebrukernavn}
domenepassord=${domenepassord}
versjon=${versjon}
applikasjonsNavn=${applikasjonsNavn}
miljo=${tilMiljo}
sone=${sone}
plattform=${plattform}
"""
        ])

        sh("docker run" +
                " --rm" +  // slett container etter kjøring
                " --env-file ${environmentFile}" +
                " ${pusDockerImagePrefiks}deploy"
        )

        if (miljotest) {
            build([
                    job : "./-miljotest-${tilMiljo}-",
                    wait: false
            ])
        }
    }

}