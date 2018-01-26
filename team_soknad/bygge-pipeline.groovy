import java.text.SimpleDateFormat

soknadBuilderImage = "docker.adeo.no:5000/soknad/soknad-builder:1.0.0"

miljo = "t6"
pomXml = "pom.xml"
versionFile = "VERSION"
environmentFile = "__build-environment__"

gitCommitHash = null
versjon = null

def addToDescription(message) {
    echo "adding to description : ${message}"
    def description = currentBuild.description ?: ""
    currentBuild.description = description != null && description.trim().length() > 0 ? "${description}<br>${message}" : message
}


def getVersjon() {
    if (fileExists(pomXml)) {
        def pom = readMavenPom file: pomXml
        def pomVersjon = pom.version
        originalVersion = pomVersjon.startsWith("\$") ? "1" : pomVersjon
        def sdf = new SimpleDateFormat("yyMMdd.Hmmss")
        return originalVersion.replace("-SNAPSHOT", "") + ".${sdf.format(new Date())}"
    }
    else if(fileExists(versionFile)) {
       return readFile(versionFile)
    }
}

def getJSON(String url) {
    return new groovy.json.JsonSlurper().parse(new URL(url))
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
                    " ${soknadBuilderImage}" +
                    " chmod -R 777 /workspace"
            )
            deleteDir()
        }

        stage("checkout") {
            sh "git clone -b ${branch} ${gitUrl} ."

            gitCommitHash = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
            def gitCommitHashShort = gitCommitHash.substring(0, 8)
            addToDescription("Commit: ${gitCommitHashShort}")
            versjon = getVersjon()

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
versjon=${versjon}
"""
            println(environment)
            writeFile([
                    file: environmentFile,
                    text: environment
            ])


            echo "Build version: ${versjon}"
            addToDescription("Version: ${versjon}")
        }

        stage('innheholder prodversjon') {
            def deployLogMedKunSisteDeploy = getJSON("https://vera.adeo.no/api/v1/deploylog?application=${applikasjonsNavn}&environment=p&onlyLatest=true")
            echo "Deploylog: ${deployLogMedKunSisteDeploy}"
            if (!deployLogMedKunSisteDeploy.isEmpty()) {
                def sisteDeploy = deployLogMedKunSisteDeploy[0]
                def sisteprodversjon = sisteDeploy.version
                echo "Siste sisteprodversjon: ${sisteprodversjon}"
                try {
                    this.shell "git merge-base --is-ancestor ${sisteprodversjon} HEAD"
                } catch (Exception e) {
                    echo "tag ${sisteprodversjon} finnes ikke som felles ancestor, tester ${applikasjonsNavn}-${sisteprodversjon}"
                    this.shell "git merge-base --is-ancestor ${applikasjonsNavn}-${sisteprodversjon} HEAD"
                }
            } else {
                echo "Finnes ingen proddeploy for ${applikasjonsNavn} - går videre"
            }
        }


        stage("start builder - kjør build.sh") {
            sh("docker run" +
                    " --rm" +
                    " --name ${applikasjonsNavn}_${branch.replace("/", "_")}" +
                    " --volume ${workspace}:/workspace/" +
                    " --volume /var/run/docker.sock:/var/run/docker.sock" + // gjør det mulig åstarte nye containere fra denne containeren.
                    " --workdir /workspace" +
                    " --env-file ${environmentFile}" +
                    " docker.adeo.no:5000/soknad/soknad-builder:1.1.0" +
                    " ./build.sh"
            )
        }

        stage("git tag") {
            def tagExists = sh(script: "git tag --list | grep ${versjon} | tr -d '\n'", returnStdout: true)
            if(!tagExists.equals(versjon)) {
                sh "git tag -a ${versjon} -m ${versjon} HEAD"
                sh "git push --tags"
            }
        }

    } catch (Throwable t) {
        throw t
    }
}