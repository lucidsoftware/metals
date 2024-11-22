package scala.meta.internal.metals.testProvider.frameworks

import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.testProvider.TestCaseEntry
import scala.meta.internal.metals.testProvider.FullyQualifiedName
import scala.meta.internal.mtags
import scala.meta.internal.parsing.Trees
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.internal.semanticdb.TypeRef
import scala.meta.io.AbsolutePath
import scala.meta.transversers._
import scala.reflect.NameTransformer

class SpecsTestFinder(
    trees: Trees,
    symbolIndex: mtags.GlobalSymbolIndex,
    semanticdbs: () => mtags.Semanticdbs,
) {

  def findTests(
      doc: TextDocument,
      path: AbsolutePath,
      suiteName: FullyQualifiedName,
      symbol: mtags.Symbol,
  ): Seq[TestCaseEntry] = {
    val treeOpt = trees.get(path)
    treeOpt
      .map { tree =>
        tree.collect {
          case in @ meta.Term.ApplyInfix(
                meta.Lit.String(lhs),
                meta.Name("in"),
                _,
                _,
              ) => {
            val testName = s"${suiteName.value}$lhs"
            TestCaseEntry(testName, in.pos.toLsp.toLocation(path.toURI))
          }
        }
      }
      .getOrElse(Nil)
  }
}
