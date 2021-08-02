package c4

import org.scalameter.api._
import PerformanceUtils._

/** Benchmarks standard map/filter operations on a plain old java Array.
 *
 * No tricks at this stage.
 *
 * The tests are designed with later optimizations in mind.
 * For example the map.filter.map.filter test is so that later you can
 * see the benefit of fusing stages to avoid intermediate collections.
 */
class Exp1_PlainArray extends Bench.LocalTime {

  performance of "Array" in {

    measure method "map" in {
      using(arrays) in {
        a => a.map(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.map(_ * 2).filter(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a.map(_ * 2).filter(_ % 6 != 0).map(_ * 3).filter(_ > 300)
      }
    }

    // Make sure your laptop is on charge before running this one...
    measure method "Heavy work: map(fac)" in {
      using(arrays) in {
        a => a.map(fac)
      }
    }

  }

}