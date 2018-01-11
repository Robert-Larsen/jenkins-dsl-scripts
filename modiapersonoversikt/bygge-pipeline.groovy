import java.text.SimpleDateFormat

pusDockerImagePrefiks = "docker.adeo.no:5000/pus/"

// https://github.com/navikt/pus-toolbox
toolboxDockerImage = "${pusDockerImagePrefiks}toolbox"

// https://github.com/navikt/pus-notifier
notifierDockerImage = "${pusDockerImagePrefiks}notifier"

// https://github.com/navikt/pus-policy-validator
policyDockerImage = "${pusDockerImagePrefiks}policy-validator"

miljo = "t6"

environmentFile = "__build-environment__"

def isMasterBranch(String branch) {
    return branch == "master"
}

def isNotMasterBranch(branch) {
    return !isMasterBranch(branch)
}

// NB: må bruke https mot github pga brannmur
def httpsGithubCom = "https://github.com"
if (gitUrl.contains(httpsGithubCom)) {
    gitUrl = gitUrl.replace(httpsGithubCom, "https://${GITHUB_USERNAME}:${GITHUB_PASSWORD}@github.com")
}

gitCommitHash = null
versjon = null

def mvnCommand(command) {
    sh("docker pull ${toolboxDockerImage}")
    sh("docker run" +
            " --rm" + // slett container etter kjøring
            " -v ${workspace}:/workspace" + // map inn workspace
            " -w /workspace" + // sett working directory til workspace
            " -v ~/.m2/repository:/root/.m2/repository" + // map inn maven cache
            " --env-file ${environmentFile}" +
            " ${toolboxDockerImage}" +
            " ${command}"
    )
}

def addToDescription(message) {
    echo "adding to description : ${message}"
    def description = currentBuild.description ?: ""
    currentBuild.description = description != null && description.trim().length() > 0 ? "${description}<br>${message}" : message
}

def status(statusCode) {
    if (gitCommitHash != null) {
        sh("docker pull ${notifierDockerImage}")
        sh("docker run" +
                " --rm" +  // slett container etter kjøring
                " --env-file ${environmentFile}" +
                " -e status=${statusCode}" +
                " ${notifierDockerImage}"
        )
    }
}

node("docker") {
    nodeName = env.NODE_NAME
    echo "running on ${nodeName}"

    try {

        stage("cleanup") {
            // docker-containere kan potensielt legge igjen filer eid av root i workspace
            // trenger å slette disse som root
            // samtidig er det et poeng å slette node_modules slik at vi får mer konsistente bygg
            sh("docker run" +
                    " --rm" + // slett container etter kjøring
                    " -v ${workspace}:/workspace" + // map inn workspace
                    " ${toolboxDockerImage}" +
                    " chmod -R 777 /workspace"
            )
            deleteDir()
        }

        stage("checkout") {
            sh "git clone -b ${branch} ${gitUrl} ."

            def gitCommitNumber = sh(returnStdout: true, script: "git rev-list --count HEAD").trim()
            gitCommitHash = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
            def gitCommitHashShort = gitCommitHash.substring(0, 8)
            addToDescription("Commit: ${gitCommitHashShort}")

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
gitCommitHash=${gitCommitHash}
"""
            println(environment)
            writeFile([
                    file: environmentFile,
                    text: environment
            ])
            status("pending")

            if (isNotMasterBranch(branch)) {

                stage("policy-sjekk") {
                    sh("docker pull ${policyDockerImage}")
                    sh("docker run" +
                            " --rm" +  // slett container etter kjøring
                            " -v ${workspace}:/workspace" + // map inn workspace
                            " -w /workspace" + // sett working directory til workspace
                            " ${policyDockerImage}"
                    )
                }
                stage("git merge master") {
                    sh "git merge origin/master"
                }
            }

            sdf = new SimpleDateFormat("yyyyMMdd.Hmm")
            versjon = "${gitCommitNumber}.${sdf.format(new Date())}"

            echo "Build version: ${versjon}"
            addToDescription("Version: ${versjon}")
        }

        stage("mvn versions:set") {
            // mest for debugging
            mvnCommand("mvn -v")

            mvnCommand("mvn versions:set --batch-mode -DnewVersion=${versjon}")
        }

        stage("mvn package") {
            mvnCommand("mvn clean package dependency:tree help:effective-pom --batch-mode -U")
        }

//        stage("mvn sonar") {
//            withSonarQubeEnv("SBL sonar") {
//                mvnCommand("mvn ${SONAR_MAVEN_GOAL} --batch-mode -Dsonar.host.url=${SONAR_HOST_URL}")
//            }
//        }

        stage("mvn deploy") {
            mvnCommand("mvn deploy --batch-mode -DskipTests")
        }

        stage("git tag") {
            sh "git tag -a ${versjon} -m ${versjon} HEAD"
            sh "git push --tags"
        }

        status("ok")
    } catch (Throwable t) {
        status("error")
        throw t
    }
}