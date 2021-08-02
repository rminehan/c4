package c4

import org.scalameter.api._
import PerformanceUtils._
import C4Array.Ops

/** Measures the speed up by reimplementing map and filter using a simple cfor based iteration.
 * Really this test is showing the speed difference between iterating over an Array using an
 * iterator with .hasNext and .next vs a tight C style loop.
 */
class Exp3_C4 extends Bench.LocalTime {

  performance of "C4Array.Ops" in {

    measure method "map" in {
      using(arrays) in {
        a => a.mapC4(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.mapC4(_ * 2).filterC4(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a.mapC4(_ * 2).filterC4(_ % 6 != 0).mapC4(_ * 3).filterC4(_ > 300)
      }
    }

    // This test is making the point that the speed up from using C4 is related to the
    // size of the array, but independent of the workload from f.
    // When f is heavy, it makes the speed up seem less significant in proportion to the
    // overall work.
    measure method "Heavy work: map(fac)" in {
      using(arrays) in {
        a => a.mapC4(fac)
      }
    }
  }
}
