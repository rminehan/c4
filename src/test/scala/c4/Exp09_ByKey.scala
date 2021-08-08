package c4

import org.scalameter.api._
import PerformanceUtils._
import Memoization.Ops

/** Investigating the speedup of memoization within each stage.
 *
 * The input arrays are all values from 0 until 100, so there should be
 * a lot of cache hits, however the operations are so simple (e.g. _ * 2)
 * that looking them up from the cache will probably be slower than just
 * computing the result directly.
 *
 * The expectation is that the heavy factorial one should improve significantly
 * in comparison to Exp1
 */
class Exp09_ByKey extends Bench.LocalTime {

  performance of "*ByKey" in {

    measure method "map" in {
      using(arrays) in {
        a => a.mapByKey(i => i)(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.mapByKey(i => i)(_ * 2).filterByKey(i => i)(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a
          .mapByKey(i => i)(_ * 2)
          .filterByKey(i => i)(_ % 6 != 0)
          .mapByKey(i => i)(_ * 3)
          .filterByKey(i => i)(_ > 300)
      }
    }

    // Make sure your laptop is on charge before running this one...
    measure method "Heavy work: map(fac)" in {
      using(arrays) in {
        a => a.mapByKey(i => i)(fac)
      }
    }

  }

}
