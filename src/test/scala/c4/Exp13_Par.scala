package c4

import org.scalameter.api._
import PerformanceUtils._

/** Examines the performance improvements from using the built in parallel collections
 * from the 2.12 collections library
 *
 * Compare these with the C4ArrayPar.Ops from Exp04
 */
class Exp13_Par extends Bench.LocalTime {

  performance of "Array.par" in {

    measure method "map" in {
      using(arrays) in {
        a => a.par.map(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.par.map(_ * 2).filter(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a.par.map(_ * 2).filter(_ % 6 != 0).map(_ * 3).filter(_ > 300)
      }
    }

  }

}
