package com.youuy.gradle.plugin.docker;

import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.Manifest

class DockerPlugin implements Plugin<Project> {
    private final static String EXTENSION_DOCKER = "docker"
    private final static String TASK_BUILD_IMAGE = "dockerBuild"
    private final static String TASK_PUSH_IMAGE = "dockerPush"
    private final static String DEFAULT_CONTEXT = "docker"
    @Override
    void apply(Project project) {
        def extension = project.extensions.create(EXTENSION_DOCKER, DockerPluginExtension.class, project)
        project.task(TASK_BUILD_IMAGE) {
            group = EXTENSION_DOCKER
            dependsOn project.tasks.getByName("bootJar")
            doLast {
                File tmp;
                try {
                    String context
                    //user setting's folder
                    String userSettingContext = extension.to.context.getOrNull()
                    boolean needGen = false
                    if (userSettingContext){
                        if (Files.exists(Path.of(userSettingContext))){
                            if (!Files.isDirectory(Path.of(userSettingContext))){
                                throw new RuntimeException("The Parameter 'docker.to.context' is not a directory.")
                            }else{
                                logger.info("用户定义工作目录为：$userSettingContext")
                                context = userSettingContext
                                needGen = false
                            }
                        } else{
                            logger.info("用户定义工作目录为：$userSettingContext ，但该目录不存在，自动生成文件模板")
                            context = userSettingContext
                            needGen = true
                        }
                    }else{
                        //user settings not defined

                        //sub project setting
                        String subProjectContext = Path.of(project.projectDir.getPath(), DEFAULT_CONTEXT)
                        //root project setting
                        String rootProjectContext = Path.of(project.rootDir.getPath(), DEFAULT_CONTEXT)
                        if (Files.exists(Path.of(subProjectContext))) {
                            if (Files.isDirectory(Path.of(subProjectContext))){
                                //use subProjectContext
                                context = subProjectContext
                                needGen = false
                            }else{
                                throw new RuntimeException("The Parameter '$subProjectContext' is not a directory.")
                            }
                        }else if (Files.exists(Path.of(rootProjectContext))){
                            if (Files.isDirectory(Path.of(rootProjectContext))){
                                //use rootProjectContext
                                context = rootProjectContext
                                needGen = false
                            }else{
                                throw new RuntimeException("The Parameter '$rootProjectContext' is not a directory.")
                            }
                        }else{
                            //use subProjectContext and gen
                            context = subProjectContext
                            needGen = true
                        }
                    }
                    if (needGen){
                        logger.warn("generate a new docker folder in $context")
                        File contextFolder = new File(context)
                        contextFolder.mkdirs()
                        File dockerfile = new File(contextFolder, "Dockerfile")
                        dockerfile.write(generateDockerfile())
                        File entrypoint = new File(contextFolder, "entrypoint.sh")
                        entrypoint.write(generateEntrypoint())
                    }
                    //check mode
                    def buildXEnabled = extension.buildX.enabled.getOrElse(false)

                    //build
                    //1. delete last
                    tmp = new File(context, ".tmp")
                    if (tmp.isDirectory()){
                        throw new RuntimeException("There are currently tasks in progress...")
                    }
                    tmp.mkdirs()
                    File unjar = new File(project.buildDir, "unjar")
                    project.delete(unjar)
                    unjar.mkdirs()
                    //2.unjar jar
                    project.exec {
                        workingDir unjar
                        executable "jar"
                        args "-xf", "../libs/${project.name}${project.version ? "-${project.version}" : ""}.jar"
                    }
                    //3.copy unjar to context
                    project.copy {
                        from unjar
                        include "BOOT-INF/**", "META-INF/**"
                        into tmp.getPath()
                    }
                    //4. setup Start-Class and JAVA_OPTS
                    Manifest manifest = new Manifest(Files.newInputStream(new File(unjar, "META-INF/MANIFEST.MF").toPath()))
                    Attributes attr = manifest.getMainAttributes()
                    String startClass = attr.getValue("Start-Class")
                    File startClassFile = new File(tmp, "START_CLASS")
                    startClassFile.write(startClass)

                    def javaOpts = extension.container.javaOpts.getOrElse(Collections.emptySet())
                    File javaOptsFile = new File(tmp, "JAVA_OPTS")
                    javaOptsFile.write(javaOpts.join(" "))

                    //5. build
                    if (!extension.to.image){
                        throw new RuntimeException("The Parameter 'to.image' cannot be null")
                    }
                    project.exec {
                        workingDir context
                        def tags = extension.to.tags.getOrNull()
                        def commandArgs = []

                        if (buildXEnabled){
                            commandArgs << "buildx"
                            commandArgs << "build"

                            def buildXAutoPush = extension.buildX.autoPush.getOrElse(false)
                            if (buildXAutoPush){
                                commandArgs << "--push"
                            }
                            def platforms = extension.buildX.platforms.getOrNull()
                            if (platforms?.size()> 0){
                                platforms?.each {platform->
                                    commandArgs << "--platform=${platform}"
                                }
                            }
                        }else{
                            commandArgs << "build"
                        }
                        if (tags?.size() > 0) {
                            tags.each {tag ->
                                commandArgs << "-t"
                                commandArgs << "${extension.to.image.get()}:${tag}"
                            }
                        }
                        commandArgs << "-t"
                        commandArgs << "${extension.to.image.get()}:${project.version}"

                        commandArgs << "-t"
                        commandArgs << "${extension.to.image.get()}:latest"

                        def baseImage = extension.from.image.getOrNull()
                        if (baseImage){
                            commandArgs << "--build-arg"
                            commandArgs << "BASE_IMAGE=${baseImage}"
                        }
                        def healthCheckPort = extension.container.healthCheckPort.getOrNull()
                        def ports = extension.container.ports.getOrNull()
                        if (ports?.size() > 0) {
                            String portsStr = ""
                            ports.each {item ->
                                if (portsStr.length() > 0){
                                    portsStr += " "
                                }
                                portsStr += String.valueOf(item)
                            }
                            commandArgs << "--build-arg"
                            commandArgs << "PORT=${portsStr}"

                            if (!healthCheckPort){
                                healthCheckPort = ports[0].toString()
                            }
                        }
                        if (healthCheckPort){
                            commandArgs << "--build-arg"
                            commandArgs << "HEALTHCHECK_PORT=${healthCheckPort}"
                        }

                        commandArgs << "--no-cache"
                        commandArgs << "."
                        executable "docker"
                        println(commandArgs)
                        args commandArgs
                    }
                }catch(e){
                    e.printStackTrace()

                }finally{
                    if (tmp){
                        project.delete(tmp)
                    }
                }
            }
        }

        project.task(TASK_PUSH_IMAGE) {
            group = EXTENSION_DOCKER
            doLast {
                project.exec {
                    executable "docker"
                    args "push", "-a", extension.to.image.get()
                }
            }
        }
    }

