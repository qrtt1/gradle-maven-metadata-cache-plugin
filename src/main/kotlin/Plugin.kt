package org.qrtt1.gradle

import org.eclipse.jetty.server.Server
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*

val logger = LoggerFactory.getLogger("maven-metadata-cache-plugin")!!

object BuildResult : BuildAdapter() {
    override fun buildFinished(result: BuildResult?) {
        logger.info("stop server[${ServerInstance.server}]")
        ServerInstance.server.stop()
    }
}

val port = 10000 + Random().nextInt(1000)

object ServerInstance {
    val repos: Set<RepoistoryInformation> = HashSet()
    val server = Server(port)
}


class MavenCacheRuleSource : RuleSource() {

    @Mutate
    fun patchProjects(tasks: ModelMap<Task>) {

        // when gradle process is kept with daemon
        // we should skip re-patch the project
        if (ServerInstance.server.isStarted) {
            return
        }

        val repos = HashSet<RepoistoryInformation>()
        val projects: Set<Project> = tasks["projects"]!!.project!!.allprojects

        projects.forEach { project ->
            logger.info("patch project: ${project.name}")
            val keep = HashSet<ArtifactRepository>()
            project.repositories.forEach {
                val r = it as DefaultMavenArtifactRepository
                if (r.url!!.scheme!!.contains("^http".toRegex())) {
                    repos.add(RepoistoryInformation(r.name!!, r.url!!.toString()))
                } else {
                    keep.add(it)
                }
            }

            with(project.repositories) {
                clear()
                addAll(keep)
                maven {
                    with(it as MavenArtifactRepository) {
                        name = "local-maven-repo"
                        url = URI("http://127.0.0.1:$port")
                    }
                }
            }
        }

        logger.info("managed repos: $repos")
        ServerInstance.server.handler = GlobalHandler(repos)
        ServerInstance.server.start()
        logger.info("start local-repo-server at port: $port [${ServerInstance.server}]")
        val gradle = projects.first().gradle
        gradle.addBuildListener(BuildResult)
    }
}