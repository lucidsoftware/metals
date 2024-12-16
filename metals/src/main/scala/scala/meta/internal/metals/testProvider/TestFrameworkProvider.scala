package scala.meta.internal.metals.testProvider

import ch.epfl.scala.bsp4j.BuildTarget
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.Specs2
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.parsing.Trees
import scala.meta.internal.semanticdb._
import scala.meta.io.AbsolutePath
import scala.meta.transversers._

final class TestFrameworkProvider(
    semanticdbs: () => Semanticdbs,
    trees: Trees,
) {
  def getTestSuiteDetailsForPath(
      path: AbsolutePath,
      textDocument: TextDocument,
  ): List[TestSuiteDetails] = {
    val location = Range.defaultInstance.toLocation(path.toURI.toString())
    def isASpecsTest(symbol: SymbolInformation) = {
      symbol.signature match {
        case klass @ ClassSignature(_, parents: Iterable[Type], _, _) => {
          parents.exists { case TypeRef(_, name: String, _) =>
            name == "sbt/testing/Framework#"
          }
        }
        case _ => false
      }
    }
    textDocument.symbols.collect {
      case symbol if isASpecsTest(symbol) => {
        val className = ClassName(symbol.displayName.split('.').last)
        TestSuiteDetails(
          fullyQualifiedName = FullyQualifiedName(
            symbol.displayName
          ), // fix maybe? not sure this is the right fully qualified name
          framework = Specs2,
          className = className,
          symbol = Symbol(symbol.symbol),
          location = location,
        )
      }
    }.toList
  }
}