    String generateDockerfile(){
        return """ARG BASE_IMAGE=openjdk:11-jre
FROM \$BASE_IMAGE
ARG PORT=8080
ARG HEALTHCHECK_PORT=8080
ENV HEALTHCHECK_PORT=\${HEALTHCHECK_PORT}
EXPOSE \$PORT
WORKDIR /app
COPY entrypoint.sh /app/entrypoint.sh
COPY .tmp/START_CLASS /app/START_CLASS
COPY .tmp/JAVA_OPTS /app/JAVA_OPTS
COPY .tmp/BOOT-INF/lib /app/lib
COPY .tmp/META-INF /app/META-INF
COPY .tmp/BOOT-INF/classes /app
RUN chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
HEALTHCHECK --start-period=10s --interval=10s --timeout=3s --retries=5 \\
            CMD curl -m 5 --silent --fail --request GET http://localhost:\$HEALTHCHECK_PORT/actuator/health \\
            | jq --exit-status -n 'inputs | if has("status") then .status=="UP" else false end' > /dev/null || exit 1
"""
    }
    String generateEntrypoint(){
        return """#!/bin/sh
# check SPRING_PROFILES_ACTIVE environment variable
[ -z "\$SPRING_PROFILES_ACTIVE" ] && echo "Error: Define SPRING_PROFILES_ACTIVE environment variable" && exit 1;
# setup main class
export START_CLASS=`cat /app/START_CLASS`
export JAVA_OPTS=`cat /app/JAVA_OPTS`
# startup
java -XX:+UseContainerSupport \\
-XX:InitialRAMPercentage=50 \\
-XX:MaxRAMPercentage=85 \\
-XX:+UnlockExperimentalVMOptions \\
-Djava.security.egd=file:/dev/./urandom \\
-Dlog4j2.formatMsgNoLookups=true \\
--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED \\
--add-opens=java.base/java.net=ALL-UNNAMED \\
\$JAVA_OPTS \\
-cp /app:/app/lib/* \$START_CLASS
"""
    }

}