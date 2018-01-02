environmentFile = "__build-environment__"
pusDockerImagePrefiks = "docker.adeo.no:5000/pus/"
smoketestFrontendImage = "${pusDockerImagePrefiks}smoketest-frontend"

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"


    stage("setup") {

        def environment = """
NEXUS_USERNAME=${NEXUS_USERNAME}
NEXUS_PASSWORD=${NEXUS_PASSWORD}
BUILD_URL=${BUILD_URL}
buildUrl=${BUILD_URL}
applikasjonsNavn=${applikasjonsNavn}
miljo=${miljo}
testmiljo=${miljo}
domenebrukernavn=${domenebrukernavn}
domenepassord=${domenepassord}
sone=${sone}
http_proxy=${http_proxy}
https_proxy=${https_proxy}
no_proxy=${no_proxy}
gitUrl=${gitUrl}
"""
        println(environment)
        writeFile([
                file: environmentFile,
                text: environment
        ])

    }

    if (type == "frontend") {

        stage("smoketest-frontend") {
            sh("docker run" +
                    " --rm" +  // slett container etter kjøring
                    " --volume=/var/run/docker.sock:/var/run/docker.sock" + // gjør det mulig å starte nye containere fra denne containeren.
                    " -p 80:80" +
                    " --env-file ${environmentFile}" +
                    " ${smoketestFrontendImage}"
            )
        }

        stage("uu-validator") {

            // TODO


        }

    }

    if (type == "backend") {


        // TODO maven!



    }


}