dockerImage="docker.adeo.no:5000/pus/toolbox"
gitUrl="ssh://git@stash.devillo.no:7999/common/release.git"
testmiljo="t6"

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"

    stage("cleanup") {
        // docker-containere kan potensielt legge igjen filer eid av root i workspace
        // trenger å slette disse som root
        sh("docker run" +
                " --rm" + // slett container etter kjøring
                " -v ${workspace}:/workspace" + // map inn workspace
                " ${dockerImage}" +
                " chmod -R 777 /workspace"
        )
        deleteDir()
    }

    stage("checkout") {
        checkout([
                $class           : "GitSCM",
                userRemoteConfigs: [[url: gitUrl]],
                extensions       : [
                        [
                                $class: "PruneStaleBranch"
                        ]
                ]
        ])
    }

    stage("build.groovy") {
        sh("docker pull ${dockerImage}")
        sh("docker run" +
                " --rm" + // slett container etter kjøring
                " -v ${workspace}:/workspace" + // map inn workspace
                " -w /workspace" + // sett working directory til workspace
                " -v ~/.m2/repository:/root/.m2/repository" + // map inn maven cache
                " -v ~/.gitconfig:/root/.gitconfig" + // slik at vi kan tagge
                " -v ~/.ssh:/root/.ssh" + // slik at vi kan tagge
                " -e NEXUS_USERNAME" +
                " -e NEXUS_PASSWORD" +
                " -e domenebrukernavn" +
                " -e domenepassord" +
                " -e testmiljo=${testmiljo}" +
                " ${dockerImage}" +
                " groovy build.groovy"
        )
    }

}