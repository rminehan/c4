package c4

import org.scalameter.api._
import PerformanceUtils._

/** Measures the speed up of certain operations by just reordering combinators
 * or fusing steps using `collect`
 */
class Exp2_ArrayTricks extends Bench.LocalTime {

  performance of "Array" in {

    measure method "filter.map" in {
      using(arrays) in {
        a => a.filter(_ % 3 != 0).map(_ * 2)
      }
    }

    measure method "collect" in {
      using(arrays) in {
        a => a.collect {
          case i if i % 3 != 0 => i * 2
        }
      }
    }

  }
}
