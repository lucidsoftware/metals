package scala.meta.internal.metals.testProvider.frameworks

import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.testProvider.TestCaseEntry
import scala.meta.internal.mtags
import scala.meta.internal.parsing.Trees
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.internal.semanticdb.TypeRef
import scala.meta.io.AbsolutePath
import scala.meta.transversers._
import scala.reflect.NameTransformer

object SpecsTestFinder {

  def collectTestCases(path: AbsolutePath, trees: Trees) = {
    val treeOpt = trees.get(path)
    treeOpt.map { tree =>
      tree.collect {
        case in @ meta.Term.ApplyInfix(lhs, meta.Name("in"), _, _) => {
          // TODO (next pr): mark as test
        }
      }
    }
  }
}
