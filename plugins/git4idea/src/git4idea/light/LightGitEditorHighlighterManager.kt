// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorInfoImpl
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.BaseSingleTaskController
import git4idea.index.isTracked
import git4idea.index.repositoryPath

private val LOG = Logger.getInstance("#git4idea.light.LightGitEditorHighlighterManager")

class LightGitEditorHighlighterManager(val tracker: LightGitTracker) : Disposable {
  private val singleTaskController = MySingleTaskController()
  private var lst: LineStatusTrackerBase<Range>? = null

  private val lightEditService
    get() = LightEditService.getInstance()

  init {
    tracker.addUpdateListener(object : LightGitTrackerListener {
      override fun update() {
        lightEditService.selectedFileEditor?.let { fileEditor -> updateLst(fileEditor) }
      }
    }, this)
    lightEditService.editorManager.addListener(object : LightEditorListener {
      override fun afterSelect(editorInfo: LightEditorInfo?) {
        if (editorInfo == null) {
          dropLst()
          return
        }
        if (editorInfo.file != lst?.virtualFile) {
          dropLst()
          updateLst(editorInfo.fileEditor)
        }
      }
    }, this)

    Disposer.register(tracker, this)
  }

  private fun readBaseVersion(file: VirtualFile, repositoryPath: String?) {
    if (repositoryPath == null) {
      lst?.setBaseRevision("")
      return
    }

    singleTaskController.request(Request(file, repositoryPath))
  }

  private fun setBaseVersion(baseVersion: BaseVersion) {
    if (lightEditService.selectedFile == baseVersion.file && lst?.virtualFile == baseVersion.file) {
      if (baseVersion.text != null) {
        lst?.setBaseRevision(baseVersion.text)
      } else {
        dropLst()
      }
    }
  }

  private fun updateLst(fileEditor: FileEditor) {
    val editor = LightEditorInfoImpl.getEditor(fileEditor)
    if (editor == null) {
      dropLst()
      return
    }

    val file = fileEditor.file ?: return
    val status = tracker.getFileStatus(file)

    if (!status.isTracked()) {
      dropLst()
      return
    }

    if (lst == null) {
      lst = SimpleLocalLineStatusTracker.createTracker(lightEditService.project, editor.document, file,
                                                       LocalLineStatusTracker.Mode.DEFAULT)
    }
    readBaseVersion(file, status.repositoryPath)
  }

  private fun dropLst() {
    lst?.release()
    lst = null
  }

  override fun dispose() {
    dropLst()
  }

  private inner class MySingleTaskController :
    BaseSingleTaskController<Request, BaseVersion>("Light Git Editor Highlighter", this::setBaseVersion, this) {
    override fun process(requests: List<Request>, previousResult: BaseVersion?): BaseVersion {
      val request = requests.last()
      try {
        return BaseVersion(request.file, getFileContentAsString(request.file, request.repositoryPath, tracker.gitExecutable))
      } catch (e: VcsException) {
        LOG.warn("Could not read base version for ${request.file}", e)
        return BaseVersion(request.file, null)
      }
    }

    override fun cancelRunningTasks(requests: Array<out Request>?): Boolean = true
  }

  private data class Request(val file: VirtualFile, val repositoryPath: String)
  private data class BaseVersion(val file: VirtualFile, val text: String?)
}
