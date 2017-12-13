# jenkins-dsl-scripts

scripts som bruker jenkins job-dsl (se https://jenkinsci.github.io/job-dsl-plugin/) til å opprette byggejobber på http://bekkci.devillo.no/

På denne måten får vi definert jobber ut fra kode sjekket inn i git.

## struktur

Hvert team/prosjekt eier hver sin mappe i dette git-repoet, fo eier f.eks. forenklet_oppfolging.
Unntaket er _jenkins_, hvor felles oppsett for selve byggserveren ligger

### pus
Endringer gjøres i http://stash.devillo.no/projects/BEKKCI/repos/jenkins-dsl-scripts/browse/pus/config.json

### forenklet oppfølging
Endringer gjøres i http://stash.devillo.no/projects/BEKKCI/repos/jenkins-dsl-scripts/browse/forenklet_oppfolging/config.json
