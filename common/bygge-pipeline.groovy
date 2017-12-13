dockerImage = "docker.adeo.no:5000/pus/maven"
testmiljo="q6"

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"

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
    }

    stage("mvn package") {
        sh("docker pull ${dockerImage}")
        sh("docker run" +
                " --rm" + // slett container etter kjøring
                " -v ${workspace}:/workspace" + // map inn workspace
                " -w /workspace" + // sett working directory til workspace
                " -v ~/.m2/repository:/root/.m2/repository" + // map inn maven cache
                " -e domenebrukernavn" +
                " -e domenepassord" +
                " -e testmiljo=${testmiljo}" +
                " ${dockerImage}" +
                " mvn deploy --batch-mode"
        )
    }

}