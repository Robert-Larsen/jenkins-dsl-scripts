environmentFile = "__build-environment__"
pusDockerImagePrefiks = "docker.adeo.no:5000/pus/"
smoketestFrontendImage = "${pusDockerImagePrefiks}smoketest-frontend"
uuDefinitionFile = "uu-definisjon.js"
uuValidatorImage = "${pusDockerImagePrefiks}uu-validator"
miljotestUrl = "http://bekkci.devillo.no/job/forenklet_oppfolging/job/${applikasjonsNavn}/job/-miljotest-${miljo}-/"

def notifyOnError(errorMessage) {
    if (gitCommitHash != null) {
        sh("docker pull ${notifierDockerImage}")
        sh("docker run" +
                " --rm" +  // slett container etter kjøring
                " --env-file ${environmentFile}" +
                " -e MILJOTEST_ERROR_MSG=${errorMessage}" +
                " ${notifierDockerImage}"
        )
    }
}

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"
	
	stage("cleanup") {
		deleteDir()
	}
	
	stage("checkout") {
		sh "git clone -b master ${gitUrl} ."
	}

    stage("setup") {
def environment = """
APPLIKASJONSNAVN=${applikasjonsNavn}
NEXUS_USERNAME=${NEXUS_USERNAME}
NEXUS_PASSWORD=${NEXUS_PASSWORD}
miljo=${miljo}
MILJO=${miljo}
testmiljo=${miljo}
TESTMILJO=${miljo}
domenebrukernavn=${domenebrukernavn}
DOMENEBRUKERNAVN=${domenebrukernavn}
domenepassord=${domenepassord}
DOMENEBRUKER=${domenebrukernavn}
DOMENEPASSORD=${domenepassord}
http_proxy=${http_proxy}
https_proxy=${https_proxy}
no_proxy=${no_proxy}
SONE=${sone}
"""
		
        println(environment)
        writeFile([
                file: environmentFile,
                text: environment
        ])

    }

    if (type == "frontend") {

        stage("smoketest-frontend") {

            try {
                sh("docker run" +
                        " --rm" +  // slett container etter kjøring
                        " --volume=/var/run/docker.sock:/var/run/docker.sock" + // gjør det mulig å starte nye containere fra denne containeren.
                        " --env-file ${environmentFile}" +
                        " ${smoketestFrontendImage}"
                )
            } catch (Exception e) {
                notifyOnError("Smoketester for frontend feilet: ${e.getMessage()}, se ${miljotestUrl} for detaljer!")
            }
        }
    }

//    if (fileExists(uuDefinitionFile)) {
//        stage("uu-validator") {
//			sh("docker pull ${uuValidatorImage}")
//			def cmd = "docker run" +
//					" --rm" +
//					" -v /var/run/docker.sock:/var/run/docker.sock" +
//					" -v ${workspace}:/workspace" +
//					" --env-file ${environmentFile}" +
//					" -e DEFINITION_FILE=/workspace/${uuDefinitionFile}" +
//					" ${uuValidatorImage}"
//			println(cmd)
//			sh(cmd)
//        }
//    }

    if (type == "backend") {


        // TODO maven!



    }


}