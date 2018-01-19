

node{

    stage("backup") {

        deleteDir()

        checkout([
                $class           : "GitSCM",
                userRemoteConfigs: [
                        [url: gitUrl],
                        [url: "ssh://git@stash.devillo.no:7999/fa/${applikasjonsNavn}.git", name: "backup"]
                ]
        ])

        sh "git checkout origin/master"
        sh "git checkout -b master"
        sh "git push -f -u backup master"
        sh "git push backup --tags"
    }

}