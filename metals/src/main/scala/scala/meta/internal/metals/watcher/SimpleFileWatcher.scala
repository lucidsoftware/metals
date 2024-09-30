package scala.meta.internal.metals.watcher

import collection.JavaConverters._
import java.nio.file.{FileSystems, Path, StandardWatchEventKinds}
import java.util.concurrent.BlockingQueue
import scala.concurrent.{ExecutionContext, Future}

class SimpleFileWatcher(
    watchFilter: Path => Boolean,
    watchEventQueue: BlockingQueue[FileWatcherEvent],
    filesToWatch: Set[Path],
    directoriesToWatch: Set[Path],
)(implicit executionContext: ExecutionContext)
    extends FileWatcher {

  val watchService = FileSystems.getDefault().newWatchService();

  override def cancel() = watchService.close()

  override def start() = {
    (filesToWatch.map(_.getParent()) ++ directoriesToWatch)
      .filter(watchFilter)
      .foreach { directory =>
        scribe.trace(s"watching directory $directory")
        directory
          .register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY,
          )
      }
    Future {
      while (true) {
        val key = watchService.take()
        if (key != null) {
          val directory: Path = key.watchable().asInstanceOf[Path]
          key.pollEvents().asScala.foreach { event =>
            val pathFromEventCtx = event.context().asInstanceOf[Path]
            val path = directory.resolve(pathFromEventCtx)
            // since we're watching the parent directory of files/directories, we could potentially pick up things that weren't in the original list
            // testing has shown that this filters out at least some of the not-in-the-build stuff
            if (watchFilter(path)) {
              scribe.info(s"got ${event.kind()} event for path: ${path}")
              watchEventQueue.add(
                event.kind() match {
                  case StandardWatchEventKinds.ENTRY_CREATE |
                      StandardWatchEventKinds.ENTRY_MODIFY =>
                    FileWatcherEvent.createOrModify(path)
                  case StandardWatchEventKinds.ENTRY_DELETE =>
                    FileWatcherEvent.delete(path)
                }
              )
            }
          }
          key.reset()
        }
      }
    }
  }
}
