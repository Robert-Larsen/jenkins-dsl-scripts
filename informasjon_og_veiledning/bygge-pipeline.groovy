package informasjon_og_veiledning

import java.text.SimpleDateFormat

pusDockerImagePrefiks = "docker.adeo.no:5000/pus/"
foMavenDockerImage = "${pusDockerImagePrefiks}maven"
foNodeDockerImage = "${pusDockerImagePrefiks}node"
notifierDockerImage = "${pusDockerImagePrefiks}notifier"
policyDockerImage = "${pusDockerImagePrefiks}policy-validator"
sblDockerImagePrefiks = "docker.adeo.no:5000/sbl/"

miljo = "t1"

appConfig = "app-config.yaml"
dockerfile = "Dockerfile"
packageJson = "package.json"
pomXml = "pom.xml"
environmentFile = "__build-environment__"

def isMasterBranch(String branch) {
    return branch == "master"
}

def isNotMasterBranch(branch) {
    return !isMasterBranch(branch)
}

frontendDirectory = null
gitCommitHash = null
versjon = null

def npmCommand(command) {
    sh("docker pull ${foNodeDockerImage}")
    sh("docker run" +
            " --rm" + // slett container etter kjøring
            " -v ${workspace}:/workspace" + // map inn workspace
            " -w /workspace/${frontendDirectory}" + // sett working directory til workspace + frontendDirectory
            " ${foNodeDockerImage}" +
            " ${command}"
    )
}

def mvnCommand(command) {
    sh("docker pull ${foMavenDockerImage}")
    sh("docker run" +
            " --rm" + // slett container etter kjøring
            " -v ${workspace}:/workspace" + // map inn workspace
            " -w /workspace" + // sett working directory til workspace
            " -v ~/.m2/repository:/root/.m2/repository" + // map inn maven cache
            " --env-file ${environmentFile}" +
            " ${foMavenDockerImage}" +
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
                    " ${foMavenDockerImage}" +
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

            sdf = new SimpleDateFormat("yyyyMMdd.HHmm")
            versjon = "${gitCommitNumber}.${sdf.format(new Date())}"

            echo "Build version: ${versjon}"
            addToDescription("Version: ${versjon}")
        }

        if (fileExists(packageJson)) {
            frontendDirectory = "."
        } else if (fileExists("web/src/frontend/${packageJson}")) {
            frontendDirectory = "web/src/frontend/"
        } else if (fileExists("src/frontend/${packageJson}")) {
            frontendDirectory = "src/frontend/"
        }

        if (frontendDirectory != null) {
            dir(frontendDirectory) {

                stage("npm version") {
                    // mest for debugging
                    npmCommand("npm -v")

                    npmCommand("npm version --no-git-tag-version ${versjon}")
                }

                stage("npm install") {
                    npmCommand("npm install")
                }

                // Brukes av to grunner:
                // 1) Logge hvilke versjoner som er brukt
                // 2) Validerer at treet er gyldig
                stage("npm ls") {
                    npmCommand("npm ls")
                }

                stage("npm run lint") {
                    npmCommand("npm run lint")
                }

                stage("npm run test:coverage") {
                    npmCommand("npm run test:coverage")
                }

                stage("npm run build") {
                    npmCommand("npm run build")
                }

            }
        }

        if (fileExists(pomXml)) {

            stage("mvn versions:set") {
                // mest for debugging
                mvnCommand("mvn -v")

                mvnCommand("mvn versions:set --batch-mode -DnewVersion=${versjon}")
            }

            stage("mvn package") {
                mvnCommand("mvn clean package dependency:tree help:effective-pom --batch-mode -U")
            }

            stage("mvn sonar") {
                withSonarQubeEnv("SBL sonar") {
                    mvnCommand("mvn ${SONAR_MAVEN_GOAL} --batch-mode -Dsonar.host.url=${SONAR_HOST_URL}")
                }
            }

            stage("mvn deploy") {
                mvnCommand("mvn deploy --batch-mode -DskipTests")
            }
        }

        stage("git tag") {
            sh "git tag -a ${versjon} -m ${versjon} HEAD"
            sh "git push --tags"
        }

        if (fileExists(dockerfile) && isMasterBranch(branch)) {
            def uversjonertTag = "${sblDockerImagePrefiks}${applikasjonsNavn}"
            def versjonertTag = "${uversjonertTag}:${versjon}"

            stage("docker build") {
                sh("docker build" +
                        " --no-cache" +
                        " --pull" + // alltid bruk siste versjon av baseimages
                        " --build-arg BASE_IMAGE_PREFIX=${pusDockerImagePrefiks}" +
                        " -t ${uversjonertTag}" +
                        " -t ${versjonertTag}" +
                        " .")
            }

            stage("docker push") {
                sh("docker push ${versjonertTag}")
                sh("docker push ${uversjonertTag}")
            }

            if (fileExists(appConfig)) {

                stage("nais deploy ${miljo}") {

                    mvnCommand("mvn deploy:deploy-file " +
                            " -DgroupId=nais" +
                            " -DartifactId=${applikasjonsNavn}" +
                            " -Dversion=${versjon}" +
                            " -Dtype=yaml" +
                            " -Dfile=app-config.yaml" +
                            " -DrepositoryId=m2internal" +
                            " -Durl=http://maven.adeo.no/nexus/content/repositories/m2internal"
                    )

                    sh("docker run" +
                            " --rm" +  // slett container etter kjøring
                            " --env-file ${environmentFile}" +
                            " -e plattform=nais" +
                            " -e versjon=${versjon}" +
                            " ${pusDockerImagePrefiks}deploy"
                    )
                }
            }
        }

        def appConfig1 = "src/main/resources/app-config.xml"
        def appConfig2 = "config/${appConfig1}"
        if ((fileExists(appConfig1) || fileExists(appConfig2)) && isMasterBranch(branch)) {

            stage("skya deploy ${miljo}") {

                sh("docker run" +
                        " --rm" +  // slett container etter kjøring
                        " --env-file ${environmentFile}" +
                        " -e plattform=skya" +
                        " -e versjon=${versjon}" +
                        " ${pusDockerImagePrefiks}deploy"
                )
            }
        }

        status("ok")
    } catch (Throwable t) {
        status("error")
        throw t
    }
}