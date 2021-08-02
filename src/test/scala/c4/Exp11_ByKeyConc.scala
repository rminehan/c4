package c4

import org.scalameter.api._
import PerformanceUtils._
import c4.C4ParUtils.C4ExecutionContext
import c4.Memoization.Ops

/** Compares the speeds of the parallelized memoized methods that use a concurrent hash map. */
class Exp11_ByKeyConc extends Bench.LocalTime {

  // TODO - tune this based on your machine
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  performance of "*ByKeyConc" in {

    measure method "map" in {
      using(arrays) in {
        a => a.mapByKeyConc(i => i)(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.mapByKeyConc(i => i)(_ * 2).filterByKeyConc(i => i)(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a
          .mapByKeyConc(i => i)(_ * 2)
          .filterByKeyConc(i => i)(_ % 6 != 0)
          .mapByKeyConc(i => i)(_ * 3)
          .filterByKeyConc(i => i)(_ > 300)
      }
    }

    measure method "Heavy work: map(fac)" in {
      using(arrays) in {
        a => a.mapByKeyConc(i => i)(fac)
      }
    }

  }
}
