def jenkinsMappe = this.SCRIPT_FOLDER
def vaskeImage = "docker.adeo.no:5000/bekkci/jenkins-vask"
def jenk = jenkins.model.Jenkins.instance
jenk.computers.each {

    def nodeName = it.nodeName
    job("${jenkinsMappe}/${nodeName}") {

        label(nodeName)

        triggers{
            cron("H * * * *")
        }

        steps {
            shell(
"""
docker pull ${vaskeImage}
docker run -v /var/run/docker.sock:/var/run/docker.sock ${vaskeImage}
""")
        }
    }
}