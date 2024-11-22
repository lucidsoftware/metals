package scala.meta.internal.metals.testProvider

import ch.epfl.scala.bsp4j.BuildTarget
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.Specs2
import scala.meta.internal.mtags
import scala.meta.internal.mtags.GlobalSymbolIndex
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.parsing.Trees
import scala.meta.internal.semanticdb._
import scala.meta.io.AbsolutePath
import scala.meta.transversers._

final class TestFrameworkProvider(
    semanticdbs: () => Semanticdbs,
    symbolIndex: GlobalSymbolIndex,
    trees: Trees,
) {
  def getTestSuiteDetailsForPath(
      path: AbsolutePath,
      textDocument: TextDocument,
  ): List[TestSuiteDetails] = {
    val location = Range.defaultInstance.toLocation(path.toURI.toString())
    textDocument.symbols.collect {
      case symbol if isASpecsTest(symbol) => {
        val className = ClassName(symbol.symbol.split('.').last)
        TestSuiteDetails(
          fullyQualifiedName = FullyQualifiedName(
            symbol.symbol
          ), // fix maybe? not sure this is the right fully qualified name
          framework = Specs2,
          className = className,
          symbol = Symbol(symbol.symbol),
          location = location,
        )
      }
    }.toList
  }

  private val specsNames =
    Set("org/specs2/Specification#", "org/specs2/mutable/Specification#")

  private def isASpecsTest(
      symbol: SymbolInformation,
      depth: Int = 0,
  ): Boolean = {
    if (specsNames.contains(symbol.symbol)) {
      return true
    }
    symbol.signature match {
      case ClassSignature(_, parents: Iterable[Type], _, _) => {
        parents.exists { case TypeRef(_, name: String, _) =>
          (for {
            definition <- symbolIndex.definition(mtags.Symbol(name))
            document <- semanticdbs()
              .textDocument(definition.path)
              .documentIncludingStale
            parentSymbol <- document.symbols.find { s => s.symbol == name }
          } yield {
            isASpecsTest(parentSymbol, depth + 1)
          })
            .getOrElse(false)
        }
      }
      case _ => false
    }
  }

}
