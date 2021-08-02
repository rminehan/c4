package c4

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import C4ArrayPar.Ops
import C4ParUtils.C4ExecutionContext

class C4ArrayParProp extends Properties("C4ArrayPar.Ops") {
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  // TODO - could add more checks here - this is enough for a prototype
  // Should also check how it handles errors generated by f
  property("mapC4 == regular map") = forAll { array: Array[Int] =>
    array.map(_ * 2).sameElements(array.mapC4Par(_ * 2))
  }

  property("filterC4 == regular filter") = forAll { array: Array[Int] =>
    array.filter(_ > 10).sameElements(array.filterC4Par(_ > 10))
  }
}