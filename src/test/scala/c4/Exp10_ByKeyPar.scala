package c4

import org.scalameter.api._
import PerformanceUtils._
import c4.C4ParUtils.C4ExecutionContext
import c4.Memoization.Ops

/** Compares the speeds of the parallelized memoized methods.
 *
 * Experiment 11 deals with the "conc" variants that use a concurrent hash map underneath.
 */
class Exp10_ByKeyPar extends Bench.LocalTime {

  // TODO - tune this based on your machine
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  performance of "*ByKeyPar" in {

    measure method "map" in {
      using(arrays) in {
        a => a.mapByKeyPar(i => i)(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.mapByKeyPar(i => i)(_ * 2).filterByKeyPar(i => i)(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a
          .mapByKeyPar(i => i)(_ * 2)
          .filterByKeyPar(i => i)(_ % 6 != 0)
          .mapByKeyPar(i => i)(_ * 3)
          .filterByKeyPar(i => i)(_ > 300)
      }
    }

    measure method "Heavy work: map(fac)" in {
      using(arrays) in {
        a => a.mapByKeyPar(i => i)(fac)
      }
    }

  }
}
