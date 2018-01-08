environmentFile = "__build-environment__"
pusDockerImagePrefiks = "docker.adeo.no:5000/pus"
smoketestFrontendImage = "${pusDockerImagePrefiks}smoketest-frontend"
definitionFile = "uu-definisjon.js"

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"
	
	stage("cleanup") {
		deleteDir()
	}
	
	stage("checkout") {
		sh "git clone -b ${branch} ${gitUrl} ."
	}


    stage("setup") {
def environment = """
NEXUS_USERNAME=${NEXUS_USERNAME}
NEXUS_PASSWORD=${NEXUS_PASSWORD}
miljo=${miljo}
testmiljo=${miljo}
TESTMILJO=${miljo}
domenebrukernavn=${domenebrukernavn}
domenepassord=${domenepassord}
DOMENEBRUKER=${domenebrukernavn}
DOMENEPASSORD=${domenepassord}
sone=${sone}
http_proxy=${http_proxy}
https_proxy=${https_proxy}
no_proxy=${no_proxy}
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
			sh("docker pull ${pusDockerImagePrefiks}/uu-validator")
			def cmd = "docker run" +
					" --rm" + 
					" --privileged" + 
					" -v /var/run/docker.sock:/var/run/docker.sock" +
					" -v ${workspace}:/workspace" +
					" -v /dev/shm:/dev/shm" +
					" --env-file ${environmentFile}" +
					" -e DEFINITION_FILE=/workspace/${definitionFile}" +
					" ${pusDockerImagePrefiks}/uu-validator"
			println(cmd)
			sh(cmd)
        }

    }

    if (type == "backend") {


        // TODO maven!



    }


}