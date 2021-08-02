package c4

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import C4ParUtils.C4ExecutionContext
import c4.FragArray.Ops

class FragArrayProp extends Properties("FragArray.Ops") {
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  property("filterFrag.map.filter == regular filter.map.filter") = forAll { array: Array[Int] =>
    array.filterFrag(_ > 30).map(_ * 2).filterFrag(_ % 3 == 0).toArray.sameElements(
      array.filter(_ > 30).map(_ * 2).filter(_ % 3 == 0)
    )
  }

}
