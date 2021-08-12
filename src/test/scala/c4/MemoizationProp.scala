package c4

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import C4ParUtils.C4ExecutionContext
import c4.Memoization.Ops

class MemoizationProp extends Properties("Memoization.Ops") {
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  property("mapByKey(i => i) == regular map") = forAll { array: Array[Int] =>
    array.mapByKey(i => i)(_ * 2).sameElements(array.map(_ * 2))
  }

  property("mapByKeyPar == regular map") = forAll { array: Array[Int] =>
    array.mapByKeyPar(i => i)(_ * 2).sameElements(array.map(_ * 2))
  }

  property("mapByKeyConc == regular map") = forAll { array: Array[Int] =>
    array.mapByKeyConc(i => i)(_ * 2).sameElements(array.map(_ * 2))
  }

  property("mapByKeyWithWarmup == regular map") = forAll { array: Array[Int] =>
    array.mapByKeyWithWarmup(warmup = 20)(i => i)(_ * 2).sameElements(array.map(_ * 2))
  }

  property("filterByKey == regular filter") = forAll { array: Array[Int] =>
    array.filterByKey(i => i)(_ > 10).sameElements(array.filter(_ > 10))
  }

  property("filterByKeyPar == regular filter") = forAll { array: Array[Int] =>
    array.filterByKey(i => i)(_ > 10).sameElements(array.filter(_ > 10))
  }

  property("filterByKeyConc == regular filter") = forAll { array: Array[Int] =>
    array.filterByKeyConc(i => i)(_ > 10).sameElements(array.filter(_ > 10))
  }

  property("filterByKeyWithWarmup == regular filter") = forAll { array: Array[Int] =>
    val warmup = math.min(500, array.length)
    array.filterByKeyWithWarmup(warmup)(i => i)(_ > 10).sameElements(array.filter(_ > 10))
  }

  property("mapByKey.filterByKey.mapByKey == regular map.filter.map") = forAll { array: Array[Int] =>
    array.mapByKey(i => i)(_ * 5).filterByKey(i => i)(_ > 10).mapByKey(i => i)(_ / 2).sameElements(
      array.map(_ * 5).filter(_ > 10).map(_ / 2)
    )
  }

  property("mapByKeyPar.filterByKeyPar.mapByKeyPar == regular map.filter.map") = forAll { array: Array[Int] =>
    array.mapByKeyPar(i => i)(_ * 5).filterByKeyPar(i => i)(_ > 10).mapByKeyPar(i => i)(_ / 2).sameElements(
      array.map(_ * 5).filter(_ > 10).map(_ / 2)
    )
  }

  property("mapByKeyConc.filterByKeyConc.mapByKeyConc == regular map.filter.map") = forAll { array: Array[Int] =>
    array.mapByKeyConc(i => i)(_ * 5).filterByKeyConc(i => i)(_ > 10).mapByKeyConc(i => i)(_ / 2).sameElements(
      array.map(_ * 5).filter(_ > 10).map(_ / 2)
    )
  }

  property("mapByKeyWithWarmup.filterByKeyWithWarmup.mapByKeyWithWarmup == regular map.filter.map") = forAll { array: Array[Int] =>
    val warmup = 10
    array
      .mapByKeyWithWarmup(warmup)(i => i)(_ * 5)
      .filterByKeyWithWarmup(warmup)(i => i)(_ > 10)
      .mapByKeyWithWarmup(warmup)(i => i)(_ / 2)
      .sameElements(
        array.map(_ * 5).filter(_ > 10).map(_ / 2)
      )
  }

  // TODO - add tests where the key logic is more complex than `i => i`
}
