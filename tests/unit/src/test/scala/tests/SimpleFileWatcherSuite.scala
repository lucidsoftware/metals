package tests

import java.nio.file.{Files, Path}
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.RecursivelyDelete
import scala.meta.internal.metals.UserConfiguration
import scala.meta.internal.metals.watcher.{FileWatcherEvent, SimpleFileWatcher}
import scala.meta.io.AbsolutePath
import scala.util.control.NonFatal

class SimpleFileWatcherSuite extends BaseSuite {
  def userConfig: UserConfiguration =
    UserConfiguration(fallbackScalaVersion = Some(BuildInfo.scalaVersion))
  implicit val ex: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  var workspace: AbsolutePath = _

  test("file-watcher-should-watch-files") {
    workspace = createWorkspace("file-watcher-should-watch-files")
    cleanWorkspace()
    val watchEventQueue: BlockingQueue[FileWatcherEvent] =
      new LinkedBlockingQueue[FileWatcherEvent]

    val directoryToWatch = workspace.resolve("directoryToWatch").toNIO
    val filesDirectory =
      workspace.resolve("directoryToIgnore").resolve("files").toNIO
    Files.createDirectories(directoryToWatch)
    Files.createDirectories(filesDirectory)
    val filesToWatch = Set("foo.scala", "bar.scala").map { fileName =>
      val path = filesDirectory.resolve(fileName)
      Files.createFile(path)
      path
    }
    val fileInDirectoryToWatch = directoryToWatch.resolve("baz.scala")
    Files.createFile(fileInDirectoryToWatch)

    val watcher = new SimpleFileWatcher(
      watchFilter = _ => true,
      watchEventQueue = watchEventQueue,
      filesToWatch = filesToWatch,
      directoriesToWatch = Set(directoryToWatch),
    )
    watcher.start()
    assert(watchEventQueue.isEmpty)

    changeFile(fileInDirectoryToWatch)
    assertEventIsInQueueAndRemove(
      FileWatcherEvent.createOrModify(fileInDirectoryToWatch),
      watchEventQueue,
    )

    filesToWatch.foreach { file =>
      deleteFile(file)
      assertEventIsInQueueAndRemove(
        FileWatcherEvent.delete(file),
        watchEventQueue,
      )
    }

    val newFile = directoryToWatch.resolve("newFile.scala")
    createFile(newFile)
    assertEventIsInQueueAndRemove(
      FileWatcherEvent.createOrModify(newFile),
      watchEventQueue,
    )

    val ignoredFile =
      workspace.resolve("directoryToIgnore").resolve("ignoredFile.scala").toNIO
    createFile(ignoredFile)
    assert(watchEventQueue.isEmpty)
  }

  // unfortunate that we need the Thread.sleep(100), but gives time for the watcher to respond in the other thread.
  // Can't just use a blocking execution context since the other thread loops and blocks forever
  def createFile(path: Path) = {
    Files.createFile(path)
    Thread.sleep(100)
  }

  def changeFile(path: Path) = {
    Files.writeString(path, "abcd")
    Thread.sleep(100)
  }

  def deleteFile(path: Path) = {
    Files.delete(path)
    Thread.sleep(100)
  }

  def assertEventIsInQueueAndRemove(
      event: FileWatcherEvent,
      queue: BlockingQueue[FileWatcherEvent],
  ) = {
    // it's possible that there's a better way to do this.
    // if we assume there will only ever be one element, we could .take() and compare, but this is a little more flexible, although it requires the thread.sleep above
    assert(queue.remove(event))
  }

  protected def createWorkspace(name: String): AbsolutePath = {
    val pathToSuite = PathIO.workingDirectory
      .resolve("target")
      .resolve("e2e")
      .resolve("SimpleFileWatcherSuite")

    val path = pathToSuite.resolve(name)

    Files.createDirectories(path.toNIO)
    path
  }
  def cleanWorkspace(): Unit = {
    if (workspace.isDirectory) {
      try {
        RecursivelyDelete(workspace)
        Files.createDirectories(workspace.toNIO)
      } catch {
        case NonFatal(_) =>
          scribe.warn(s"Unable to delete workspace $workspace")
      }
    }
  }
}
