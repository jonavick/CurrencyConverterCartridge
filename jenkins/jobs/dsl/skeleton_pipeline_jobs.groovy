//define project folder name
def projectFoldername = "DSL_PROJECT_SIMULATION"

// define jobs
def buildMavenJob = freeStyleJob("${projectFoldername}/Cartridge_Activity1_Build_Maven")
def buildSonarJob = freeStyleJob("${projectFoldername}/Cartridge_Activity2_CodeQuality_Sonarqube")
def buildNexusJob = freeStyleJob("${projectFoldername}/Cartridge_Activity3_Snapshots_Nexus")
def buildAnsibleJob = freeStyleJob("${projectFoldername}/Cartridge_Activity4_Ansible")
def buildSeleniumJob = freeStyleJob("${projectFoldername}/Cartridge_Activity5_FuncTest_Selenium")
def buildReleaseJob = freeStyleJob("${projectFoldername}/Cartridge_Activity6_ReleaseProject_Nexus")

//view
def pipelineView = buildPipelineView("${projectFoldername}/Cartridge_CurrencyConverter_Pipeline")

pipelineView.with{
	title('Cartridge_CurrencyConverter_Pipeline')
	displayedBuilds(3)
	selectedJob("${projectFoldername}/Cartridge_Activity1_Build_Maven")
	showPipelineParameters()
	showPipelineDefinitionHeader()
	refreshFrequency(5)
	}

folder("${projectFoldername}") {
	displayName("${projectFoldername}")
	description("${projectFoldername}")
	}
	
buildMavenJob.with {
    // general
    properties {
        copyArtifactPermissionProperty {
        projectNames('Cartridge_Activity3_Snapshots_Nexus')
        }
    }  

    // source code management  
    scm {
        git {
            remote {
                url('git@gitlab:Orellano/CurrencyConverterSimulationActivity.git')
                credentials('adop-jenkins-master')
            }
        }
    }

    // build triggers
     triggers {
        gitlabPush {
            buildOnMergeRequestEvents(true)
            buildOnPushEvents(true)
            enableCiSkip(false)
            setBuildDescription(false)
            rebuildOpenMergeRequest('never')
        }
    }
    wrappers {
        preBuildCleanup()
    }
  
    // build
    steps {
        maven{
            mavenInstallation('ADOP Maven')
            goals('package')
        }
    }

    // post build actions
     publishers {
        archiveArtifacts {
            pattern('**/*.war')
            onlyIfSuccessful()
        }
        downstream('Cartridge_Activity2_CodeQuality_Sonarqube', 'SUCCESS')
    }
}

buildSonarJob.with {
    // source code management  
    scm {
        git {
            remote {
                url('git@gitlab:Orellano/CurrencyConverterSimulationActivity.git')
                credentials('adop-jenkins-master')
            }
        }
    }

	// build environment
    wrappers {
        preBuildCleanup()
    }
  
    // build

      configure { project ->
    project / 'builders' / 'hudson.plugins.sonar.SonarRunnerBuilder' {
            properties('''sonar.projectKey=SonarActivityTest
      		sonar.projectName=simulationActivity
     		sonar.projectVersion=1.0
            sonar.sources=.''')
            javaOpts()
            jdk('(Inherit From Job)')
            task()
    }
     
    }
    // post build actions
     publishers {
        downstream('Cartridge_Activity3_Snapshots_Nexus', 'SUCCESS')
     }
}	

buildNexusJob.with {
   // general

    // source code management  

    // build triggers
  
    // build
  steps {
      copyArtifacts('Cartridge_Activity1_Build_Maven') {
            includePatterns('target/*.war','*.properties')
            buildSelector {
                latestSuccessful(true)
            }
        }
        nexusArtifactUploader {
        nexusVersion('NEXUS2')
        protocol('HTTP')
        nexusUrl('nexus:8081/nexus')
        groupId('DTSActivity')
        version('1')
        repository('snapshots')
        credentialsId('adop-ldap')
        artifact {
            artifactId('CurrencyConverter')
            type('war')
            file('/var/jenkins_home/jobs/DSL_PROJECT/jobs/Cartridge_Activity3_Snapshots_Nexus/workspace/target/CurrencyConverter.war')
        }
      }
    }
  // post build actions
     publishers {
        archiveArtifacts {
            pattern('**/*.war')
        }
        downstream('Cartridge_Activity4_Ansible', 'SUCCESS')
    }
}

buildAnsibleJob.with {
   //general 
  label('ansible')
    
  // source code management
  scm {
        git {
            remote {
                url('http://gitlab/gitlab/Orellano/Ansible_Activity.git')
                credentials('adop-ldap')
            }
        }
    } 
 
  // build environment
   wrappers {
        preBuildCleanup()
    }
   wrappers {
        colorizeOutput('xterm')
    }
   wrappers {
        sshAgent('adop-jenkins-master')
    }
  
  //build
  steps {
    wrappers {
          sshAgent('adop-jenkins-master')
            credentialsBinding {
              
              usernamePassword('username','password','adop-ldap')
          }
            shell('ansible-playbook -i hosts master.yml -u ec2-user -e "image_version=$BUILD_NUMBER username=$username password=$password"')
      }
    } 
  
  
   //  post build actions
     publishers 
  {
        downstream('Cartridge_Activity5_FuncTest_Selenium', 'SUCCESS')
    }
}

buildSeleniumJob.with {
  // source code management
  scm {
        git {
            remote {
                url('http://gitlab/gitlab/Orellano/SeleniumDTS.git')
                credentials('adop-ldap')
            }
        }
    } 
  
  //build
      steps {
        maven{
            mavenInstallation('ADOP Maven')
            goals('test')
        }
    }
  
   //  post build actions
     publishers 
  {
        downstream('Cartridge_Activity6_ReleaseProject_Nexus', 'SUCCESS')
    }
}

buildReleaseJob.with {
    // general
    properties {
        copyArtifactPermissionProperty {
        projectNames('Cartridge_Activity3_Snapshots_Nexus')
        }
    }  
     
  wrappers {
     preBuildCleanup()
  }
    // build
    steps {
        copyArtifacts('Cartridge_Activity3_Snapshots_Nexus') {
            includePatterns('target/*.war', '*.properties')
            fingerprintArtifacts(true)
            buildSelector {
                latestSuccessful(true)
            }
        }
     
      nexusArtifactUploader {
        nexusVersion('NEXUS2')
        protocol('HTTP')
        nexusUrl('nexus:8081/nexus')
        groupId('DTSActivity')
        version('${BUILD_NUMBER}')
        repository('releases')
        credentialsId('adop-ldap')
        artifact {
            artifactId('CurrencyConverter')
            type('war')
            classifier('')
            file('target/CurrencyConverter.war')
        }
      }
    }
}