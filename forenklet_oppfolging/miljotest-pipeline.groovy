environmentFile = "__build-environment__"
pusDockerImagePrefiks = "docker.adeo.no:5000/pus/"
smoketestFrontendImage = "${pusDockerImagePrefiks}smoketest-frontend"

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"


    stage("setup") {

        def environment = """
APPLIKASJONSNAVN=${applikasjonsNavn}
MILJO=${miljo}
DOMENEBRUKERNAVN=${domenebrukernavn}
DOMENEPASSORD=${domenepassord}
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