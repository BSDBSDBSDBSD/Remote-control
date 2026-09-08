# Build Result
Date: Tue Sep  8 09:25:05 UTC 2026

## ❌ FAILED - No APK found

### Last 60 lines of build log:
```
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:337)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:324)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:317)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:463)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:380)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
	at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwExceptionIfCompilationFailed(tasksUtils.kt:22)
	at org.jetbrains.kotlin.compilerRunner.GradleKotlinCompilerWork.run(GradleKotlinCompilerWork.kt:144)
	at org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction.execute(GradleCompilerRunnerWithWorkers.kt:76)
	at org.gradle.workers.internal.DefaultWorkerServer.execute(DefaultWorkerServer.java:63)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:66)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:62)
	at org.gradle.internal.classloader.ClassLoaderUtils.executeInClassloader(ClassLoaderUtils.java:100)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.lambda$execute$0(NoIsolationWorkerFactory.java:62)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:44)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:41)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
	at org.gradle.workers.internal.AbstractWorker.executeWrappedInBuildOperation(AbstractWorker.java:41)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.execute(NoIsolationWorkerFactory.java:59)
	at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:170)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:187)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:120)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:162)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:249)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:109)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:114)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:157)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:126)
	... 2 more


BUILD FAILED in 1m 9s
27 actionable tasks: 27 executed
```

### Errors:
```
> Task :app:compileDebugKotlin FAILED
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/MainActivity.kt:17:27 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/MainActivity.kt:169:9 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/MainActivity.kt:170:29 Suspend function 'connect' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:9:27 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:72:21 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:73:33 Suspend function 'tap' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:83:38 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:83:70 Suspend function 'back' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:84:38 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:84:70 Suspend function 'home' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:85:41 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:85:73 Suspend function 'recents' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:86:39 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:86:71 Suspend function 'volumeUp' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:87:41 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:87:73 Suspend function 'volumeDown' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:110:9 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:111:32 Suspend function 'getAppList' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:118:25 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:119:37 Suspend function 'launchApp' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:140:17 Unresolved reference: lifecycleScope
e: file:///home/runner/work/Remote-control/Remote-control/app/src/main/java/com/bsd/remotecontrol/ui/RemoteViewActivity.kt:141:42 Suspend function 'runShell' should be called only from a coroutine or another suspend function
* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
	at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwExceptionIfCompilationFailed(tasksUtils.kt:22)
```
