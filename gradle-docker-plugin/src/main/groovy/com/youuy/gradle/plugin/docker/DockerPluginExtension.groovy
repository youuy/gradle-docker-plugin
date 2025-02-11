package com.youuy.gradle.plugin.docker

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

@CompileStatic
class DockerPluginExtension {
    FromParameter from
    ToParameter to
    ContainerParameter container
    BuildXParameter buildX
    @Inject
    DockerPluginExtension(Project project) {
        ObjectFactory factory = project.getObjects()
        from = factory.newInstance(FromParameter.class)
        to = factory.newInstance(ToParameter.class)
        container = factory.newInstance(ContainerParameter.class)
        buildX = factory.newInstance(BuildXParameter.class)
    }

    public void from(Action<? super FromParameter> action) {
        action.execute(from);
    }

    public void to(Action<? super ToParameter> action) {
        action.execute(to);
    }

    public void container(Action<? super ContainerParameter> action) {
        action.execute(container);
    }
    public void buildX(Action<? super BuildXParameter> action) {
        action.execute(buildX);
    }
}


@CompileStatic
class FromParameter {
    @Input
    @Optional
    Property<String> image

    @Inject
    FromParameter(ObjectFactory objectFactory) {
        image = objectFactory.property(String.class)
        image.set("openjdk:11-jre")
    }
}

@CompileStatic
class ToParameter {
    @Input
    Property<String> image
    @Input
    @Optional
    SetProperty<String> tags
    @Input
    @Optional
    Property<String> context

    @Inject
    ToParameter(ObjectFactory objectFactory) {
        image = objectFactory.property(String.class)
        tags = objectFactory.setProperty(String).empty()
        context = objectFactory.property(String.class)
    }
}

@CompileStatic
class ContainerParameter {
    @Input
    @Optional
    SetProperty<Integer> ports
    @Input
    @Optional
    Property<String> entrypoint

    @Input
    @Optional
    SetProperty<String> javaOpts

    @Input
    @Optional
    Property<Integer> healthCheckPort

    @Inject
    ContainerParameter(ObjectFactory objectFactory) {
        ports = objectFactory.setProperty(Integer).empty()
        ports.set([8080])
        entrypoint = objectFactory.property(String.class)
        entrypoint.set("/entrypoint.sh")
        javaOpts = objectFactory.setProperty(String).empty()
        healthCheckPort = objectFactory.property(Integer.class)
    }
}

@CompileStatic
class BuildXParameter {
    @Input
    @Optional
    Property<Boolean> enabled

    @Input
    @Optional
    Property<Boolean> autoPush

    /**
     * platform list
     *
     * linux/386
     * linux/amd64
     * linux/arm/v6
     * linux/arm/v7
     * linux/arm64/v8
     * linux/ppc64le
     * linux/s390x
     */
    @Input
    @Optional
    SetProperty<String> platforms
    @Inject
    BuildXParameter(ObjectFactory objectFactory) {
        enabled = objectFactory.property(Boolean.class)
        enabled.set(false)
        autoPush = objectFactory.property(Boolean.class)
        autoPush.set(false)
        platforms = objectFactory.setProperty(String).empty()
    }
}