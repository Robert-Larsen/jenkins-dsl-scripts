import java.text.SimpleDateFormat
baseImagePrefix = "docker.adeo.no:5000/pus/"
deployDockerImage = "${baseImagePrefix}deploy"

def versjon

def shell(cmd) {
    echo cmd
    try {
        return sh(cmd)
    } catch (Exception e) {
        echo "shell-kommando feilet!"
        echo e.getMessage()
        throw e
    }
}

def addToDescription(message) {
    echo "adding to description : ${message}"
    def description = currentBuild.description ?: ""
    currentBuild.description = description != null && description.trim().length() > 0 ? "${description}<br>${message}" : message
}

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"

    stage("checkout") {
        addToDescription("Branch: ${branch}")

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

        def gitCommitNumber = sh(returnStdout: true, script: "git rev-list --count HEAD").trim()
        def gitCommitHash = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
        def gitCommitHashShort = gitCommitHash.substring(0, 8)
        addToDescription("Commit: ${gitCommitHashShort}")

        sdf = new SimpleDateFormat("yyyyMMdd.HHmm")
        versjon = "${gitCommitNumber}.${sdf.format(new Date())}"

        echo "Build version: ${versjon}"
        addToDescription("Version: ${versjon}")
    }

    if (fileExists("pom.xml")) {
        stage("mvn install") {
            sh("docker run" +
                    " --rm" + // slett container etter kjøring
                    " -v ${workspace}:/workspace" + // map inn workspace
                    " -w /workspace" + // sett working directory til workspace
                    " -v ~/.m2/repository:/root/.m2/repository" + // map inn maven cache
                    " -e domenebrukernavn" +
                    " -e domenepassord" +
                    " -e testmiljo=t6" +
                    " ${baseImagePrefix}maven" +
                    " mvn install --batch-mode"
            )
        }
    }

    def uversjonertTag = "docker.adeo.no:5000/pus/${applikasjonsNavn}"
    def versjonertTag = "${uversjonertTag}:${versjon}"

    stage("docker build") {
        shell("docker build" +
                " --build-arg BASE_IMAGE_PREFIX=${baseImagePrefix}" +
                " -t ${uversjonertTag}" +
                " -t ${versjonertTag}" +
                " .")
    }

    stage("docker push") {
        shell("docker push ${versjonertTag}")
        shell("docker push ${uversjonertTag}")
    }


    if (fileExists("app-config.yaml")) {

        stage("nais deploy") {
            sh("docker pull ${deployDockerImage}")
            sh("docker run" +
                    " --rm" +  // slett container etter kjøring
                    " -v ${workspace}:/workspace" + // map inn workspace
                    " -w /workspace" + // sett working directory
                    " -e domenebrukernavn" +
                    " -e domenepassord" +
                    " -e plattform=nais" +
                    " -e sone=fss" +
                    " -e versjon=${versjon}" +
                    " -e applikasjonsNavn=${applikasjonsNavn}" +
                    " -e miljo=t" +
                    " ${deployDockerImage}"
            )
        }
    }

}