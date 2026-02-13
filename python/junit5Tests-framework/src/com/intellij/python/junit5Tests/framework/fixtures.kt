package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.resolveFromRootOrRelative
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.UUID


/**
 * [kotlinx.coroutines.CoroutineScope] that is limited by [com.intellij.openapi.application.Application]
 */
@TestOnly
fun applicationScope(name: String = UUID.randomUUID().toString()): TestFixture<CoroutineScope> = testFixture {
  @Service
  class MyService(val scope: CoroutineScope)

  var unhandledException: Throwable? = null
  val handler = CoroutineExceptionHandler { _, exception ->
   unhandledException = exception
   throw exception
  }
  val scope = ApplicationManager.getApplication().service<MyService>().scope.childScope(name, handler)
  initialized(scope) {
    scope.coroutineContext.job.cancelAndJoin()
    scope.cancel()
    unhandledException?.let {
      throw it
    }
  }
}

@TestOnly
fun TestFixture<PsiDirectory>.psiFileFixture(fileRelativePath: Path): TestFixture<PsiFile> = testFixture { _ ->
  val sourceRootDirectory = this@psiFileFixture.init()
  val virtualFile = sourceRootDirectory.virtualFile.resolveFromRootOrRelative(fileRelativePath.toString())
                    ?: error("Can't resolve VirtualFile for $fileRelativePath")
  val psiFile = readAction {
    sourceRootDirectory.manager.findFile(virtualFile)
    ?: error("Can't find PsiFile for $virtualFile")
  }
  initialized(psiFile) {}
}