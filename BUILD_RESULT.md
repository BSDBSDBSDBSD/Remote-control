# Build Result
Date: Tue Sep  8 09:14:54 UTC 2026

## ❌ FAILED - No APK found

### Last 60 lines of build log:
```
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.LogToClient.doBuild(LogToClient.java:63)
	at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:37)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.EstablishBuildEnvironment.doBuild(EstablishBuildEnvironment.java:84)
	at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:37)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.StartBuildOrRespondWithBusy$1.run(StartBuildOrRespondWithBusy.java:52)
	at org.gradle.launcher.daemon.server.DaemonStateCoordinator$1.run(DaemonStateCoordinator.java:297)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.gradle.api.InvalidUserCodeException: Build was configured to prefer settings repositories over project repositories but repository 'Google' was added by build file 'app/build.gradle'
	at org.gradle.internal.management.DefaultDependencyResolutionManagement.repoMutationDisallowedOnProject(DefaultDependencyResolutionManagement.java:199)
	at org.gradle.internal.ImmutableActionSet$SetWithFewActions.execute(ImmutableActionSet.java:285)
	at org.gradle.api.internal.DefaultDomainObjectCollection.doAdd(DefaultDomainObjectCollection.java:262)
	at org.gradle.api.internal.DefaultNamedDomainObjectCollection.doAdd(DefaultNamedDomainObjectCollection.java:113)
	at org.gradle.api.internal.DefaultDomainObjectCollection.add(DefaultDomainObjectCollection.java:251)
	at org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.access$101(DefaultArtifactRepositoryContainer.java:35)
	at org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.lambda$new$0(DefaultArtifactRepositoryContainer.java:38)
	at org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.addWithUniqueName(DefaultArtifactRepositoryContainer.java:101)
	at org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.addRepository(DefaultArtifactRepositoryContainer.java:89)
	at org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.addRepository(DefaultArtifactRepositoryContainer.java:84)
	at org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.google(DefaultRepositoryHandler.java:151)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at org.gradle.internal.metaobject.BeanDynamicObject$MetaClassAdapter.invokeMethod(BeanDynamicObject.java:489)
	at org.gradle.internal.metaobject.BeanDynamicObject.tryInvokeMethod(BeanDynamicObject.java:196)
	at org.gradle.internal.metaobject.CompositeDynamicObject.tryInvokeMethod(CompositeDynamicObject.java:98)
	at org.gradle.internal.extensibility.MixInClosurePropertiesAsMethodsDynamicObject.tryInvokeMethod(MixInClosurePropertiesAsMethodsDynamicObject.java:36)
	at org.gradle.internal.metaobject.ConfigureDelegate.invokeMethod(ConfigureDelegate.java:61)
	at build_2wjjv8ph1k2ugmy4g2py43ts5$_run_closure2.doCall(/home/runner/work/Remote-control/Remote-control/app/build.gradle:32)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at org.gradle.util.internal.ClosureBackedAction.execute(ClosureBackedAction.java:73)
	at org.gradle.util.internal.ConfigureUtil.configureTarget(ConfigureUtil.java:155)
	at org.gradle.util.internal.ConfigureUtil.configureSelf(ConfigureUtil.java:131)
	at org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.configure(DefaultArtifactRepositoryContainer.java:65)
	at org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.configure(DefaultArtifactRepositoryContainer.java:35)
	at org.gradle.util.internal.ConfigureUtil.configure(ConfigureUtil.java:104)
	at org.gradle.api.internal.project.DefaultProject.repositories(DefaultProject.java:1289)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at org.gradle.internal.metaobject.BeanDynamicObject$MetaClassAdapter.invokeMethod(BeanDynamicObject.java:489)
	at org.gradle.internal.metaobject.BeanDynamicObject.tryInvokeMethod(BeanDynamicObject.java:196)
	at org.gradle.internal.metaobject.CompositeDynamicObject.tryInvokeMethod(CompositeDynamicObject.java:98)
	at org.gradle.internal.extensibility.MixInClosurePropertiesAsMethodsDynamicObject.tryInvokeMethod(MixInClosurePropertiesAsMethodsDynamicObject.java:36)
	at org.gradle.groovy.scripts.BasicScript$ScriptDynamicObject.tryInvokeMethod(BasicScript.java:138)
	at org.gradle.internal.metaobject.AbstractDynamicObject.invokeMethod(AbstractDynamicObject.java:163)
	at org.gradle.api.internal.project.DefaultDynamicLookupRoutine.invokeMethod(DefaultDynamicLookupRoutine.java:58)
	at org.gradle.groovy.scripts.BasicScript.invokeMethod(BasicScript.java:87)
	at build_2wjjv8ph1k2ugmy4g2py43ts5.run(/home/runner/work/Remote-control/Remote-control/app/build.gradle:31)
	at org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java:91)
	... 163 more

==============================================================================

BUILD FAILED in 34s
```

### Errors:
```
* Exception is:
org.gradle.api.GradleScriptException: A problem occurred evaluating project ':app'.
Caused by: org.gradle.api.InvalidUserCodeException: Build was configured to prefer settings repositories over project repositories but repository 'Google' was added by build file 'app/build.gradle'
* Exception is:
org.gradle.api.GradleScriptException: A problem occurred evaluating project ':app'.
Caused by: org.gradle.api.InvalidUserCodeException: Build was configured to prefer settings repositories over project repositories but repository 'Google' was added by build file 'app/build.gradle'
BUILD FAILED in 34s
```
