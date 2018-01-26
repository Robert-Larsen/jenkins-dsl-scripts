pusDockerImagePrefiks = "docker.adeo.no:5000/pus/"
dockerImage = "${pusDockerImagePrefiks}maven"
notifierDockerImage = "${pusDockerImagePrefiks}notifier"
testmiljo = "t6"

gitCommitHash = null

def status(statusCode) {
    if (gitCommitHash != null) {
        sh("docker pull ${notifierDockerImage}")
        sh("docker run" +
                " --rm" +  // slett container etter kjøring
                " -e domenebrukernavn" +
                " -e domenepassord" +
                " -e gitCommitHash=${gitCommitHash}" +
                " -e gitUrl=${gitUrl}" +
                " -e buildUrl=${BUILD_URL}" +
                " -e status=${statusCode}" +
                " ${notifierDockerImage}"
        )
    }
}

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"
    try {
        stage("checkout") {
            checkout([
                    $class           : "GitSCM",
                    branches         : [[name: branch]],
                    userRemoteConfigs: [[url: gitUrl]],
                    extensions       : [
                            [
                                    $class: "PruneStaleBranch"
                            ]
                    ]
            ])
            gitCommitHash = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
        }

        status("pending")

        stage("mvn package") {
            sh("docker pull ${dockerImage}")
            sh("docker run" +
                    " --rm" + // slett container etter kjøring
                    " -v ${workspace}:/workspace" + // map inn workspace
                    " -w /workspace" + // sett working directory til workspace
                    " -v ~/.m2/repository:/root/.m2/repository" + // map inn maven cache
                    " -e domenebrukernavn" +
                    " -e domenepassord" +
                    " -e NEXUS_USERNAME" +
                    " -e NEXUS_PASSWORD" +
                    " -e testmiljo=${testmiljo}" +
                    " ${dockerImage}" +
                    " mvn deploy --batch-mode"
            )
        }

        status("ok")
    } catch (Throwable t) {
        status("error")
        throw t
    }

}